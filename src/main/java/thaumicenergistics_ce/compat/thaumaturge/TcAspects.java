package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TCAspects;
import java.util.List;
import net.minecraft.resources.ResourceKey;

/**
 * 本 mod 用到的要素键。{@code TCAspects} 的常量只出现在方法体里，
 * 从不进入本 mod 自己的签名，整套转发，不必包装类型本身。
 * 上游把注册表类前缀从 {@code TC} 改成 {@code TT} 时，要改的只有上面那行 import 和下面这些赋值。
 */
public final class TcAspects {
    private TcAspects() {}

    public static final ResourceKey<IAspect> AER = TCAspects.AER;
    public static final ResourceKey<IAspect> AQUA = TCAspects.AQUA;
    public static final ResourceKey<IAspect> IGNIS = TCAspects.IGNIS;
    public static final ResourceKey<IAspect> ORDO = TCAspects.ORDO;
    public static final ResourceKey<IAspect> PERDITIO = TCAspects.PERDITIO;
    public static final ResourceKey<IAspect> TERRA = TCAspects.TERRA;

    public static final ResourceKey<IAspect> POTENTIA = TCAspects.POTENTIA;
    public static final ResourceKey<IAspect> AURAM = TCAspects.AURAM;
    public static final ResourceKey<IAspect> VITIUM = TCAspects.VITIUM;
    public static final ResourceKey<IAspect> COGNITIO = TCAspects.COGNITIO;

    /** 六个元质，顺序即六根 vis 柱所用的固定顺序。 */
    public static final List<ResourceKey<IAspect>> PRIMALS = TCAspects.PRIMALS;
}
