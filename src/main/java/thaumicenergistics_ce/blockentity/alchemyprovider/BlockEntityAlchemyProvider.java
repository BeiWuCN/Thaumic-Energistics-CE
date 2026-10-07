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
 * 炼金供应器：ME 网络把要送出去的源质放这儿，是输入总线的另一半，
 * 把源质交给碰到的容器或机器。缓冲只当中转点，不做存储：插入的源质下一 tick 推给邻居，
 * 不落盘。没接东西的供应器拒收一切；要源质的机器走网格，经一份网格持续补满的预留供给。
 */
public class BlockEntityAlchemyProvider extends AENetworkedBlockEntity
        implements IStorageProvider, IGridTickable, IEssentiaStorage {

    public static final int BUFFER_PER_ASPECT = 16;

    /** 一个供应器服务几个接收方。每多一个，供应器多花一份待机电力。 */
    public static final int MAX_LINKED_RECEIVERS = 8;

    public static final int MAX_LINK_DISTANCE = 32;

    /** 一单位源质经链路送出的收费，按总线的费率。 */
    public static final double AE_PER_ESSENTIA = 10.0;

    /** 链路花的那份预留，由网格补满；[Jade] tooltip 报的就是这个数。 */
    public static final double AE_CACHE = 40.0;

    private static final int TICK_RATE_ACTIVE = 10;
    private static final int TICK_RATE_IDLE = 40;

    private final AlchemyProviderBuffer buffer = new AlchemyProviderBuffer(this);

    /** 自持的 AE，补网格不付的那部分；网格没电就什么都搬不动。 */
    private double cacheAE;

    private final ReceiverLinks links = new ReceiverLinks(this);

    private final IActionSource actionSource =
            IActionSource.ofMachine(this);

    public BlockEntityAlchemyProvider(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALCHEMY_PROVIDER.get(), pos, state);
        // 待机电力统一向链路问，避免两处各写一份；没有链路时用基础值。
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
            // 接收方可能已被拆掉，为不存在的接收方付费玩家看不出来。
            if (pruneDeadReceivers()) {
                return TickRateModulation.URGENT;
            }
            return TickRateModulation.SLOWER;
        }

        boolean moved = buffer.push();
        // 缓冲里还剩东西，说明邻居全满了，早一点再看也清不掉。
        return moved ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    // [IEssentiaStorage] 网络看到的这个缓冲

    @Override
    public int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
        return buffer.insert(aspect, amount, simulate);
    }

    /**
     * 已绑定接收方插入的，算链路流量，和取走一样按单位付费；网格付不起，两个方向都搬不动。
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

    /** 拒绝的原因，链路建成时为 null；连接器显示的就是这条消息。 */
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
     * 让已绑定接收方直接从网格取源质，不走 {@link #extract}（那个服务缓冲）。
     * 按搬运单位付费，网格没电就搬不动。
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

    /** 预留的水位，[Jade] tooltip 报这个数。 */
    public int cachedAE() {
        return (int) Math.floor(cacheAE);
    }

    /** 从网格把预留补满：网格付得起是满的，付不起是空的。 */
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

    /** 一次付一个单位，先扣网格，剩下的用预留补；两边都付不出就停。 */
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

    // 只存链路。缓冲是中转点，不入档。

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
