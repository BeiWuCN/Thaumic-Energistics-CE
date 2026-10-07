package thaumicenergistics_ce.part;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.parts.p2p.P2PModels;
import appeng.parts.p2p.P2PTunnelPart;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aura.IVisRelaySource;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.util.ThELog;

/**
 * Vis 接口：让 Thaumaturge 的机器从 ME 网络的灵气中抽取 vis。
 * 它是线缆所在方块的 {@link IVisRelaySource}，一次供给取「可达节点可售的要素」
 * 与「能量服务负担得起的 centivis」中的较小者。它
 * 从不向 vis 链索要 vis——那个环被禁止——所以 vis 是用
 * AE 造出来的。
 */
public class PartVisInterface extends P2PTunnelPart<PartVisInterface> implements IVisRelaySource {

    public static final ResourceLocation MODEL_VIS_INTERFACE = ThEIds.id("parts/p2p/p2p_tunnel_vis");

    private static final P2PModels MODELS = new P2PModels(MODEL_VIS_INTERFACE);

    /** AE2 的模型注册表不会自行发现这些状态模型，缺一个就是
     * 渲染器崩溃，所以这个部件可能被绘制出的每个位置都必须列出。 */
    public static final List<ResourceLocation> MODEL_LOCATIONS = List.of(
            MODEL_VIS_INTERFACE,
            P2PModels.MODEL_STATUS_OFF,
            P2PModels.MODEL_STATUS_ON,
            P2PModels.MODEL_STATUS_HAS_CHANNEL);

    /** 是一个费率，不是固定费用：法杖充能与一次合成并不相同。 */
    private static final double AE_PER_VIS = 100.0;

    private static final int CENTIVIS_PER_VIS = 100;

    /** 高于任何网络的缓冲，这样模拟抽取会回答整个缓冲，而
     * 不是回答请求量。远低于 {@code Integer.MAX_VALUE} centivis。 */
    private static final double AE_SIMULATE_CEILING = 1.0e9;

    /** 两个带缓存的查找中任一个都要扫描 17x17x17，所以两者都不每 tick 运行。
     * 二十 tick 是一秒。 */
    private static final int UPSTREAM_POLL_INTERVAL = 20;

