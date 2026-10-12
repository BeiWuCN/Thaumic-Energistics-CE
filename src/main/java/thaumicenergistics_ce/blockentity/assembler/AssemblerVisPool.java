package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import java.util.Arrays;

final class AssemblerVisPool {

    static final int IDLE_TARGET = 300;

    private static final String TAG_BUFFERED_VIS = "BufferedVis";
    private static final String TAG_ASPECT_VIS = "AspectVis";

    private int bufferedVis;
    private final int[] aspectVis;

    AssemblerVisPool(int primalCount) {
        this.aspectVis = new int[primalCount];
    }

    int bufferedVis() {
        return bufferedVis;
    }

    void setBufferedVis(int amount) {
        this.bufferedVis = Math.clamp(amount, 0, IDLE_TARGET);
        reconcileAspectVis();
    }

    int aspectVis(int index) {
        return index >= 0 && index < aspectVis.length ? aspectVis[index] : 0;
    }

    int visTarget(boolean crafting, int craftPrice) {
        return IDLE_TARGET;
    }

    void bankVis(int amount, int primalIndex) {
        if (amount <= 0 || primalIndex < 0 || primalIndex >= aspectVis.length) {
            return;
        }
        amount = Math.min(amount, IDLE_TARGET - bufferedVis);
        aspectVis[primalIndex] += amount;
        bufferedVis += amount;
    }

    void bankVisEvenly(int amount) {
        if (amount <= 0) {
            return;
        }
        amount = Math.min(amount, IDLE_TARGET - bufferedVis);
        for (int i = 0; i < amount; i++) {
            aspectVis[lowestAspect()]++;
        }
        bufferedVis += amount;
        reconcileAspectVis();
    }

    private int lowestAspect() {
        int lowest = 0;
        for (int i = 1; i < aspectVis.length; i++) {
            if (aspectVis[i] < aspectVis[lowest]) {
                lowest = i;
            }
        }
        return lowest;
    }

    void spreadEvenly(int amount) {
        Arrays.fill(aspectVis, 0);
        int base = amount / aspectVis.length;
        int remainder = amount % aspectVis.length;
        for (int i = 0; i < aspectVis.length; i++) {
            aspectVis[i] = base + (i < remainder ? 1 : 0);
        }
    }

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

    void readNbt(ValueInput input) {
        bufferedVis = Math.clamp(input.getIntOr(TAG_BUFFERED_VIS, 0), 0, IDLE_TARGET);
        int[] savedAspects = input.getIntArray(TAG_ASPECT_VIS).orElse(new int[0]);
        if (savedAspects.length == aspectVis.length) {
            System.arraycopy(savedAspects, 0, aspectVis, 0, aspectVis.length);
            reconcileAspectVis();
        } else {
            spreadEvenly(bufferedVis);
        }
    }

    void writeNbt(ValueOutput output) {
        output.putInt(TAG_BUFFERED_VIS, bufferedVis);
        output.putIntArray(TAG_ASPECT_VIS, aspectVis);
    }

    void readSync(ValueInput input) {
        bufferedVis = Math.clamp(input.getIntOr(TAG_BUFFERED_VIS, 0), 0, IDLE_TARGET);
        int[] syncedAspects = input.getIntArray(TAG_ASPECT_VIS).orElse(new int[0]);
        if (syncedAspects.length == aspectVis.length) {
            System.arraycopy(syncedAspects, 0, aspectVis, 0, aspectVis.length);
        }
        reconcileAspectVis();
    }
}
