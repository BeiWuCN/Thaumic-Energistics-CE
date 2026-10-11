package thaumicenergistics_ce.registry.capability;

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

public final class CachedEssentiaNeighbours {

    private final BlockEntity owner;

    private final @Nullable BlockCapabilityCache<IEssentiaStorage, Direction>[] storageCaches;

    private final @Nullable BlockCapabilityCache<IEssentiaTransport, Direction>[] transportCaches;

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

    public @Nullable IEssentiaStorage storage(Direction face) {
        return at(storageCaches, EssentiaCapabilities.STORAGE, face);
    }

    public @Nullable IEssentiaTransport transport(Direction face) {
        return at(transportCaches, EssentiaCapabilities.TRANSPORT, face);
    }

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
