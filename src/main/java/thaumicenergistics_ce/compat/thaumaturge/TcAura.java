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
 * Thaumaturge 的灵气、vis 中继链和节点网络。
 * 0.4.6 把中继改成拉取式，连同 {@code resolveAddonSource} 一起删掉了预留 API；
 * 调用方拿不到中继或节点对象，链的遍历就留在本类里。
 */
public final class TcAura {
    private TcAura() {}

    // 环境灵气

    public static float vis(Level level, BlockPos pos) {
        return AuraHelper.getVis(level, pos);
    }

    public static int auraBase(Level level, BlockPos pos) {
        return AuraHelper.getAuraBase(level, pos);
    }

    /** {@code simulate} 下只查灵气不改动它：返回值是调用方能取走的量，不是实际取走的量。 */
    public static float drainVis(Level level, BlockPos pos, float want, boolean simulate) {
        return AuraHelper.drainVis(level, pos, want, simulate);
    }

    /** 咒波是区块上的一个数字，不是槽里的物件：加它就是凭空生成，
     * 也没有上游能问装不装得下。传输只要目标持有得比原来多。 */
    public static void addFlux(Level level, BlockPos pos, float amount) {
        AuraHelper.addFlux(level, pos, amount);
    }

    /** 从区块上取走至多 {@code want} 点咒波，报告实际取到多少：
     * 抽取端的来源就是它所在的区块，返回值偏少说明那里没东西可搬。 */
    public static float drainFlux(Level level, BlockPos pos, float want, boolean simulate) {
        return AuraHelper.drainFlux(level, pos, want, simulate);
    }

    // vis 中继链

    public static boolean relayWithinReach(ServerLevel level, BlockPos consumer) {
        return VisRelayNetwork.findRelayNear(level, consumer) != null;
    }

    /** 父链不通的中继能被链接，却什么都不输送，这跟 {@link #relayWithinReach} 是两个问题：
     * 一个找方块，一个找链。 */
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

    // 能力注册

    public static <P extends IPart & IVisRelaySource> void registerVisSource(
            RegisterPartCapabilitiesEvent event, Class<P> partClass) {
        event.register(VisRelayCapabilities.SOURCE, (part, context) -> part, partClass);
    }

    // 内部实现

    private static @Nullable BlockEntityNode nodeAtEnd(
            ServerLevel level, @Nullable BlockEntityVisRelay relay) {
        if (relay == null) {
            return null;
        }
        var linked = relay.resolveSource(level);
        return linked != null && linked.source() instanceof NodeVisRelaySource node ? node.node() : null;
    }
}
