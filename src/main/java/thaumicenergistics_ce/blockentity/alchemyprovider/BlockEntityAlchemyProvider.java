package thaumicenergistics_ce.blockentity.alchemyprovider;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;

/**
 * 炼金供应器：ME 网络存放送往外界的源质之处——输入总线的另一半，
 * 把源质交给它所接触到的任何容器或机器。缓冲只是中转点而非存储：
 * 插入的源质在下一个 tick 被推给邻接方，且从不持久化。没有接任何
 * 东西的供应器拒绝一切，而需要源质的机器由网格通过一份网格持续补满的
 * 预留来供给。
 */
public class BlockEntityAlchemyProvider extends AENetworkedBlockEntity
        implements IStorageProvider, IGridTickable, IEssentiaStorage {

    public static final int BUFFER_PER_ASPECT = 16;

    /** 一个供应器可以服务多少个接收方。每一个都会让供应器多花一份待机电力。 */
    public static final int MAX_LINKED_RECEIVERS = 8;

    public static final int MAX_LINK_DISTANCE = 32;

    /** 一个单位源质经由链路送出时向网格收取的费用，即总线的费率。 */
    public static final double AE_PER_ESSENTIA = 10.0;

    /** 链路动用的预留额度，由网格补满：也就是 Jade tooltip 报告的那个数字。 */
    public static final double AE_CACHE = 40.0;

    private static final int TICK_RATE_ACTIVE = 10;
    private static final int TICK_RATE_IDLE = 40;

    private final AlchemyProviderBuffer buffer = new AlchemyProviderBuffer(this);

    /** 自持的 AE：用于覆盖网格不愿支付的部分，因此没有电力的网格什么都搬不动。 */
    private double cacheAE;

    private final ReceiverLinks links = new ReceiverLinks(this);

    private final IActionSource actionSource =
            IActionSource.ofMachine(this);

    public BlockEntityAlchemyProvider(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALCHEMY_PROVIDER.get(), pos, state);
        // 向链路询问，使待机电力只有一个作者：没有链路时，它们给出基础数值。
        links.updateIdlePower();
        getMainNode()
                .addService(IStorageProvider.class, this)
                .addService(IGridTickable.class, this);
    }

    @Override
    public void mountInventories(IStorageMounts mounts) {
        mounts.mount(new AlchemyProviderStorage(this));
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TICK_RATE_ACTIVE, TICK_RATE_IDLE, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        if (level == null || level.isClientSide()) {
            return TickRateModulation.IDLE;
        }
        fillCache();
        if (!getMainNode().isActive() || !buffer.hasWork()) {
            // 接收方可能已被破坏；为已不存在的接收方付费，玩家察觉不到。
            if (pruneDeadReceivers()) {
                return TickRateModulation.URGENT;
            }
            return TickRateModulation.SLOWER;
        }

        boolean moved = buffer.push();
        // 仍有东西留在缓冲里说明所有邻接方都满了；更早再查一次也不会把它清空。
        return moved ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    // [IEssentiaStorage]——网络所见的缓冲

    @Override
    public int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
        return buffer.insert(aspect, amount, simulate);
    }

    /**
     * 来自已绑定接收方的插入：属于链路的流量，与取走一样按单位付费，因此
     * 付不起费用的网格两个方向都搬不动东西。
     */
    public int insertFromLink(Holder<IAspect> aspect, int amount, boolean simulate) {
        if (aspect == null || amount <= 0) {
            return 0;
        }
        int accepted = buffer.insert(aspect, amount, true);
        if (accepted <= 0 || simulate) {
            return accepted;
        }
        int paid = chargeForLink(accepted);
        return paid <= 0 ? 0 : buffer.insert(aspect, paid, false);
    }

    @Override
    public int extract(Holder<IAspect> aspect, int amount, boolean simulate) {
        return 0;
    }

    @Override
    public AspectList contents() {
        return buffer.contents();
    }

    @Override
    public long contentRevision() {
        return buffer.revision();
    }

    public int buffered(Holder<IAspect> aspect) {
        return buffer.buffered(aspect);
    }

    // 已绑定的接收方

    /** 拒绝的原因；链路建立成功时为 null；这条消息就是连接器显示的内容。 */
    public @Nullable String addLinkedReceiver(BlockPos receiver) {
        return links.add(receiver);
    }

    public void removeLinkedReceiver(BlockPos receiver) {
        links.remove(receiver);
    }

    public boolean isLinkedReceiver(BlockPos receiver) {
        return links.contains(receiver);
    }

    public int linkedReceiverCount() {
        return links.count();
    }

    public List<BlockPos> linkedReceivers() {
        return links.all();
    }

    public boolean pruneDeadReceivers() {
        return links.pruneDead();
    }

    /**
     * 让已绑定的接收方从网格取源质，而不走 {@link #extract}，后者服务的是
     * 缓冲。它按搬运的单位付费，因此没有电力的网格什么都搬不动。
     */
    public int takeForLink(Holder<IAspect> aspect, int amount, boolean simulate) {
        if (aspect == null || amount <= 0 || !getMainNode().isActive()) {
            return 0;
        }
        MEStorage storage = networkStorage();
        if (storage == null) {
            return 0;
        }
        AEssentiaKey key = AEssentiaKey.of(aspect);
        if (key == null) {
            return 0;
        }
        int wanted = amount;
        if (!simulate) {
            long available = storage.extract(key, amount, Actionable.SIMULATE, actionSource);
            if (available <= 0) {
                return 0;
            }
            wanted = chargeForLink((int) Math.min(available, amount));
            if (wanted <= 0) {
                return 0;
            }
        }
        Actionable mode = simulate
                ? Actionable.SIMULATE
                : Actionable.MODULATE;
        long moved = storage.extract(key, wanted, mode, actionSource);
        if (!simulate && moved > 0) {
            setChanged();
        }
        return (int) Math.min(moved, Integer.MAX_VALUE);
    }

    /** 预留额度的水平，由 Jade tooltip 报告。 */
    public int cachedAE() {
        return (int) Math.floor(cacheAE);
    }

    /** 从网格把预留补满：网格能付费时是满的，不能付费时是空的。 */
    private void fillCache() {
        if (cacheAE >= AE_CACHE) {
            return;
        }
        IEnergyService energy = networkEnergy();
        if (energy == null) {
            return;
        }
        cacheAE += energy.extractAEPower(AE_CACHE - cacheAE, Actionable.MODULATE,
                PowerMultiplier.CONFIG);
    }

    /** 一次支付一个单位，先扣网格，其余部分用预留补齐；两者都付不出时停止。 */
    private int chargeForLink(int units) {
        IEnergyService energy = networkEnergy();
        if (energy == null) {
            return 0;
        }
        int paid = 0;
        while (paid < units) {
            double offered = energy.extractAEPower(AE_PER_ESSENTIA, Actionable.SIMULATE,
                    PowerMultiplier.CONFIG);
            double shortfall = AE_PER_ESSENTIA - offered;
            if (shortfall > cacheAE) {
                break;
            }
            if (offered > 0) {
                energy.extractAEPower(offered, Actionable.MODULATE, PowerMultiplier.CONFIG);
            }
            cacheAE -= shortfall;
            paid++;
        }
        return paid;
    }

    private @Nullable IGrid networkGrid() {
        IGridNode node = getMainNode().getNode();
        return node == null ? null : node.getGrid();
    }

    private @Nullable MEStorage networkStorage() {
        IGrid grid = networkGrid();
        if (grid == null) {
            return null;
        }
        IStorageService service =
                grid.getService(IStorageService.class);
        return service == null ? null : service.getInventory();
    }

    private @Nullable IEnergyService networkEnergy() {
        IGrid grid = networkGrid();
        return grid == null ? null : grid.getService(IEnergyService.class);
    }

    // 持久化：只保存链路。缓冲只是中转点，不保存。

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag receivers = new ListTag();
        for (BlockPos pos : links.all()) {
            receivers.add(LongTag.valueOf(pos.asLong()));
        }
        tag.put("LinkedReceivers", receivers);
        tag.putDouble("CacheAE", cacheAE);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        ListTag receivers = tag.getList("LinkedReceivers", CompoundTag.TAG_LONG);
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < receivers.size(); i++) {
            if (receivers.get(i) instanceof LongTag value) {
                positions.add(BlockPos.of(value.getAsLong()));
            }
        }
        links.replace(positions);
        links.updateIdlePower();
        cacheAE = tag.getDouble("CacheAE");
        buffer.clear();
    }
}
