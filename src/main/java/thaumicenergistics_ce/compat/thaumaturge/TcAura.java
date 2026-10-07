package thaumicenergistics_ce.compat.thaumaturge;

import appeng.api.parts.IPart;
import appeng.api.parts.RegisterPartCapabilitiesEvent;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.aura.IVisRelaySource;
import com.leclowndu93150.thaumaturge.api.aura.VisRelayCapabilities;
import com.leclowndu93150.thaumaturge.api.aura.VisRelayHelper;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityNode;
import com.leclowndu93150.thaumaturge.content.aura.node.NodeVisRelaySource;
import com.leclowndu93150.thaumaturge.content.aura.relay.BlockEntityVisRelay;
import com.leclowndu93150.thaumaturge.content.aura.relay.VisRelayNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Thaumaturge 的灵气、vis 中继链和节点网络。0.4.6 版把中继改成拉取式，
 * 并连同 {@code resolveAddonSource} 一起删除了预留 API，而且调用方
 * 永远不会拿到中继或节点对象，所以链的遍历留在本类内部。
 */
public final class TcAura {
    private TcAura() {}

    // -- 环境灵气 --------------------------------------------------------

    public static float vis(Level level, BlockPos pos) {
        return AuraHelper.getVis(level, pos);
    }

    public static int auraBase(Level level, BlockPos pos) {
        return AuraHelper.getAuraBase(level, pos);
    }

    /** 在 {@code simulate} 下只查询灵气而不改动它，所以返回值是调用方
     * 能取走的量，而不是实际取走的量。 */
    public static float drainVis(Level level, BlockPos pos, float want, boolean simulate) {
        return AuraHelper.drainVis(level, pos, want, simulate);
    }

    /** 咒波是区块上的一个数字，不是槽里的一个物件：加它就是凭空生成，也没有
     * 上游可以问它装不装得下。传输的职责是让目标持有得更多，而不是更少。 */
    public static void addFlux(Level level, BlockPos pos, float amount) {
        AuraHelper.addFlux(level, pos, amount);
    }

    /** 从区块上取走至多 {@code want} 点咒波并报告实际取到多少：抽取端的
     * 来源就是它所在的区块，所以返回值偏少意味着那里没有可搬走的东西。 */
    public static float drainFlux(Level level, BlockPos pos, float want, boolean simulate) {
        return AuraHelper.drainFlux(level, pos, want, simulate);
    }

    // -- vis 中继链 -----------------------------------------------------

    public static boolean relayWithinReach(ServerLevel level, BlockPos consumer) {
        return VisRelayNetwork.findRelayNear(level, consumer) != null;
    }

    /** 父链不通的中继可以被链接却仍然不输送任何东西，所以这
     * 是与 {@link #relayWithinReach} 不同的问题：一个找到方块，一个找到链。 */
    public static boolean relayResolves(ServerLevel level, BlockPos consumer) {
        BlockEntityVisRelay relay = VisRelayNetwork.findRelayNear(level, consumer);
        return relay != null && relay.resolveSource(level) != null;
    }

    public static @Nullable BlockPos nodeBehind(ServerLevel level, BlockPos consumer) {
        BlockEntityNode node = nodeAtEnd(level, VisRelayNetwork.findRelayNear(level, consumer));
        return node == null ? null : node.getBlockPos();
    }

    public static boolean nodeHolds(ServerLevel level, BlockPos nodePos, ResourceKey<IAspect> primal) {
        return level.getBlockEntity(nodePos) instanceof BlockEntityNode node
                && node.getAspectsBase().amountOf(primal, level.registryAccess()) > 0;
    }

    /** centivis 是链自身的单位：100 centivis 等于 1 vis。 */
    public static int drainCentivis(ServerLevel level, BlockPos consumer,
            ResourceKey<IAspect> primal, int amount, boolean simulate) {
        return VisRelayHelper.drainCentivis(level, consumer, primal, amount, simulate);
    }

    // -- 能力注册 ---------------------------------------------

    public static <P extends IPart & IVisRelaySource> void registerVisSource(
            RegisterPartCapabilitiesEvent event, Class<P> partClass) {
        event.register(VisRelayCapabilities.SOURCE, (part, context) -> part, partClass);
    }

    // -- 内部实现 -----------------------------------------------------------

    private static @Nullable BlockEntityNode nodeAtEnd(
            ServerLevel level, @Nullable BlockEntityVisRelay relay) {
        if (relay == null) {
            return null;
        }
        var linked = relay.resolveSource(level);
        return linked != null && linked.source() instanceof NodeVisRelaySource node ? node.node() : null;
    }
}
