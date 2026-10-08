package thaumicenergistics_ce.part;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.parts.p2p.P2PTunnelPart;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aura.IVisRelaySource;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.util.ThELog;
import thaumicenergistics_ce.util.ThETransaction;

/**
 * Vis 接口：让 Thaumaturge 的机器从 ME 网络的灵气里取 vis。
 * 它是线缆所在方块的 {@link IVisRelaySource}，一次供给取
 * 「可达节点可售的要素」和「能量服务负担得起的 centivis」里小的那个。
 * 它不向 vis 链要 vis，那个环被禁掉了；vis 是用 AE 造出来的。
 */
public class PartVisInterface extends P2PTunnelPart<PartVisInterface> implements IVisRelaySource {

    public static final Identifier MODEL_VIS_INTERFACE = ThEIds.id("parts/p2p/p2p_tunnel_vis");

    /** 共用的 p2p 状态贴图。26.1 的部件模型从 JSON 解析，不再留 Java 常量，
     * 所以在这里给它们起名；本部件的 JSON 引用的就是这两个 id。 */
    public static final Identifier MODEL_STATUS_OFF =
            Identifier.fromNamespaceAndPath("ae2", "part/display_status_off");

    public static final Identifier MODEL_STATUS_ON =
            Identifier.fromNamespaceAndPath("ae2", "part/display_status_on");

    public static final Identifier MODEL_STATUS_HAS_CHANNEL =
            Identifier.fromNamespaceAndPath("ae2", "part/display_status_has_channel");

    /** AE2 的模型注册表不会自己发现这些状态模型，缺一个就渲染崩溃；
     * 部件可能画到的每个位置都要列出来。 */
    public static final List<Identifier> MODEL_LOCATIONS = List.of(
            MODEL_VIS_INTERFACE, MODEL_STATUS_OFF, MODEL_STATUS_ON, MODEL_STATUS_HAS_CHANNEL);

    /** 100 是费率，不是固定费用：法杖充能和一次合成用掉的量不一样。 */
    private static final double AE_PER_VIS = 100.0;

    private static final int CENTIVIS_PER_VIS = 100;

    /** 1e9 高于任何网络的缓冲，模拟抽取才会回答整个缓冲，不回答请求量；
     * 又远低于 {@code Integer.MAX_VALUE} centivis。 */
    private static final double AE_SIMULATE_CEILING = 1.0e9;

    /** 两个带缓存的查找各要扫 17x17x17，都跑不到每 tick；20 tick 是一秒。 */
    private static final int UPSTREAM_POLL_INTERVAL = 20;

    /** 记不记每次 vis 交付。除 {@code THAUMICENERGISTICS_VIS_TRACE=true} 外都关着。 */
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

    // ----- IVisRelaySource：Thaumaturge 与奥术组装机所看到的东西 -----

    @Override
    public boolean isActive() {
        return getMainNode().isActive();
    }

    /** 可达链在这里断掉时返回 {@code false}，中继就交给旁边的节点；
     * 接到本源的继电器没有要素列表。 */
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
        // 问的是节点的要素列表，不是存量：抽空的节点还能问。
        if (!TcAura.nodeHolds(server, node, primal)) {
            return 0;
        }
        IEnergyService energy = energy();
        if (energy == null) {
            return 0;
        }
        // 一次模拟抽取就能答出网络负担得起多少，单位 centivis。
        double affordable = energy.extractAEPower(
                AE_SIMULATE_CEILING, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        return centivisFor(affordable);
    }

    /** vis 不从别处取来：那笔 AE 支付就是全部交换，付不够就把价钱交回去。网格的电力花在一切日志之外，
     * 中止的事务照样为提供给它的东西付了费；什么都不该花的时候，该问的是 {@link #availableCentivis}。 */
    @Override
    public int drainCentivis(ResourceKey<IAspect> primal, int amount, TransactionContext transaction) {
        if (amount <= 0) {
            return 0;
        }
        int offered = Math.min(amount, availableCentivis(primal));
        if (offered <= 0) {
            return 0;
        }
        IEnergyService energy = energy();
        if (energy == null) {
            return 0;
        }
        double cost = aeCost(offered);
        double paid = energy.extractAEPower(cost, Actionable.MODULATE, PowerMultiplier.CONFIG);
        if (paid + 1.0e-6 < cost) {
            // 网络耗尽前已经取走一部分代价，交还回去。
            energy.injectPower(paid, Actionable.MODULATE);
            return 0;
        }
        if (TRACE) {
            ThELog.LOG.info(
                    "[vis] supplied {} centivis of {} at {} for {} AE against the chain's aspects",
                    offered, primal.identifier().getPath(), visPos(), paid);
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
        // 解析不到东西的中继通向虚无；这里扫的是中继方块，不是部件。
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

        /** vis 真交付之后置 true，第二次提交才不会重复扣费。 */
        private boolean committed;

        private PendingVis(int centivis, ResourceKey<IAspect> aspect) {
            this.centivis = centivis;
            this.aspect = aspect;
        }

        @Override
        public int amount() {
            return centivis;
        }

        /** 交付了 vis 而支付随后失败，就是凭空造 vis。 */
        @Override
        public int commit() {
            if (committed) {
                return 0;
            }
            int taken = ThETransaction.apply(transaction -> drainCentivis(aspect, centivis, transaction));
            if (taken > 0) {
                committed = true;
            }
            return taken;
        }

        @Override
        public void close() {
            // 没有预先扣下东西，见 [VisReservation] 上的说明。
        }
    }
}
