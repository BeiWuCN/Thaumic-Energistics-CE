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

/**
 * Presents a Thaumaturge essentia container to the ME network as storage.
 *
 * <p>This is what lets a jar be part of the network rather than merely something a bus shuttles essentia
 * into and out of. A storage bus mounts one of these, and from then on the jar's contents are listed in
 * the terminal, count towards what the network holds, and can be inserted into and extracted from like
 * any other storage.
 *
 * <p>Amounts are {@code int} on the Thaumaturge side and {@code long} on the ME side. Every conversion
 * here is the narrowing direction, so each one is clamped - a container cannot hold more than
 * {@code Integer.MAX_VALUE} of anything, and asking it to would wrap to a negative amount.
 */
public final class EssentiaMEStorage implements MEStorage {

    private final IEssentiaStorage storage;

    public EssentiaMEStorage(IEssentiaStorage storage) {
        this.storage = storage;
    }

    /** The container behind this view, for a caller that needs to talk to it directly. */
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
        // Thaumaturge reports what it took rather than what it was offered, and the simulate flag is its
        // own - so a dry run and a real one are the same call with a different last argument.
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

    /** A version counter the network can watch instead of polling contents. */
    public long contentRevision() {
        return storage.contentRevision();
    }

    /**
     * What this storage is, for AE2's diagnostic output.
     *
     * <p>A plain description rather than the container's own name: the container is a block, and asking it
     * for a name would mean asking the block entity - which this adapter deliberately does not hold, so
     * that it cannot go stale when the container is replaced.
     */
    @Override
    public net.minecraft.network.chat.Component getDescription() {
        return net.minecraft.network.chat.Component.translatable("gui.thaumicenergistics_ce.essentia_storage");
    }
}
