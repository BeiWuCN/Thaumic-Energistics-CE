package thaumicenergistics_ce.selftest;

import com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Reads Thaumaturge's aspect index the way the self-tests need it: never before it has been published.
 * <ul>
 *   <li>Thaumaturge builds the index on a worker thread and hands it back through one hop of the server
 *       task queue, so a check answering {@code ServerStartedEvent} reads an index that is still EMPTY.
 *   <li>The probe items are shared, so both sides read an empty answer the same way: not published yet.
 * </ul>
 */
final class AspectIndexWait {

    /** Items both self-tests read, in the order they read them. */
    static final List<String> PROBE_ITEM_IDS = List.of("minecraft:bone", "minecraft:stone", "minecraft:coal");

    /** Ticks a self-test waits for the index before it reports what it read; 100 is about five seconds. */
    static final int MAX_WAIT_TICKS = 100;

    private AspectIndexWait() {}

    /** The stack for one probe id, or null when nothing is registered under it. */
    static @Nullable ItemStack resolve(Level level, String id) {
        var items = level.registryAccess().lookupOrThrow(Registries.ITEM);
        var holder = items.get(ResourceKey.create(Registries.ITEM, ResourceLocation.parse(id)));
        return holder.isEmpty() ? null : new ItemStack(holder.get().value());
    }

    /** True once one probe item reports aspects: the index has been published, or was never empty. */
    static boolean isPublished(Level level) {
        for (String id : PROBE_ITEM_IDS) {
            ItemStack stack = resolve(level, id);
            if (stack == null) {
                continue;
            }
            AspectList aspects = AspectIndexAccess.of(stack);
            if (aspects != null && !aspects.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** The shared waiting rule: wait while the index is unpublished, but never past the bound. */
    static boolean keepWaiting(Level level, int waitedTicks) {
        return waitedTicks < MAX_WAIT_TICKS && !isPublished(level);
    }
}
