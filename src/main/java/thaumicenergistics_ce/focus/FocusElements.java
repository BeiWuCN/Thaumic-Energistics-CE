package thaumicenergistics_ce.focus;

import com.leclowndu93150.thaumaturge.TTIds;
import com.leclowndu93150.thaumaturge.api.spell.behavior.SpellBehaviorType;
import com.leclowndu93150.thaumaturge.api.spell.part.SpellPart;
import com.leclowndu93150.thaumaturge.api.spell.part.SpellPartKind;
import com.leclowndu93150.thaumaturge.registry.TTSpellBehaviors;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * 从本 mod 视角看到的 Thaumaturge 法术注册表。
 * 行为类型直接注册进 Thaumaturge 自己的 {@code TTSpellBehaviors.BEHAVIORS}，
 * 没有附加 mod 钩子，也没有可混入之处。
 * 法术部件是 JSON 描述的，见 {@code thaumaturge/spell_part/aewrench.json}。
 */
public final class FocusElements {

    /** 扳手行为，路径与法术部件相同，免得两者走岔。 */
    public static final DeferredHolder<SpellBehaviorType<?>, SpellBehaviorType<FocusEffectAEWrench>>
            AEWRENCH_BEHAVIOR = TTSpellBehaviors.BEHAVIORS.register(
                    FocusEffectAEWrench.KEY.getPath(),
                    () -> new SpellBehaviorType<>(SpellPartKind.EFFECT, FocusEffectAEWrench.CODEC));

    /** 与行为配对的法术部件，由 {@code thaumaturge/spell_part/aewrench.json} 提供。 */
    public static final ResourceKey<SpellPart> AEWRENCH =
            ResourceKey.create(SpellPart.REGISTRY_KEY, TTIds.rl(FocusEffectAEWrench.KEY.getPath()));

    private FocusElements() {}

    /** 上面几项进的是 Thaumaturge 自己的注册表，这个总线没东西可交。
     * 留着它是因为要调用它才会跑本类的静态初始化，而那必须早于数据包解析 spell_part。 */
    public static void register(IEventBus modBus) {}
}
