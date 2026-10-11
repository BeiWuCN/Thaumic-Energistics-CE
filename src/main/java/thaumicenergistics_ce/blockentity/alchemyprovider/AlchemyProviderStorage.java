package thaumicenergistics_ce.blockentity.alchemyprovider;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.util.ThETransaction;

final class AlchemyProviderStorage implements MEStorage {

    private final BlockEntityAlchemyProvider provider;

    AlchemyProviderStorage(BlockEntityAlchemyProvider provider) {
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
        int wanted = clamp(amount);
        return mode.isSimulate()
                ? provider.previewInsert(aspect, wanted)
                : ThETransaction.apply(transaction -> provider.insert(aspect, wanted, transaction));
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
            Identifier id = entry.aspect().unwrapKey().map(k -> k.identifier()).orElse(null);
            if (id != null && entry.amount() > 0) {
                out.add(AEssentiaKey.of(id), entry.amount());
            }
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable(
                "block.thaumicenergistics_ce.alchemy_provider");
    }

    private static int clamp(long amount) {
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }
}
