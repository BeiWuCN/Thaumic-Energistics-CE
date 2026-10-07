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
 * 一个方块实体的六个邻居，记着不是每 tick 重问：每面一个缓存，
 * 首次使用时建，方块实体被移除时丢。世界信号。放方块、砸方块、区块加载，
 * 会让它失效，故后来才出现的邻居仍能找到。缓存按机器各一份，从不共享。
 */
public final class CachedEssentiaNeighbours {

    private final BlockEntity owner;

    private final @Nullable BlockCapabilityCache<IEssentiaStorage, Direction>[] storageCaches;

    private final @Nullable BlockCapabilityCache<IEssentiaTransport, Direction>[] transportCaches;

    /** 每面一个缓存槽；某个面首次被问之前是 null。 */
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

    /** 那面的容器，那面没有则为 null。每 tick 问一次很便宜。 */
    public @Nullable IEssentiaStorage storage(Direction face) {
        return at(storageCaches, EssentiaCapabilities.STORAGE, face);
    }

    /** 那面的管道，那面没有则为 null。每 tick 问一次很便宜。 */
    public @Nullable IEssentiaTransport transport(Direction face) {
        return at(transportCaches, EssentiaCapabilities.TRANSPORT, face);
    }

    /**
     * 有缓存就从缓存答，首次使用时建。重建要等 NeoForge 的世界信号；
     * 宿主被移除后，维持它注册的那些通知也停了。
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
