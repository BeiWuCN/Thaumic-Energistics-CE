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
 * 炼金供应器连接端：通往供应器的无线源质链路的远端。
 * 它只过路不存储，送达的源质会在下一个 tick 继续前往供应器；
 * 贴在请求的机器旁时，它像线缆式供应器那样从网格应答。
 * 绑定距离不超过 [BlockEntityAlchemyProvider#MAX_LINK_DISTANCE] 格；链路丢了一半会自行清理。
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
     * 把本接收端绑到供应器上；限制（连多少个、多远）在供应器那边，先问它，被拒时不改动任何一方。
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
            // 自愈：供应器的列表丢了本接收端就补回去（旧存档如此）。
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
     * 从供应器的网格给接收端旁的机器供料。
     * 抽吸型机器没有容器面，容器路径会让它一直等；先取出再交付。
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
            // 机器拒收的源质留在输入端，已应答的请求就不会被丢弃。
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
     * 从相邻容器取走源质，只在链路已绑定时做。
     * 没有供应器就无处可去，硬取只会把缓冲撑大。
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
                // 容器一次只给一份内容快照，一次要多于 1 就是透支。
                // 每次取 1 个单位，与供应器自身的取用一致。
                int taken = source.extract(aspect, 1, false);
                if (taken > 0) {
                    buffer.put(aspect, held + taken);
                    revision++;
                    moved = true;
                }
            }
        }
        // 每次访问标一次待保存；每个单位标一次会让区块反复进保存队列。
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
     * 从供应器的网络取源质给相邻容器，不动缓冲区。
     * 缓冲区装的是正在送入的源质，再发出去会让同一份源质走两条路径。
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
        // 缓冲区不写入：它只装进行中的传输，不累积。
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
