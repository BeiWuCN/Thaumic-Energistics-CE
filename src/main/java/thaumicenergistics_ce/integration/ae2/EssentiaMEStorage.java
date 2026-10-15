package thaumicenergistics_ce.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import net.minecraft.network.chat.Component;

/** Adapts the 1.0.2 simulation API without committing preview operations. */
public record EssentiaMEStorage(IEssentiaStorage storage) implements MEStorage {
    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (!(key instanceof AEssentiaKey essentia) || amount <= 0) return 0;
        var aspect = essentia.resolveAspect();
        return aspect == null ? 0 : storage.insert(aspect, (int) Math.min(Integer.MAX_VALUE, amount),
                mode == Actionable.SIMULATE);
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (!(key instanceof AEssentiaKey essentia) || amount <= 0) return 0;
        var aspect = essentia.resolveAspect();
        return aspect == null ? 0 : storage.extract(aspect, (int) Math.min(Integer.MAX_VALUE, amount),
                mode == Actionable.SIMULATE);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (var entry : storage.contents().sortedByAmount()) {
            var key = AEssentiaKey.of(entry.aspect());
            if (key != null && entry.amount() > 0) out.add(key, entry.amount());
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gui.thaumicenergistics_ce.essentia_storage");
    }
}
