package thaumicenergistics_ce.blockentity.alchemyprovider;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.block.BlockAlchemyProviderConnection;
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.init.capability.CachedEssentiaNeighbours;

/**
 * 炼金供应器连接端：通往供应器的无线源质链路的远端。它只输送源质，
 * 从不存储，因为送达的源质会在下一个 tick 继续前往供应器；位于请求的机器旁时，
 * 它像线缆式供应器那样从网格应答。绑定距离不超过
 * [BlockEntityAlchemyProvider#MAX_LINK_DISTANCE] 格，丢失的一半会自行清理。
 */
public class BlockEntityAlchemyProviderConnection extends ThEBaseBlockEntity implements IEssentiaStorage {

    public static final int TRANSFER_LIMIT = 16;

    private static final int TICK_INTERVAL = 10;

    private @Nullable BlockPos providerPos;

    private final Map<Holder<IAspect>, Integer> buffer = new HashMap<>();

    private final CachedEssentiaNeighbours neighbours = new CachedEssentiaNeighbours(this);

    private long revision;
    private int tickCounter;

    public BlockEntityAlchemyProviderConnection(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALCHEMY_PROVIDER_CONNECTION.get(), pos, state);
    }


    public @Nullable BlockPos linkedProvider() {
        return providerPos;
    }

    public boolean isLinked() {
        return resolveProvider() != null;
    }

    /**
     * 把本接收端绑定到一个供应器，先询问供应器是因为它掌握各种限制——服务多少
     * 个接收端、距离多远——所以被拒绝的链路不会改动任何一方。
     *
     * @return 链路被拒绝的原因，成功时为 {@code null}
     */
    public @Nullable String link(BlockPos toProvider) {
        if (level == null || level.isClientSide()) {
            return null;
        }
        if (!(level.getBlockEntity(toProvider) instanceof BlockEntityAlchemyProvider provider)) {
            return "not a provider";
        }
        if (!(level.getBlockEntity(worldPosition) instanceof BlockEntityAlchemyProviderConnection)) {
            return "receiver is gone";
        }
        String refusal = provider.addLinkedReceiver(worldPosition);
        if (refusal != null) {
            return refusal;
        }
        providerPos = toProvider.immutable();
        setChanged();
        updateConnectedState();
        return null;
    }

    public void unlink() {
        if (level != null && !level.isClientSide() && providerPos != null
                && level.getBlockEntity(providerPos) instanceof BlockEntityAlchemyProvider provider) {
            provider.removeLinkedReceiver(worldPosition);
        }
        providerPos = null;
        buffer.clear();
        setChanged();
        updateConnectedState();
    }

    public @Nullable BlockEntityAlchemyProvider resolveProvider() {
        if (level == null || level.isClientSide() || providerPos == null) {
            return null;
        }
        if (level.getBlockEntity(providerPos) instanceof BlockEntityAlchemyProvider provider) {
            // 自愈：供应器的列表把接收端丢了时把它放回去，例如来自旧存档的存档。
            if (!provider.isLinkedReceiver(worldPosition)) {
                provider.addLinkedReceiver(worldPosition);
            }
            return provider;
        }
        providerPos = null;
        setChanged();
        updateConnectedState();
        return null;
    }

    public void updateConnectedState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(BlockAlchemyProviderConnection.CONNECTED)) {
            return;
        }
        boolean connected = providerPos != null;
        if (state.getValue(BlockAlchemyProviderConnection.CONNECTED) != connected) {
            level.setBlock(worldPosition, state.setValue(BlockAlchemyProviderConnection.CONNECTED, connected), 3);
        }
    }


    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (++tickCounter < TICK_INTERVAL) {
            return;
        }
        tickCounter = 0;

        pushBufferToProvider();
        if (isLinked()) {
            drawFromNeighbours();
            feedSuctionMachines();
        }
    }

    /**
     * 从供应器的网格为接收端旁的机器供料：抽吸型机器不提供容器面，
     * 所以容器路径会一直让它等待。先取出，再交付。
     */
    private void feedSuctionMachines() {
        BlockEntityAlchemyProvider provider = resolveProvider();
        if (provider == null) {
            return;
        }
        boolean fetched = false;
        for (Direction side : Direction.values()) {
            if (neighbours.storage(side) != null) {
                continue;
            }
            SuctionTarget machine = SuctionTarget.on(neighbours, side);
            if (machine == null) {
                continue;
            }
            Holder<IAspect> wanted = machine.wants();
            if (wanted == null) {
                continue;
            }
            int room = TRANSFER_LIMIT - buffer.getOrDefault(wanted, 0);
            if (room <= 0) {
                continue;
            }
            int taken = provider.takeForLink(wanted, room, false);
            if (taken <= 0) {
                continue;
            }
            fetched = true;
            int accepted = machine.accept(wanted, taken);
            // 机器拒收的源质留在输入端，所以已应答的请求永远不会被丢弃。
            int left = taken - accepted;
            if (left > 0) {
                buffer.put(wanted, buffer.getOrDefault(wanted, 0) + left);
                revision++;
            }
        }
        if (fetched) {
            setChanged();
        }
    }

    private void pushBufferToProvider() {
        if (buffer.isEmpty()) {
            return;
        }
        BlockEntityAlchemyProvider provider = resolveProvider();
        if (provider == null) {
            return;
        }
        boolean moved = false;
        for (var aspect : new ArrayList<>(buffer.keySet())) {
            int held = buffer.getOrDefault(aspect, 0);
            if (held <= 0) {
                buffer.remove(aspect);
                continue;
            }
            int accepted = provider.insertFromLink(aspect, held, false);
            if (accepted > 0) {
                moved = true;
            }
            int left = held - accepted;
            if (left <= 0) {
                buffer.remove(aspect);
            } else {
                buffer.put(aspect, left);
            }
        }
        if (moved) {
            revision++;
            setChanged();
        }
    }

    /**
     * 从相邻容器取走源质，仅在已绑定链路时进行：没有供应器就无处可去，
     * 硬取只会让一个永远排不空的缓冲变大。
     */
    private void drawFromNeighbours() {
        if (level == null) {
            return;
        }
        boolean moved = false;
        for (Direction side : Direction.values()) {
            IEssentiaStorage source = neighbours.storage(side);
            if (source == null) {
                continue;
            }
            for (AspectInstance entry : source.contents().entries()) {
                if (entry.amount() <= 0) {
                    continue;
                }
                Holder<IAspect> aspect = entry.aspect();
                int held = buffer.getOrDefault(aspect, 0);
                int space = TRANSFER_LIMIT - held;
                if (space <= 0) {
                    continue;
                }
                // 容器只返回一次内容快照，所以一次请求多于 1 会透支它。
                // 每次只取 1 个单位，与供应器自身的取用保持同步。
                int taken = source.extract(aspect, 1, false);
                if (taken > 0) {
                    buffer.put(aspect, held + taken);
                    revision++;
                    moved = true;
                }
            }
        }
        // 每次访问一次，而不是每移动一个单位一次：每次 setChanged() 都会把区块标记为待保存。
        if (moved) {
            setChanged();
        }
    }


    @Override
    public int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
        if (aspect == null || amount <= 0 || !isLinked()) {
            return 0;
        }
        int held = buffer.getOrDefault(aspect, 0);
        int space = TRANSFER_LIMIT - held;
        if (space <= 0) {
            return 0;
        }
        int accepted = Math.min(amount, space);
        if (!simulate) {
            buffer.put(aspect, held + accepted);
            revision++;
            setChanged();
        }
        return accepted;
    }

    /**
     * 从供应器的网络而非缓冲区为相邻容器供料：缓冲区装着正在送入的源质，
     * 把它再发出去会让同一份源质同时出现在两条路径上。
     */
    @Override
    public int extract(Holder<IAspect> aspect, int amount, boolean simulate) {
        if (aspect == null || amount <= 0) {
            return 0;
        }
        BlockEntityAlchemyProvider provider = resolveProvider();
        if (provider == null) {
            return 0;
        }
        return provider.takeForLink(aspect, Math.min(amount, TRANSFER_LIMIT), simulate);
    }

    @Override
    public AspectList contents() {
        if (buffer.isEmpty()) {
            return AspectList.EMPTY;
        }
        var entries = new ArrayList<AspectInstance>();
        buffer.forEach((aspect, amount) -> {
            if (amount > 0) {
                entries.add(new AspectInstance(aspect, amount));
            }
        });
        return AspectList.ofEntries(entries);
    }

    @Override
    public long contentRevision() {
        return revision;
    }


    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (providerPos != null) {
            tag.putLong("ProviderPos", providerPos.asLong());
        }
        // 缓冲区刻意不写入：这是进行中的传输，不是要累积的容器。
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        providerPos = tag.contains("ProviderPos") ? BlockPos.of(tag.getLong("ProviderPos")) : null;
        buffer.clear();
    }

    @Override
    public AbstractContainerMenu createMenu(
            int containerId, Inventory playerInventory,
            Player player) {
        return null;
    }
}
