package thaumicenergistics_ce.blockentity.essentiaprovider;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;

/**
 * The provider seen as an ME inventory: what the grid hands over, for the world to take away.
 * <ul>
 *   <li>The buffer holds the essentia; everything asked here is asked of it.
 *   <li>Extraction is refused: essentia in the buffer is on its way out, never back in.
 * </ul>
 */
final class EssentiaProviderStorage implements MEStorage {

    private final BlockEntityEssentiaProvider provider;

    EssentiaProviderStorage(BlockEntityEssentiaProvider provider) {
        this.provider = provider;
    }

    @Override
    public long insert(
            AEKey what,
            long amount,
            Actionable mode,
            IActionSource source) {
        if (!(what instanceof AEssentiaKey key) || amount <= 0) {
            return 0;
        }
        Holder<IAspect> aspect = key.resolveAspect();
        if (aspect == null) {
            return 0;
        }
        return provider.insert(aspect, clamp(amount), mode.isSimulate());
    }

    @Override
    public long extract(
            AEKey what,
            long amount,
            Actionable mode,
            IActionSource source) {
        return 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        var contents = provider.contents();
        for (var entry : contents.entries()) {
            ResourceLocation id = entry.aspect().unwrapKey().map(k -> k.location()).orElse(null);
            if (id != null && entry.amount() > 0) {
                out.add(AEssentiaKey.of(id), entry.amount());
            }
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable(
                "block.thaumicenergistics_ce.essentia_provider");
    }

    /** The buffer holds ints; a single AE insert cannot exceed what one aspect slot allows anyway. */
    private static int clamp(long amount) {
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }
}
