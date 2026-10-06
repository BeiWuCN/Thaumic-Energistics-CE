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
 * The six neighbours of one block entity, remembered instead of re-asked every tick: a cache
 * per face, created on first use and dropped when the block entity is removed. A world signal
 * - a block placed, broken, or a chunk loaded - invalidates it, so a neighbour that appears
 * later is still found. The cache is per machine and never shared.
 */
public final class CachedEssentiaNeighbours {

    private final BlockEntity owner;

    private final @Nullable BlockCapabilityCache<IEssentiaStorage, Direction>[] storageCaches;

    private final @Nullable BlockCapabilityCache<IEssentiaTransport, Direction>[] transportCaches;

    /** One cache slot per face; a slot is null until that face is first asked about. */
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

    /** The container on that face, or null for a face that has none - cheap to ask every tick. */
    public @Nullable IEssentiaStorage storage(Direction face) {
        return at(storageCaches, EssentiaCapabilities.STORAGE, face);
    }

    /** The pipe on that face, or null for a face that has none - cheap to ask every tick. */
    public @Nullable IEssentiaTransport transport(Direction face) {
        return at(transportCaches, EssentiaCapabilities.TRANSPORT, face);
    }

    /**
     * Answers from the cache when there is one, and builds it on first use. Rebuilding waits for
     * NeoForge's world signal, and a removed owner stops the notifications that keep it registered.
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
