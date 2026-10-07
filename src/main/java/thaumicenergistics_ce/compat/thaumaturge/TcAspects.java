package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import java.util.List;
import net.minecraft.resources.ResourceKey;

/**
 * 本 mod 用到的要素键。{@code TTAspects} 的常量只出现在方法体里，
 * 从不进入本 mod 自己的签名，整套转发即可，不必包装类型本身。
 */
public final class TcAspects {
    private TcAspects() {}

    public static final ResourceKey<IAspect> AER = TTAspects.AER;
    public static final ResourceKey<IAspect> AQUA = TTAspects.AQUA;
    public static final ResourceKey<IAspect> IGNIS = TTAspects.IGNIS;
    public static final ResourceKey<IAspect> ORDO = TTAspects.ORDO;
    public static final ResourceKey<IAspect> PERDITIO = TTAspects.PERDITIO;
    public static final ResourceKey<IAspect> TERRA = TTAspects.TERRA;

    public static final ResourceKey<IAspect> POTENTIA = TTAspects.POTENTIA;
    public static final ResourceKey<IAspect> AURAM = TTAspects.AURAM;
    public static final ResourceKey<IAspect> VITIUM = TTAspects.VITIUM;
    public static final ResourceKey<IAspect> COGNITIO = TTAspects.COGNITIO;

    /** 六个元质，顺序即六根 vis 柱所用的固定顺序。 */
    public static final List<ResourceKey<IAspect>> PRIMALS = TTAspects.PRIMALS;
}
