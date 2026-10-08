package thaumicenergistics_ce.essentia;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.core.Holder;

/**
 * 必须直接问它会做什么改动的存储，因为它不认识事务：管道的一面，
 * Thaumaturge 的管道 API 在被问的那一刻就应用改动；还有通往提供器网格的
 * 无线连接，它的电耗在任何日志之外。
 *
 * <p>所以 {@link thaumicenergistics_ce.util.ThETransaction#preview} 落在这种存储上，会正好留下
 * 它本该避免的那个改动；只想问一声的调用方，必须改调
 * 这些方法。
 */
public interface ThEImmediateStorage {

    /** 插入 {@code amount} 会收下多少，问的时候什么都不改。 */
    int previewInsert(Holder<IAspect> aspect, int amount);

    /** 抽取 {@code amount} 会给出多少，问的时候什么都不改。 */
    int previewExtract(Holder<IAspect> aspect, int amount);
}
