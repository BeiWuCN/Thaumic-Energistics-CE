package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;

/** 本机存起来的 vis：一个标量供合成扣费，六根柱子读的是按元质拆开的份额。
 * 定价读池子，{@link #reconcileAspectVis()} 让两边说的是同一件事。 */
final class AssemblerVisPool {

    /** 空闲时的 vis 上限；{@link #visTarget} 把它提到一次合成的价格，灵气封顶。 */
    static final int IDLE_TARGET = 512;

    /** 池子的写入名。存档标签与更新标签共用，Jade 载荷在自己的契约里写明第一个；两个名字得一致。 */
    private static final String TAG_BUFFERED_VIS = "BufferedVis";
    private static final String TAG_ASPECT_VIS = "AspectVis";

    /** 从灵气和继电器网络抽来的环境 vis，缓冲给下一次合成。 */
    private int bufferedVis;
    /** 按元质存的 vis，索引跟组装机的元质一致：它是 {@link #bufferedVis} 的拆分，
     * 不是第二份真相；定价和停滞判定读的都是池子。 */
    private final int[] aspectVis;

    AssemblerVisPool(int primalCount) {
        this.aspectVis = new int[primalCount];
    }

    int bufferedVis() {
        return bufferedVis;
    }

    void setBufferedVis(int amount) {
        this.bufferedVis = amount;
    }

    int aspectVis(int index) {
        return index >= 0 && index < aspectVis.length ? aspectVis[index] : 0;
    }

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

    int visTarget(boolean crafting, int craftPrice) {
        return crafting ? Math.max(IDLE_TARGET, craftPrice) : IDLE_TARGET;
    }

    /** 把整数 vis 记到某个要素名下并加进池子；进入缓冲只有这一条路。 */
    void bankVis(int amount, int primalIndex) {
        if (amount <= 0 || primalIndex < 0 || primalIndex >= aspectVis.length) {
            return;
        }
        aspectVis[primalIndex] += amount;
        bufferedVis += amount;
    }

    /** 存不带自身要素的 vis，六个平分：Thaumaturge 的灵气每区块只有一个标量，
     * 换别的切法等于编出一份游戏里从没有过的分布。 */
    void bankVisEvenly(int amount) {
        if (amount <= 0) {
            return;
        }
        // 先给存量最少的，一次一 vis。按索引取余会把零头每次都塞给
        // 同样那几个低存量要素，末尾的要素永远补不上。
        for (int i = 0; i < amount; i++) {
            aspectVis[lowestAspect()]++;
        }
        bufferedVis += amount;
        reconcileAspectVis();
    }

    /** 最小存量的索引，并列取索引最小者。 */
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
        // 取整会留下几点 vis，只有存量最大的那个吸收得下。
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
            // 跟平均入账一样先给最低的：全压在一个要素上，会有一根柱子比另外五根高。
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

    /** 从保存的标签恢复池子与它的拆分，用的修法与加载时相同：拆分对不上的
     * 标签写于柱子拆分之前，一律平均铺开。 */
    void readNbt(ValueInput input) {
        bufferedVis = input.getIntOr(TAG_BUFFERED_VIS, 0);
        int[] savedAspects = input.getIntArray(TAG_ASPECT_VIS).orElse(new int[0]);
        if (savedAspects.length == aspectVis.length) {
            System.arraycopy(savedAspects, 0, aspectVis, 0, aspectVis.length);
            // 手改过的标签不能留下六根柱子与池子对不上。
            reconcileAspectVis();
        } else {
            // 写于柱子拆分之前，没有拆分可恢复：把池子平均铺开。
            spreadEvenly(bufferedVis);
        }
    }

    void writeNbt(ValueOutput output) {
        output.putInt(TAG_BUFFERED_VIS, bufferedVis);
        output.putIntArray(TAG_ASPECT_VIS, aspectVis);
    }

    void readSync(ValueInput input) {
        bufferedVis = input.getIntOr(TAG_BUFFERED_VIS, 0);
        int[] syncedAspects = input.getIntArray(TAG_ASPECT_VIS).orElse(new int[0]);
        if (syncedAspects.length == aspectVis.length) {
            System.arraycopy(syncedAspects, 0, aspectVis, 0, aspectVis.length);
        }
    }
}
