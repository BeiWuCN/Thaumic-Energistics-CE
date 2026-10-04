package thaumicenergistics_ce.blockentity;

import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;

/** The vis this machine banks: one scalar that crafts are paid from, and the per-primal split the six
 * bars read. The split is never a second source of truth - prices and stall tests read the pool, and
 * {@link #reconcileAspectVis()} is what keeps the two telling the same story.
 *
 * <p>A collaborator of {@link BlockEntityArcaneAssembler} rather than a part of it: the assembler had
 * grown to carry every one of its own subsystems, and this is one that can be reasoned about alone. */
final class AssemblerVisPool {

    /** Idle vis ceiling; {@link #visTarget} raises it to a craft's price, the aura bounds it. */
    static final int IDLE_TARGET = 512;

    /** Ambient vis pulled from the aura and the relay network, buffered for the next craft. */
    private int bufferedVis;
    /** Vis banked per primal, indexed as the assembler's primals are: a breakdown of
     * {@link #bufferedVis}, never a second source of truth, as prices and stall tests read the pool. */
    private final int[] aspectVis;

    AssemblerVisPool(int primalCount) {
        this.aspectVis = new int[primalCount];
    }

    int bufferedVis() {
        return bufferedVis;
    }

    /** Sets the pool to a number of the caller's own choosing, for a hook that needs a known one. */
    void setBufferedVis(int amount) {
        this.bufferedVis = amount;
    }

    /** Banked vis of one primal, for the six vis bars. {@code index} is a primal index. */
    int aspectVis(int index) {
        return index >= 0 && index < aspectVis.length ? aspectVis[index] : 0;
    }

    /** The six holdings as {@code "n n n n n n"}, for the state dump. */
    String aspectVisTrace() {
        StringBuilder text = new StringBuilder();
        for (int value : aspectVis) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(value);
        }
        return text.toString();
    }

    /** Vis the machine wants to hold: the idle buffer, raised to a craft's price (up to 1728). */
    int visTarget(boolean crafting, int craftPrice) {
        return crafting ? Math.max(IDLE_TARGET, craftPrice) : IDLE_TARGET;
    }

    /** Banks whole vis against one aspect and adds it to the pool; the only path into the buffer. */
    void bankVis(int amount, int primalIndex) {
        if (amount <= 0 || primalIndex < 0 || primalIndex >= aspectVis.length) {
            return;
        }
        aspectVis[primalIndex] += amount;
        bufferedVis += amount;
    }

    /** Banks vis with no aspect of its own, spread evenly over the six: Thaumaturge's aura is one scalar
     * per chunk, so any other split would be inventing a distribution the game never had. */
    void bankVisEvenly(int amount) {
        if (amount <= 0) {
            return;
        }
        // Lowest holding first, one vis at a time. Taking the remainder by index instead would hand the
        // odd units to the same low aspects on every call, and the aura arrives in drips of a few vis --
        // so the last aspects would never be topped up, and the six bars would sit at two heights. This
        // also levels a split the relay path left uneven, which is the whole point of banking evenly.
        for (int i = 0; i < amount; i++) {
            aspectVis[lowestAspect()]++;
        }
        bufferedVis += amount;
        reconcileAspectVis();
    }

    /** The index of the smallest holding, ties going to the lowest index. */
    private int lowestAspect() {
        int lowest = 0;
        for (int i = 1; i < aspectVis.length; i++) {
            if (aspectVis[i] < aspectVis[lowest]) {
                lowest = i;
            }
        }
        return lowest;
    }

    /** Fills the six aspects without touching the pool: what {@link #readNbt} falls back to for a save
     * written before the bars were split, where there is no split to restore. */
    void spreadEvenly(int amount) {
        Arrays.fill(aspectVis, 0);
        int base = amount / aspectVis.length;
        int remainder = amount % aspectVis.length;
        for (int i = 0; i < aspectVis.length; i++) {
            aspectVis[i] = base + (i < remainder ? 1 : 0);
        }
    }

    /** Takes from pool and aspects in proportion, so the six bars drain together: a craft is one lump. */
    void spendVis(int amount) {
        int spent = Math.min(bufferedVis, Math.max(0, amount));
        if (spent <= 0) {
            return;
        }
        int total = bufferedVis;
        bufferedVis -= spent;
        int taken = 0;
        for (int i = 0; i < aspectVis.length; i++) {
            int share = (int) ((long) aspectVis[i] * spent / total);
            share = Math.min(share, aspectVis[i]);
            aspectVis[i] -= share;
            taken += share;
        }
        // Rounding can leave a few vis unaccounted for; the largest holding is the one that can absorb it.
        int leftover = spent - taken;
        if (leftover > 0) {
            int largest = 0;
            for (int i = 1; i < aspectVis.length; i++) {
                if (aspectVis[i] > aspectVis[largest]) {
                    largest = i;
                }
            }
            aspectVis[largest] = Math.max(0, aspectVis[largest] - leftover);
        }
        reconcileAspectVis();
    }

    /** Forces the six aspects to add up to {@link #bufferedVis}: the pool is the real state, the split
     * only what the bars read. */
    void reconcileAspectVis() {
        int sum = 0;
        for (int value : aspectVis) {
            sum += value;
        }
        int difference = bufferedVis - sum;
        if (difference == 0) {
            return;
        }
        if (difference > 0) {
            // Lowest first, as banking evenly does: dropping it all on one aspect would show up as one
            // bar standing taller than the other five.
            for (int i = 0; i < difference; i++) {
                aspectVis[lowestAspect()]++;
            }
            return;
        }
        int excess = -difference;
        while (excess > 0) {
            int largest = 0;
            for (int i = 1; i < aspectVis.length; i++) {
                if (aspectVis[i] > aspectVis[largest]) {
                    largest = i;
                }
            }
            int take = Math.min(excess, aspectVis[largest]);
            if (take <= 0) {
                break;
            }
            aspectVis[largest] -= take;
            excess -= take;
        }
    }

    /** Restores the pool and its split from a saved tag, with the same repair a load does: a tag whose
     * split does not fit is one written before the bars were split, and is spread evenly. */
    void readNbt(CompoundTag tag) {
        bufferedVis = tag.getInt("BufferedVis");
        int[] savedAspects = tag.getIntArray("AspectVis");
        if (savedAspects.length == aspectVis.length) {
            System.arraycopy(savedAspects, 0, aspectVis, 0, aspectVis.length);
            // A hand-edited tag must not leave six bars disagreeing with the pool.
            reconcileAspectVis();
        } else {
            // Written before the bars were split, so there is no split to restore: spread the pool evenly.
            spreadEvenly(bufferedVis);
        }
    }

    /** Writes the pool and the split it is broken down into, under the keys a save has always used. */
    void writeNbt(CompoundTag tag) {
        tag.putInt("BufferedVis", bufferedVis);
        tag.putIntArray("AspectVis", aspectVis);
    }

    /** Applies a sync tag: the pool and its split, and deliberately no repair of the split, which the
     * server has already reconciled and which a client has no business second-guessing. */
    void readSync(CompoundTag tag) {
        bufferedVis = tag.getInt("BufferedVis");
        int[] syncedAspects = tag.getIntArray("AspectVis");
        if (syncedAspects.length == aspectVis.length) {
            System.arraycopy(syncedAspects, 0, aspectVis, 0, aspectVis.length);
        }
    }
}
