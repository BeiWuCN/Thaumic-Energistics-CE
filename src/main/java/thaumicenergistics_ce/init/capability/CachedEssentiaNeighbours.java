package thaumicenergistics_ce.init.capability;

import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jspecify.annotations.Nullable;

/**
 * 某个方块实体的六个邻居，记住它们而不是每 tick 重新询问：每个面一个缓存，
 * 首次使用时创建，方块实体被移除时丢弃。世界信号——放置或破坏方块、
 * 加载区块——会使其失效，因此之后才出现的邻居仍然能被找到。
 * 该缓存按机器各存一份，从不共享。
 */
public final class CachedEssentiaNeighbours {

    private final BlockEntity owner;

    private final @Nullable BlockCapabilityCache<IEssentiaStorage, Direction>[] storageCaches;

    private final @Nullable BlockCapabilityCache<IEssentiaTransport, Direction>[] transportCaches;

    /** 每个面一个缓存槽位；某个面在首次被询问之前，其槽位为 null。 */
    public CachedEssentiaNeighbours(BlockEntity owner) {
        this.owner = owner;
        this.storageCaches = newCaches();
        this.transportCaches = newCaches();
    }

    private static <T> @Nullable BlockCapabilityCache<T, Direction>[] newCaches() {
        @SuppressWarnings("unchecked")
        BlockCapabilityCache<T, Direction>[] caches = new BlockCapabilityCache[Direction.values().length];
        return caches;
    }

    /** 该面上的容器；该面没有容器时为 null——每 tick 询问一次开销很低。 */
    public @Nullable IEssentiaStorage storage(Direction face) {
        return at(storageCaches, EssentiaCapabilities.STORAGE, face);
    }

    /** 该面上的管道；该面没有管道时为 null——每 tick 询问一次开销很低。 */
    public @Nullable IEssentiaTransport transport(Direction face) {
        return at(transportCaches, EssentiaCapabilities.TRANSPORT, face);
    }

    /**
     * 有缓存就从缓存作答，首次使用时构建它。重建要等 NeoForge 的世界信号，
     * 而宿主被移除后，那些维持其注册的通知也会停止。
     */
    private <T> @Nullable T at(@Nullable BlockCapabilityCache<T, Direction>[] caches,
            BlockCapability<T, Direction> capability, Direction face) {
        if (!(owner.getLevel() instanceof ServerLevel server)) {
            return null;
        }
        int slot = face.ordinal();
        BlockCapabilityCache<T, Direction> cache = caches[slot];
        if (cache == null) {
            cache = BlockCapabilityCache.create(capability, server,
                    owner.getBlockPos().relative(face), face.getOpposite(),
                    () -> !owner.isRemoved(), () -> {});
            caches[slot] = cache;
        }
        return cache.getCapability();
    }
}
