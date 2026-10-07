package thaumicenergistics_ce.blockentity.vibrationchamber;

import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber.BurnState;

/**
 * 振动室的燃烧：一份燃料值多少，它还剩下多少。[Potentia] 燃烧的持续时间和
 * 功率为 1.6 倍，[ignis] 为基础速率，其它一律减半；[BurnState] 由能量槽
 * 剩余的空间推出，绝不沿用之前的状态。{@link #update} 在状态翻转时通知
 * 客户端，而调用方会在一份燃料烧尽时被告知。
 */
final class ChamberBurn {

    static final int BASE_BURN_TICKS = 800;

    static final double BASE_AE_PER_TICK = 200.0;

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

    /** 燃烧是否允许进行：要么正在燃烧，要么空闲但有燃料在等。 */
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
     * 根据剩余空间与燃烧读出状态，并在状态翻转时通知客户端。“Full” 是一个
     * 水平而非锁存，所以状态以及界面据此绘制的线条都随仪表走。
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

    /** 一次燃烧的 tick 量，或最小可能燃烧量；绝不为 0，因此 剩余空间/速率 就是 tick 数。 */
    double tickPower() {
        return burnTicksRemaining > 0 ? Math.max(aePerTick, 1.0) : BASE_AE_PER_TICK / 2.0;
    }

    /** 只烧掉其功率装得下的那些 tick，剩下的以后再说：这份燃料冻结，而不是重新开始。 */
    int ticksThatFit(int ticksSinceLast) {
        return (int) Math.min(burnTicksRemaining, Math.min(ticksSinceLast, energy.room() / tickPower()));
    }

    /** 把烧掉的 tick 从这份燃料上扣掉；烧尽时返回 true，由调用方通知客户端。 */
    boolean spend(int burntTicks) {
        burnTicksRemaining -= burntTicks;
        if (burnTicksRemaining > 0) {
            return false;
        }
        burnTicksRemaining = 0;
        aePerTick = 0;
        return true;
    }

    /** 点燃这份燃料：从槽位取出一份燃料，并把该要素的 tick 数和速率写入燃烧。 */
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
            return BASE_AE_PER_TICK * 1.6;
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

    /** 重载后起始的状态；调用方从槽位推出它，而不是把它读回来。 */
    void setState(BurnState restored) {
        state = restored;
    }
}