    /** 是否记录每次 vis 交付。除 {@code THAUMICENERGISTICS_VIS_TRACE=true} 外均关闭。 */
    private static final boolean TRACE = "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_VIS_TRACE"));

    private @Nullable PartVisInterface upstreamEnd;

    private long nextUpstreamLookup;

    private boolean upstreamKnown;

    private @Nullable BlockPos permitPos;

    private long nextPermitLookup;

    private boolean permitKnown;

    public PartVisInterface(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(1.0);
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(isPowered(), isActive());
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(6, 6, 15, 10, 10, 16);
        bch.addBox(4, 4, 14, 12, 12, 15);
        bch.addBox(5, 5, 13, 11, 11, 14);
    }

    @Override
    public int getLightLevel() {
        return isActive() ? 8 : 0;
    }

    public @Nullable IGrid getGrid() {
        IGridNode node = getMainNode().getNode();
        return node == null ? null : node.getGrid();
    }

    private @Nullable Level visLevel() {
        return getLevel();
    }

    private BlockPos visPos() {
        return getBlockEntity().getBlockPos();
    }

    // ----- IVisRelaySource——Thaumaturge 与奥术组装机所看到的东西 -----

    @Override
    public boolean isActive() {
        return getMainNode().isActive();
    }

    /** 可达链在此终止时为 {@code false}，于是中继会被送回它旁边的
     * 节点：链到本源的继电器不会有要素列表。 */
    @Override
    public boolean canSupply() {
        return isActive() && permit() != null;
    }

    @Override
    public int availableCentivis(ResourceKey<IAspect> primal) {
        if (primal == null) {
            return 0;
        }
        BlockPos node = permit();
        if (node == null || !(visLevel() instanceof ServerLevel server)) {
            return 0;
        }
        // 问的是节点的要素列表而非它的存量：被抽空的节点还可以再问。
        if (!TcAura.nodeHolds(server, node, primal)) {
            return 0;
        }
        IEnergyService energy = energy();
        if (energy == null) {
            return 0;
        }
        // 一次模拟抽取即可回答「网络能负担多少」，单位为 centivis。
        double affordable = energy.extractAEPower(
                AE_SIMULATE_CEILING, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        return centivisFor(affordable);
    }

    /** vis 不从任何地方取来：那笔 AE 支付就是全部交换，所以付不够的
     * 那一次会把价钱交还回去。 */
    @Override
    public int drainCentivis(ResourceKey<IAspect> primal, int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        int offered = Math.min(amount, availableCentivis(primal));
        if (offered <= 0) {
            return 0;
        }
        if (simulate) {
            return offered;
        }
        IEnergyService energy = energy();
        if (energy == null) {
            return 0;
        }
        double cost = aeCost(offered);
        double paid = energy.extractAEPower(cost, Actionable.MODULATE, PowerMultiplier.CONFIG);
        if (paid + 1.0e-6 < cost) {
            // 代价的一部分在网络耗尽之前已被取出；把它交还回去。
            energy.injectPower(paid, Actionable.MODULATE);
            return 0;
        }
        if (TRACE) {
            ThELog.LOG.info(
                    "[vis] supplied {} centivis of {} at {} for {} AE against the chain's aspects",
                    offered, primal.location().getPath(), visPos(), paid);
        }
        return offered;
    }

    public @Nullable VisReservation reserve(ResourceKey<IAspect> aspect, int centivis) {
        if (aspect == null || centivis <= 0) {
            return null;
        }
        int offered = Math.min(centivis, availableCentivis(aspect));
        return offered <= 0 ? null : new PendingVis(offered, aspect);
    }

    private static double aeCost(int centivis) {
        return AE_PER_VIS * centivis / CENTIVIS_PER_VIS;
    }

    private static int centivisFor(double ae) {
        return (int) Math.floor(ae * CENTIVIS_PER_VIS / AE_PER_VIS);
    }

    private @Nullable BlockPos permit() {
        if (!(visLevel() instanceof ServerLevel server)) {
            return null;
        }
        long now = server.getGameTime();
        if (permitKnown && now < nextPermitLookup) {
            return permitPos;
        }
        permitKnown = true;
        nextPermitLookup = now + UPSTREAM_POLL_INTERVAL;
        PartVisInterface end = upstream(server);
        permitPos = end == null ? null : TcAura.nodeBehind(server, end.visPos());
        return permitPos;
    }

    private @Nullable PartVisInterface upstream(ServerLevel server) {
        long now = server.getGameTime();
        if (upstreamKnown && now < nextUpstreamLookup) {
            return upstreamEnd;
        }
        upstreamKnown = true;
        nextUpstreamLookup = now + UPSTREAM_POLL_INTERVAL;
        upstreamEnd = canReachRelay(server) ? this : partnerWithRelay(server);
        return upstreamEnd;
    }

    private boolean canReachRelay(ServerLevel server) {
        // 解析不到任何东西的中继通向虚无；这里扫描的是中继方块，不是部件。
        return TcAura.relayResolves(server, visPos());
    }

    private @Nullable PartVisInterface partnerWithRelay(ServerLevel server) {
        PartVisInterface input = getInput();
        if (input != null && input != this && input.canReachRelay(server)) {
            return input;
        }
        for (PartVisInterface output : getOutputStream().toList()) {
            if (output != this && output.canReachRelay(server)) {
                return output;
            }
        }
        return null;
    }

    private @Nullable IEnergyService energy() {
        IGrid grid = getGrid();
        return grid == null ? null : grid.getService(IEnergyService.class);
    }

    public @Nullable BlockPos upstreamPosition() {
        if (!(visLevel() instanceof ServerLevel server)) {
            return null;
        }
        PartVisInterface source = upstream(server);
        return source == null ? null : source.visPos();
    }

    public @Nullable BlockPos permitPosition() {
        return permit();
    }

    private final class PendingVis implements VisReservation {

        private final int centivis;
        private final ResourceKey<IAspect> aspect;

        /** vis 实际交付之后为 true，这样第二次提交不会重复扣费。 */
        private boolean committed;

        private PendingVis(int centivis, ResourceKey<IAspect> aspect) {
            this.centivis = centivis;
            this.aspect = aspect;
        }

        @Override
        public int amount() {
            return centivis;
        }

        /** 交付了 vis 而对应的支付随后失败，等于凭空造出 vis。 */
        @Override
        public int commit() {
            if (committed) {
                return 0;
            }
            int taken = drainCentivis(aspect, centivis, false);
            if (taken > 0) {
                committed = true;
            }
            return taken;
        }

        @Override
        public void close() {
            // 没有预先扣下任何东西——见 [VisReservation] 上的说明。
        }
    }
}
