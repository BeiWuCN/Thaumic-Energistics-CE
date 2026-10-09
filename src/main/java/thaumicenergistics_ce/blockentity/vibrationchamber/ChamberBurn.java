package thaumicenergistics_ce.blockentity.vibrationchamber;

import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber.BurnState;

/**
 * 振动室的燃烧：一份燃料值多少，还剩多少。
 * [Potentia] 的持续时间和功率是 1.6 倍，[ignis] 是基础速率，别的要素一律减半。
 * [BurnState] 由能量槽剩下的空间推出，不沿用之前的状态。
 * {@link #update} 在状态翻转时通知客户端，一份燃料烧尽时调用方会被告知。
 */
final class ChamberBurn {

    static final int BASE_BURN_TICKS = 800;

    /** 基础输出：potentia 乘 1.6、ignis 原样、其余减半。 */
    static final double BASE_AE_PER_TICK = 20.0;

    /** 单 tick 的产出天花板：[potentia] 的 1.6 倍，推给网格的上限就取在它上面。 */
    static final double PEAK_AE_PER_TICK = BASE_AE_PER_TICK * 1.6;

    static final String ASPECT_POTENTIA = "potentia";
    static final String ASPECT_IGNIS = "ignis";

    private final BlockEntityEssentiaVibrationChamber chamber;
    private final ChamberEssentiaTank tank;
    private final ChamberEnergyOutput energy;

    private int burnTicksRemaining;
    private int totalBurnTicks;
    private double aePerTick;

    private BurnState state = BurnState.IDLE;

    ChamberBurn(BlockEntityEssentiaVibrationChamber chamber, ChamberEssentiaTank tank, ChamberEnergyOutput energy) {
        this.chamber = chamber;
        this.tank = tank;
        this.energy = energy;
    }

    BurnState state() {
        return state;
    }

    boolean burning() {
        return state == BurnState.BURNING;
    }

    boolean paused() {
        return state == BurnState.PAUSED_FULL;
    }

    /** 允不允许燃烧：正在烧，或者空闲但有燃料在等。 */
    boolean mayBurn() {
        return state.mayBurn();
    }

    int remaining() {
        return burnTicksRemaining;
    }

    int total() {
        return totalBurnTicks;
    }

    double aePerTick() {
        return aePerTick;
    }

    float progress() {
        return totalBurnTicks <= 0 ? 0.0F : 1.0F - (float) burnTicksRemaining / totalBurnTicks;
    }

    /**
     * 按剩余空间和燃烧读出状态，状态翻转时通知客户端。
     * 「Full」是个水平不是锁存，状态和界面据此画的线都跟着仪表走。
     */
    void update(boolean onNetwork) {
        BurnState next;
        if (!onNetwork) {
            next = BurnState.NO_NETWORK;
        } else if (energy.isFull()) {
            next = BurnState.PAUSED_FULL;
        } else if (burnTicksRemaining > 0) {
            next = BurnState.BURNING;
        } else {
            next = BurnState.IDLE;
        }

        if (next != state) {
            state = next;
            chamber.setChanged();
            chamber.markForClientUpdate();
        }
    }

    /** 一次燃烧每 tick 的功率，最少也是这个值；绝不为 0，剩余空间除以速率就是 tick 数。 */
    double tickPower() {
        return burnTicksRemaining > 0 ? Math.max(aePerTick, 1.0) : BASE_AE_PER_TICK / 2.0;
    }

    /** 只烧功率装得下的那些 tick，剩下的以后再烧：这份燃料冻结，不重新开始。 */
    int ticksThatFit(int ticksSinceLast) {
        return (int) Math.min(burnTicksRemaining, Math.min(ticksSinceLast, energy.room() / tickPower()));
    }

    /** 从这份燃料扣掉烧掉的 tick；烧尽返回 true，由调用方通知客户端。 */
    boolean spend(int burntTicks) {
        burnTicksRemaining -= burntTicks;
        if (burnTicksRemaining > 0) {
            return false;
        }
        burnTicksRemaining = 0;
        aePerTick = 0;
        return true;
    }

    /** 点燃这份燃料：从槽位取一份，把该要素的 tick 数和速率写进燃烧。 */
    void start() {
        int burnTicks = burnTicksFor();
        double power = powerFor();

        tank.revertOne();
        burnTicksRemaining = burnTicks;
        totalBurnTicks = burnTicks;
        aePerTick = power;
        chamber.setChanged();
        chamber.markForClientUpdate();
    }

    private int burnTicksFor() {
        String path = tank.aspectPath();
        if (ASPECT_POTENTIA.equals(path)) {
            return (int) (BASE_BURN_TICKS / 1.6F);
        }
        return BASE_BURN_TICKS / 2;
    }

    private double powerFor() {
        String path = tank.aspectPath();
        if (ASPECT_POTENTIA.equals(path)) {
            return PEAK_AE_PER_TICK;
        }
        if (ASPECT_IGNIS.equals(path)) {
            return BASE_AE_PER_TICK;
        }
        return BASE_AE_PER_TICK / 2.0;
    }

    void applyStreamed(BurnState streamedState, double streamedRate) {
        state = streamedState;
        aePerTick = streamedRate;
    }

    void restore(int remaining, int total, double rate) {
        burnTicksRemaining = remaining;
        totalBurnTicks = total;
        aePerTick = rate;
    }

    /** 重载后起始的状态；调用方从槽位推出，不读回来。 */
    void setState(BurnState restored) {
        state = restored;
    }
}
