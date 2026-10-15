package thaumicenergistics_ce.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import net.minecraft.core.Holder;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;

/** The provider's native return buffer is the sole owner of returned essentia. */
public final class EssentiaProviderReturn implements IEssentiaStorage {
    private final EssentiaPatternProvider provider;
    private long revision;

    public EssentiaProviderReturn(EssentiaPatternProvider provider) { this.provider = provider; }

    @Override
    public AspectList contents() {
        var inventory = provider.essentiaBuffer();
        var result = AspectList.EMPTY;
        for (int i = 0; i < inventory.size(); i++) {
            var stack = inventory.getStack(i);
            if (stack != null && stack.what() instanceof AEssentiaKey key) {
                var aspect = key.resolveAspect();
                if (aspect != null) result = result.add(aspect, (int) Math.min(Integer.MAX_VALUE, stack.amount()));
            }
        }
        return result;
    }

    @Override
    public int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
        if (amount <= 0 || !provider.essentiaEnabled()) return 0;
        var key = AEssentiaKey.of(aspect);
        return key == null ? 0 : (int) provider.essentiaBuffer().insert(key, amount,
                simulate ? Actionable.SIMULATE : Actionable.MODULATE, IActionSource.empty());
    }

    @Override
    public int extract(Holder<IAspect> aspect, int amount, boolean simulate) { return 0; }

    @Override
    public long contentRevision() { return revision; }

    public void inventoryChanged() { revision++; }
}
