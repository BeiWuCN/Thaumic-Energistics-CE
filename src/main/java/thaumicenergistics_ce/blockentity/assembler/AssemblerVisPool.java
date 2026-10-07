package thaumicenergistics_ce.blockentity.assembler;

import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;

/** 本机存储的 vis：一个标量，合成由它扣费；六根柱子读的是按元质拆分的份额。
 * 定价读池子，{@link #reconcileAspectVis()} 让两者说同一件事。 */
final class AssemblerVisPool {

    /** 空闲时的 vis 上限；{@link #visTarget} 把它提到一次合成的价格，灵气则给它封顶。 */
    static final int IDLE_TARGET = 512;

    /** 池子的写入名称。存档标签与更新标签共用它们，Jade
     * 载荷在它自己的契约里写出了第一个名字；两者必须保持一致。 */
    private static final String TAG_BUFFERED_VIS = "BufferedVis";
    private static final String TAG_ASPECT_VIS = "AspectVis";

    /** 从灵气与继电器网络抽取的环境 vis，缓冲给下一次合成。 */
    private int bufferedVis;
    /** 按元质存储的 vis，索引方式与组装机的元质一致：它是
     * {@link #bufferedVis} 的拆分，绝不是第二个真相来源，因为定价与停滞判定读的都是池子。 */
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

    /** 把整数 vis 记到某个要素名下并加入池子；这是进入缓冲的唯一路径。 */
    void bankVis(int amount, int primalIndex) {
        if (amount <= 0 || primalIndex < 0 || primalIndex >= aspectVis.length) {
            return;
        }
        aspectVis[primalIndex] += amount;
        bufferedVis += amount;
    }

    /** 存入不带自身要素的 vis，六个要素平分：Thaumaturge 的灵气每区块是一个
     * 标量，任何别的切分都是在编造游戏里从未有过的分布。 */
    void bankVisEvenly(int amount) {
        if (amount <= 0) {
            return;
        }
        // 先给存量最少的，一次一 vis。按索引取余数会把零头每次都给
        // 同样那几个低存量要素，末尾的要素就永远补不上。
        for (int i = 0; i < amount; i++) {
            aspectVis[lowestAspect()]++;
        }
        bufferedVis += amount;
        reconcileAspectVis();
    }

    /** 最小存量的索引，并列时取索引最小者。 */
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
        // 取整可能留下几点 vis 没着落；存量最大的那个才吸收得下。
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
            // 与平均入账一样先给最低的：全压在一个要素上会表现为一根
            // 柱子比另外五根高。
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

    /** 从存档标签恢复池子及其拆分，并做与加载相同的修复：拆分对不上的标签
     * 是在柱子拆分之前写入的，按平均铺开。 */
    void readNbt(CompoundTag tag) {
        bufferedVis = tag.getInt(TAG_BUFFERED_VIS);
        int[] savedAspects = tag.getIntArray(TAG_ASPECT_VIS);
        if (savedAspects.length == aspectVis.length) {
            System.arraycopy(savedAspects, 0, aspectVis, 0, aspectVis.length);
            // 手改过的标签不能留下六根柱子与池子对不上的局面。
            reconcileAspectVis();
        } else {
            // 写于柱子拆分之前，没有可恢复的拆分：把池子平均铺开。
            spreadEvenly(bufferedVis);
        }
    }

    void writeNbt(CompoundTag tag) {
        tag.putInt(TAG_BUFFERED_VIS, bufferedVis);
        tag.putIntArray(TAG_ASPECT_VIS, aspectVis);
    }

    void readSync(CompoundTag tag) {
        bufferedVis = tag.getInt(TAG_BUFFERED_VIS);
        int[] syncedAspects = tag.getIntArray(TAG_ASPECT_VIS);
        if (syncedAspects.length == aspectVis.length) {
            System.arraycopy(syncedAspects, 0, aspectVis, 0, aspectVis.length);
        }
    }
}
