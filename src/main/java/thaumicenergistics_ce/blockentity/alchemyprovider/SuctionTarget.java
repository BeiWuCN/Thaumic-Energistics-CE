package thaumicenergistics_ce.blockentity.alchemyprovider;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.capability.CachedEssentiaNeighbours;

/**
 * 主动索取源质的邻居——管道、Thaumatorium、熔炉——与之相对的是接收
 * 插入的容器。抽吸型机器根本不提供存储面，所以容器路径看不到它：
 * 这就是放在 Thaumatorium 旁的供应器拒绝每一份源质却不报错的原因。
 * 源质靠交付而非插入到达这种机器。
 */
final class SuctionTarget {

    private final IEssentiaTransport transport;

    /** 机器自己的面：位于它北侧的所有者对应它的 [SOUTH] 面。 */
    private final Direction face;

    private SuctionTarget(IEssentiaTransport transport, Direction face) {
        this.transport = transport;
        this.face = face;
    }

    /** 所有者该侧的机器，该邻居不从它取任何东西时为 null。 */
    static @Nullable SuctionTarget on(CachedEssentiaNeighbours neighbours, Direction side) {
        IEssentiaTransport transport = neighbours.transport(side);
        if (transport == null) {
            return null;
        }
        Direction face = side.getOpposite();
        return transport.isConnectable(face) ? new SuctionTarget(transport, face) : null;
    }

    /** 机器索取的要素，机器自身没有工作时为 null。 */
    @Nullable Holder<IAspect> wants() {
        if (transport.getSuctionAmount(face) <= 0) {
            return null;
        }
        return transport.getSuctionType(face);
    }

    /** 交付源质：机器留下当前工作所需的量，拒收其余部分。 */
    int accept(Holder<IAspect> aspect, int amount) {
        return transport.addEssentia(aspect, amount, face, false);
    }
}
