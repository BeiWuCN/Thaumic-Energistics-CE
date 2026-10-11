package thaumicenergistics_ce.essentia;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.core.Holder;

public interface ThEImmediateStorage {

    int previewInsert(Holder<IAspect> aspect, int amount);

    int previewExtract(Holder<IAspect> aspect, int amount);
}
