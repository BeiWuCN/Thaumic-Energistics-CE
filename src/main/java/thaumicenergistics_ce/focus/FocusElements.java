package thaumicenergistics_ce.focus;

import com.leclowndu93150.thaumaturge.api.spell.behavior.SpellBehaviorType;
import com.leclowndu93150.thaumaturge.api.spell.part.SpellPart;
import com.leclowndu93150.thaumaturge.api.spell.part.SpellPartKind;
import com.leclowndu93150.thaumaturge.registry.TTSpellBehaviors;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class FocusElements {

    public static final DeferredHolder<SpellBehaviorType<?>, SpellBehaviorType<FocusEffectAEWrench>>
            AEWRENCH_BEHAVIOR = TTSpellBehaviors.BEHAVIORS.register(FocusEffectAEWrench.KEY.getPath(),
                    () -> new SpellBehaviorType<>(SpellPartKind.EFFECT, FocusEffectAEWrench.CODEC));

    public static final ResourceKey<SpellPart> AEWRENCH =
            ResourceKey.create(SpellPart.REGISTRY_KEY, FocusEffectAEWrench.KEY);

    private FocusElements() {}

    public static void register(IEventBus modBus) {}

}
