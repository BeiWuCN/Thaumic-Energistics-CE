package thaumicenergistics_ce.focus;

import com.leclowndu93150.thaumaturge.api.casters.FocusElementType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics_ce.ThEIds;

/**
 * 从本 mod 视角看到的 Thaumaturge 核心元素注册表。{@code FocusElementType} 是一个
 * NeoForge 注册表，因此自行针对同一个 {@code REGISTRY_KEY} 注册一个 {@link DeferredRegister} 即可，
 * 既不需要附加 mod 钩子，也没有可混入之处。Thaumaturge 会在任何附加 mod 的构造函数
 * 运行之前把它绑定进 {@code FocusEngine}。
 */
public final class FocusElements {

    public static final DeferredRegister<FocusElementType> REGISTRY =
            DeferredRegister.create(FocusElementType.REGISTRY_KEY, ThEIds.MODID);

    /** 研究页面会直接 blit {@code icon}，所以是 {@code .png}；{@code color} 是 AE2 扳手的染色。 */
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
