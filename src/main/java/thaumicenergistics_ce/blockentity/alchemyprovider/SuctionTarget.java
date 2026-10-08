package thaumicenergistics_ce.blockentity.alchemyprovider;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.capability.CachedEssentiaNeighbours;

/**
 * 主动索取源质的邻居，管道、Thaumatorium、熔炉，跟接收插入的容器相对。
 * 这类机器不提供存储面，容器路径看不到它：
 * 放在 Thaumatorium 旁的供应器会拒掉每一份源质却不报错。源质靠交付到达这种机器，不走插入。
 */
final class SuctionTarget {

    private final IEssentiaTransport transport;

    /** 机器自己的面：在它北边的所有者对应它的 [SOUTH] 面。 */
    private final Direction face;

    private SuctionTarget(IEssentiaTransport transport, Direction face) {
        this.transport = transport;
        this.face = face;
    }

    /** 所有者这一侧的机器；该邻居不从它取东西时是 null。 */
    static @Nullable SuctionTarget on(CachedEssentiaNeighbours neighbours, Direction side) {
        IEssentiaTransport transport = neighbours.transport(side);
        if (transport == null) {
            return null;
        }
        Direction face = side.getOpposite();
        return transport.isConnectable(face) ? new SuctionTarget(transport, face) : null;
    }

    /** 机器索取的要素；机器没活干时是 null。 */
    @Nullable Holder<IAspect> wants() {
        if (transport.getSuctionAmount(face) <= 0) {
            return null;
        }
        return transport.getSuctionType(face);
    }

    /** 交付源质：机器留下当前工作要的量，多出来的拒收。 */
    int accept(Holder<IAspect> aspect, int amount) {
        return transport.addEssentia(aspect, amount, face, false);
    }
}
