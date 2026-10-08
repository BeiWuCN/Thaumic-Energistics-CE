package thaumicenergistics_ce.part;

import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.essentia.ThEImmediateStorage;

/**
 * 找到总线所面对方块上的源质容器，并问对问题。
 * <ul>
 * <li>{@code isConnectable} 收的是容器自己的面：位于北侧的总线落在它的 SOUTH 面。
 * <li>面不对就沉默：罐子只在 {@code UP} 应答 true，别的面什么都插不进去。
 * <li>顺序：总线那一面上的 transport，然后任一被接受的面上的 storage，最后把 transport 当 storage。
 * </ul>
 */
final class EssentiaNeighbour {

    private static final Direction[] FACES = Direction.values();

    private EssentiaNeighbour() {}

    /**
     * 有意不缓存，是本 mod 邻居查找里唯一这么做的一个：目标不是调用方自己的方块，
     * 部件的宿主也从不说邻居被重新打包过。直接问更省。
     */
    static @Nullable IEssentiaStorage find(Level level, BlockPos target, Direction from) {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }

        IEssentiaTransport transport = server.getCapability(EssentiaCapabilities.TRANSPORT, target, from);
        if (transport == null) {
            return server.getCapability(EssentiaCapabilities.STORAGE, target, from);
        }
        if (!transport.isConnectable(from)) {
            return null;
        }

        for (Direction accepted : FACES) {
            if (!transport.isConnectable(accepted)) {
                continue;
            }
            IEssentiaStorage storage = server.getCapability(EssentiaCapabilities.STORAGE, target, accepted);
            if (storage != null) {
                return storage;
            }
        }

        return new Adapter(transport, from);
    }

    /** 按面不可免：管道的内容按面不同，一个适配器只服务一个面。 */
    private record Adapter(IEssentiaTransport transport, Direction face)
            implements IEssentiaStorage, ThEImmediateStorage {

        @Override
        public AspectList contents() {
            var aspect = transport.getEssentiaType(face);
            int amount = transport.getEssentiaAmount(face);
            return aspect == null || amount <= 0
                    ? AspectList.EMPTY
                    : AspectList.EMPTY.add(aspect, amount);
        }

        @Override
        public int amount(Holder<IAspect> aspect) {
            var held = transport.getEssentiaType(face);
            return held != null && held.equals(aspect) ? transport.getEssentiaAmount(face) : 0;
        }

        /** 立即生效：Thaumaturge 的管道 API 没有事务可以托管这次改动。 */
        @Override
        public int insert(Holder<IAspect> aspect, int amount, TransactionContext transaction) {
            return transport.addEssentia(aspect, amount, face);
        }

        @Override
        public int extract(Holder<IAspect> aspect, int amount, TransactionContext transaction) {
            return transport.takeEssentia(aspect, amount, face);
        }

        @Override
        public int previewInsert(Holder<IAspect> aspect, int amount) {
            return transport.addEssentia(aspect, amount, face, true);
        }

        @Override
        public int previewExtract(Holder<IAspect> aspect, int amount) {
            return transport.takeEssentia(aspect, amount, face, true);
        }

        @Override
        public long contentRevision() {
            return 0;
        }
    }
}
