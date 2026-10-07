package thaumicenergistics_ce.interfaceaccess;

import appeng.helpers.InterfaceLogicHost;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * 接口的某一面作为源质存储给出什么：原样的容器，或包装后能对上的管道。
 * 从不索取物品，所以这就是一轮中邻居侧的全部。
 */
final class EssentiaNeighbour {

    private EssentiaNeighbour() {}

    /**
     * 某一面给出的源质存储，没有则为 {@code null}。从不索取物品：
     * 玩家的规则是这张卡搬运源质，而不是箱子会收什么。
     */
    static @Nullable IEssentiaStorage at(InterfaceLogicHost host, Direction face) {
        BlockEntity be = host.getBlockEntity();
        if (be == null || !(be.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        BlockPos neighbour = be.getBlockPos().relative(face);
        Direction from = face.getOpposite();
        if (!level.isLoaded(neighbour)) {
            return null;
        }
        // 管道回应的是 TRANSPORT 而不是 STORAGE，所以先问它，再包装到能对上。
        IEssentiaTransport tube = level.getCapability(EssentiaCapabilities.TRANSPORT, neighbour, from);
        if (tube != null && tube.isConnectable(from)) {
            return new TubeStorage(tube, from);
        }
        return level.getCapability(EssentiaCapabilities.STORAGE, neighbour, from);
    }

    /**
     * 把管道读成容器。管道上的源质按面而非按单一仓库存在，所以一个适配器服务一个面，
     * 而对每轮都读一遍的调用方来说，它的 revision 没有用。
     */
    private record TubeStorage(IEssentiaTransport transport, Direction face) implements IEssentiaStorage {

        @Override
        public AspectList contents() {
            Holder<IAspect> held = transport.getEssentiaType(face);
            int amount = transport.getEssentiaAmount(face);
            return held == null || amount <= 0 ? AspectList.EMPTY : AspectList.EMPTY.add(held, amount);
        }

        @Override
        public int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
            return transport.addEssentia(aspect, amount, face, simulate);
        }

        @Override
        public int extract(Holder<IAspect> aspect, int amount, boolean simulate) {
            return transport.takeEssentia(aspect, amount, face, simulate);
        }

        @Override
        public long contentRevision() {
            return 0;
        }
    }
}
