package com.beiwu.thaumicenergistics_ce.blockentity.assembler;

import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;

/** 本机存起来的 vis：一个标量供合成扣费，六根柱子读的是按元质拆开的份额。
 * 定价读池子，{@link #reconcileAspectVis()} 让两边说的是同一件事。 */
final class AssemblerVisPool {

    // 300 Vis total, split equally across the six primal tanks.
    static final int IDLE_TARGET = 300;
    static final int ASPECT_CAPACITY = 50;

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
        this.bufferedVis = Math.clamp(amount, 0, capacity());
        reconcileAspectVis();
    }

    int aspectVis(int index) {
        return index >= 0 && index < aspectVis.length ? aspectVis[index] : 0;
    }

    int[] elements() {
        return aspectVis.clone();
    }

    void applyPayment(AssemblerVisPayment.Plan plan) {
        restoreAspects(plan.remainingElements());
    }

    int visTarget(boolean crafting, int craftPrice) {
        return capacity();
    }

    private int capacity() {
        return ASPECT_CAPACITY * aspectVis.length;
    }

    /** 把整数 vis 记到某个要素名下并加进池子；进入缓冲只有这一条路。 */
    void bankVis(int amount, int primalIndex) {
        if (amount <= 0 || primalIndex < 0 || primalIndex >= aspectVis.length) {
            return;
        }
        amount = Math.min(amount, ASPECT_CAPACITY - aspectVis[primalIndex]);
        aspectVis[primalIndex] += amount;
        bufferedVis += amount;
    }

    /** 存不带自身要素的 vis，六个平分：Thaumaturge 的灵气每区块只有一个标量，
     * 换别的切法等于编出一份游戏里从没有过的分布。 */
    void bankVisEvenly(int amount) {
        if (amount <= 0) {
            return;
        }
        amount = Math.min(amount, capacity() - bufferedVis);
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
        for (int i = 0; i < aspectVis.length; i++) {
            aspectVis[i] = Math.clamp(aspectVis[i], 0, ASPECT_CAPACITY);
            sum += aspectVis[i];
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

    void readNbt(CompoundTag input) {
        bufferedVis = Math.clamp(input.getInt(TAG_BUFFERED_VIS), 0, capacity());
        int[] savedAspects = input.getIntArray(TAG_ASPECT_VIS);
        if (savedAspects.length == aspectVis.length) {
            restoreAspects(savedAspects);
        } else {
            // 写于柱子拆分之前，没有拆分可恢复：把池子平均铺开。
            spreadEvenly(bufferedVis);
        }
    }

    void writeNbt(CompoundTag tag) {
        tag.putInt(TAG_BUFFERED_VIS, bufferedVis);
        tag.putIntArray(TAG_ASPECT_VIS, aspectVis);
    }

    void readSync(CompoundTag input) {
        bufferedVis = Math.clamp(input.getInt(TAG_BUFFERED_VIS), 0, capacity());
        int[] syncedAspects = input.getIntArray(TAG_ASPECT_VIS);
        if (syncedAspects.length == aspectVis.length) {
            restoreAspects(syncedAspects);
        } else {
            spreadEvenly(bufferedVis);
        }
    }

    private void restoreAspects(int[] saved) {
        bufferedVis = 0;
        for (int i = 0; i < aspectVis.length; i++) {
            aspectVis[i] = Math.clamp(saved[i], 0, ASPECT_CAPACITY);
            bufferedVis += aspectVis[i];
        }
    }
}
