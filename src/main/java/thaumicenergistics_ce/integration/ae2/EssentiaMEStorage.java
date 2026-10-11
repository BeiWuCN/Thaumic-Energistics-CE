package thaumicenergistics_ce.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import thaumicenergistics_ce.integration.ae2.essentia.ThEImmediateStorage;
import thaumicenergistics_ce.util.ThETransaction;

public final class EssentiaMEStorage implements MEStorage {

    private final IEssentiaStorage storage;

    public EssentiaMEStorage(IEssentiaStorage storage) {
        this.storage = storage;
    }

    public IEssentiaStorage container() {
        return storage;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (AspectInstance entry : storage.contents().entries()) {
            Holder<IAspect> aspect = entry.aspect();
            if (aspect != null && entry.amount() > 0) {
                AEssentiaKey key = AEssentiaKey.of(aspect);
                if (key != null) {
                    out.add(key, entry.amount());
                }
            }
        }
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !(what instanceof AEssentiaKey key)) {
            return 0;
        }
        Holder<IAspect> aspect = key.resolveAspect();
        if (aspect == null) {
            return 0;
        }
        int wanted = (int) Math.min(amount, Integer.MAX_VALUE);
        return put(aspect, wanted, mode == Actionable.SIMULATE);
    }

    private int put(Holder<IAspect> aspect, int wanted, boolean dryRun) {
        if (dryRun && storage instanceof ThEImmediateStorage immediate) {
            return immediate.previewInsert(aspect, wanted);
        }
        return dryRun
                ? ThETransaction.preview(transaction -> storage.insert(aspect, wanted, transaction))
                : ThETransaction.apply(transaction -> storage.insert(aspect, wanted, transaction));
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !(what instanceof AEssentiaKey key)) {
            return 0;
        }
        Holder<IAspect> aspect = key.resolveAspect();
        if (aspect == null) {
            return 0;
        }
        int wanted = (int) Math.min(amount, Integer.MAX_VALUE);
        return take(aspect, wanted, mode == Actionable.SIMULATE);
    }

    private int take(Holder<IAspect> aspect, int wanted, boolean dryRun) {
        if (dryRun && storage instanceof ThEImmediateStorage immediate) {
            return immediate.previewExtract(aspect, wanted);
        }
        return dryRun
                ? ThETransaction.preview(transaction -> storage.extract(aspect, wanted, transaction))
                : ThETransaction.apply(transaction -> storage.extract(aspect, wanted, transaction));
    }

    public long contentRevision() {
        return storage.contentRevision();
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gui.thaumicenergistics_ce.essentia_storage");
    }
}
