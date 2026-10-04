package thaumicenergistics_ce.focus;

import com.leclowndu93150.thaumaturge.api.casters.FocusElementType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics_ce.ThEIds;

/**
 * Thaumaturge's focus element registry, as seen from this mod.
 * <ul>
 * <li>{@code FocusElementType} is a NeoForge registry, so register a {@link DeferredRegister} of your
 * own over the same {@code REGISTRY_KEY} - no addon hook, nothing to mix into.
 * <li>Thaumaturge binds it into {@code FocusEngine} before any addon's constructor runs.
 * </ul>
 */
public final class FocusElements {

    public static final DeferredRegister<FocusElementType> REGISTRY =
            DeferredRegister.create(FocusElementType.REGISTRY_KEY, ThEIds.MODID);

    /** The wrench focus effect; the research page blits {@code icon} directly, hence the
     * {@code .png}; {@code color} is AE2's wrench tint, as in the original focus. */
    public static final DeferredHolder<FocusElementType, FocusElementType> AEWRENCH = REGISTRY.register(
            FocusEffectAEWrench.KEY.getPath(),
            () -> new FocusElementType(
                    new FocusEffectAEWrench(),
                    ThEIds.id("textures/foci/aewrench.png"),
                    0x4FC3F7));

    private FocusElements() {}

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
