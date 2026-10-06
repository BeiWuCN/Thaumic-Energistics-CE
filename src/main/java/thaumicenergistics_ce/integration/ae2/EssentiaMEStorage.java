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

/**
 * Presents a Thaumaturge essentia container to the ME network as storage. A storage bus mounts
 * one of these, and the jar's contents are then listed in the terminal, count towards what the
 * network holds, and insert and extract like any other storage. Amounts are {@code int} on one
 * side and {@code long} on the other, so every conversion is clamped.
 */
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
        // Thaumaturge reports what it took, and the simulate flag is its own; a dry run and a real one
        // are the same call with a different last argument.
        boolean simulate = mode == Actionable.SIMULATE;
        return storage.insert(aspect, wanted, simulate);
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
        boolean simulate = mode == Actionable.SIMULATE;
        return storage.extract(aspect, wanted, simulate);
    }

    public long contentRevision() {
        return storage.contentRevision();
    }

    /**
     * What this storage is, for AE2's diagnostic output: a plain description rather than the container's
     * own name, which would mean asking its block entity, a thing this adapter deliberately does not hold.
     */
    @Override
    public Component getDescription() {
        return Component.translatable("gui.thaumicenergistics_ce.essentia_storage");
    }
}
