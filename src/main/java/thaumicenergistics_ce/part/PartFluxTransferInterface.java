package thaumicenergistics_ce.part;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.p2p.P2PTunnelPart;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TCAspects;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

/** 由内存卡绑定的一对：抽取端支付 [auram]/[ordo] 并持有咒波缓冲，释放
 * 端将其排空。不是 vis 中继。 */
public class PartFluxTransferInterface extends P2PTunnelPart<PartFluxTransferInterface>
        implements IGridTickable {

    @PartModels
    public static final ResourceLocation MODEL_BASE = ThEIds.id("parts/flux_transfer_interface_base");

    @PartModels
    public static final ResourceLocation MODEL_OFF = ThEIds.id("parts/flux_transfer_interface_off");

    @PartModels
    public static final ResourceLocation MODEL_ON = ThEIds.id("parts/flux_transfer_interface_on");

    @PartModels
    public static final ResourceLocation MODEL_HAS_CHANNEL =
            ThEIds.id("parts/flux_transfer_interface_has_channel");

    public static final List<ResourceLocation> MODEL_LOCATIONS =
            List.of(MODEL_BASE, MODEL_OFF, MODEL_ON, MODEL_HAS_CHANNEL);

    private static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF);
    private static final PartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON);
    private static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_HAS_CHANNEL);

    private static final double IDLE_POWER = 1.0;

    // 一个周期是一秒。
    private static final int CYCLE_TICKS = 20;

    static final int FLUX_PER_CYCLE = 4;

    // 设计值 64 乘以四：到达此值抽取端就停止购买燃料。
    private static final int POOL_LIMIT = 256;

    private static final String TAG_POOL = "fluxPool";

    // 从抽取端所在区块取走、尚未释放；随那一端的区块一同消亡。
    private int fluxPool;

    private boolean workedThisCycle;

    public PartFluxTransferInterface(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(IDLE_POWER).addService(IGridTickable.class, this);
    }

    // 与 AE2 存储总线相同的碰撞箱：借用的模型就是按它塑形的。
    @Override
    public void getBoxes(IPartCollisionHelper boxes) {
        boxes.addBox(5, 5, 12, 11, 11, 14);
        boxes.addBox(2, 2, 14, 14, 14, 15);
        boxes.addBox(3, 3, 15, 13, 13, 16);
    }

    @Override
    public IPartModel getStaticModels() {
        if (!isPowered()) {
            return MODELS_OFF;
        }
        return isActive() ? MODELS_ON : MODELS_HAS_CHANNEL;
    }

    // ----- 这一对在告诉玩家什么 ------------------------------

    public @Nullable FluxWait waitReason() {
        if (!(getLevel() instanceof ServerLevel server) || !getMainNode().isActive()) {
            return FluxWait.NO_NETWORK;
        }
        PartFluxTransferInterface payer = drawEnd();
        if (payer == null || !payer.live(server)) {
            return null;
        }
        // 两端都会问到，答案则针对整对：tick 做出的某种拒绝若被这份列表遗漏，
        // 就会在一台无所作为的机器上读作「idle」。
        if (!payer.volumeClear(server)) {
            return FluxWait.NO_SPACE;
        }
        FluxWait blocked = payer.releaseBlockedReason(server);
        if (blocked != null) {
            return blocked;
        }
        if (isDrawEnd()) {
            // 还没有存下任何东西：抽取端为何没把它填满才是有用的答案。
            return fluxAvailable(server) ? payer.fuelShort(server) : FluxWait.NO_FLUX;
        }
        if (payer.fluxPool <= 0) {
            return fluxAvailable(server) ? payer.fuelShort(server) : FluxWait.NO_FLUX;
        }
        return null;
    }

    public boolean working() {
        PartFluxTransferInterface payer = drawEnd();
        return payer != null && payer.workedThisCycle;
    }

    // ----- tick 处理 ----------------------------------------------------------

    // 绝不发休眠请求：休眠的设备会不再留意自己的搭档。
    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(CYCLE_TICKS, CYCLE_TICKS, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        if (getLevel() instanceof ServerLevel server && getMainNode().isActive()) {
            if (isDrawEnd()) {
                drawCycle(server);
            } else {
                releaseCycle(server);
            }
        }
        return TickRateModulation.SAME;
    }

    private void drawCycle(ServerLevel server) {
        workedThisCycle = false;
        if (fluxPool >= POOL_LIMIT || !volumeClear(server) || !releaseEndReady(server)
                || !fluxAvailable(server)) {
            return;
        }
        MEStorage storage = storage();
        IEnergyService energy = energy();
        AEKey auram = aspectKey(server, TCAspects.AURAM);
        AEKey ordo = aspectKey(server, TCAspects.ORDO);
        if (storage == null || energy == null || auram == null || ordo == null) {
            return;
        }
        if (FluxFuel.take(energy, storage, auram, ordo, actionSource(), FLUX_PER_CYCLE)) {
            TcAura.drainFlux(server, fluxPos(), FLUX_PER_CYCLE, false);
            fluxPool = Math.min(POOL_LIMIT, fluxPool + FLUX_PER_CYCLE);
            workedThisCycle = true;
            getHost().markForSave();
        }
    }

    // 燃料已在抽取端付过，所以缓冲为空是一种等待，而不是失败。
    private void releaseCycle(ServerLevel server) {
        PartFluxTransferInterface payer = drawEnd();
        if (payer == null || !payer.live(server)) {
            return;
        }
        // 只查找一次并交给检查：一个周期不得为这次搜索付两遍代价。
        BlockPos landing = FluxCondensation.landing(server, grid(), fluxPos());
        if (releaseBlock(server, landing) != null) {
            return;
        }
        int banked = Math.min(payer.fluxPool, FluxCondensation.BURST);
        FluxCondensation.roll(
                server,
                landing,
                fluxPos(),
                banked,
                grid(),
                storage(),
                aspectKey(server, TCAspects.VITIUM),
                actionSource(),
                payer::takeFlux);
    }

    private @Nullable FluxWait fuelShort(ServerLevel server) {
        IEnergyService energy = energy();
        MEStorage storage = storage();
        AEKey auram = aspectKey(server, TCAspects.AURAM);
        AEKey ordo = aspectKey(server, TCAspects.ORDO);
        if (energy == null || storage == null || auram == null || ordo == null) {
            return FluxWait.NO_NETWORK;
        }
        return FluxFuel.shortOf(energy, storage, auram, ordo, actionSource(), FLUX_PER_CYCLE);
    }

    // ----- 这一对 ---------------------------------------------------------

    private boolean isDrawEnd() {
        return !isOutput();
    }

    private boolean releaseEndReady(ServerLevel server) {
        return releaseBlockedReason(server) == null;
    }

    // 一次拒绝背后藏着四种不同的修法，所以这一对会说出它正等待的第一个。
    private @Nullable FluxWait releaseBlockedReason(ServerLevel server) {
        FluxWait first = null;
        for (PartFluxTransferInterface output : getOutputStream().toList()) {
            BlockPos landing = FluxCondensation.landing(server, output.grid(), output.fluxPos());
            FluxWait block = output.releaseBlock(server, landing);
            if (block == null) {
                return null;
            }
            if (first == null) {
                first = block;
            }
        }
        return first == null ? FluxWait.NO_PARTNER : first;
    }

    private @Nullable FluxWait releaseBlock(ServerLevel server, @Nullable BlockPos landing) {
        if (!live(server)) {
            return FluxWait.OUT_UNLOADED;
        }
        if (!isActive()) {
            return FluxWait.OUT_OFFLINE;
        }
        if (!volumeClear(server)) {
            return FluxWait.OUT_BLOCKED;
        }
        return landing == null ? FluxWait.OUT_NO_LANDING : null;
    }

    private boolean live(ServerLevel server) {
        return getLevel() == server && !getBlockEntity().isRemoved() && server.isLoaded(fluxPos());
    }

    // AE2 的 [getInput] 在它就是输入时返回本部件，过滤器因此存在；未配对时返回 null。
    private @Nullable PartFluxTransferInterface drawEnd() {
        if (isDrawEnd()) {
            return this;
        }
        PartFluxTransferInterface input = getInput();
        return input == this ? null : input;
    }

    // 两端都在服务端线程上 tick，所以缓冲不需要锁。
    int takeFlux(int want) {
        int taken = Math.min(want, fluxPool);
        if (taken <= 0) {
            return 0;
        }
        fluxPool -= taken;
        workedThisCycle = true;
        getHost().markForSave();
        return taken;
    }

    // ----- 网格辅助方法 -----------------------------------------------------

    private boolean volumeClear(ServerLevel server) {
        return FluxVolume.clear(server, fluxPos(), getSide());
    }

    // Thaumaturge 每 tick 都从邻近区块回填，所以被抽干不等于空。
    private boolean fluxAvailable(ServerLevel server) {
        return TcAura.drainFlux(server, fluxPos(), FLUX_PER_CYCLE, true) >= FLUX_PER_CYCLE - 0.001f;
    }

    private @Nullable IGrid grid() {
        IGridNode node = getMainNode().getNode();
        return node == null ? null : node.getGrid();
    }

    private @Nullable IEnergyService energy() {
        IGrid grid = grid();
        return grid == null ? null : grid.getService(IEnergyService.class);
    }

    private @Nullable MEStorage storage() {
        IGrid grid = grid();
        return grid == null ? null : grid.getStorageService().getInventory();
    }

    private IActionSource actionSource() {
        return IActionSource.ofMachine(this);
    }

    private BlockPos fluxPos() {
        return getBlockEntity().getBlockPos();
    }

    private static @Nullable AEKey aspectKey(Level level, ResourceKey<IAspect> aspect) {
        Holder<IAspect> holder = AEssentiaKeyType.aspectOf(level, aspect.location());
        return holder == null ? null : AEssentiaKey.of(holder);
    }

    // ----- 持久化 ------------------------------------------------------

    // 读取时夹取：来自限制更大的构建的标签不得让咒波搁浅。
    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        fluxPool = Math.min(POOL_LIMIT, Math.max(0, data.getInt(TAG_POOL)));
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.putInt(TAG_POOL, fluxPool);
    }
}
