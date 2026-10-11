package thaumicenergistics_ce.blockentity.vibrationchamber;

import thaumicenergistics_ce.config.ThEConfig;

import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber.BurnState;

final class ChamberBurn {


    static final double BASE_AE_PER_TICK = 200.0;

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

    double tickPower() {
        return burnTicksRemaining > 0 ? Math.max(aePerTick, 1.0) : BASE_AE_PER_TICK / 2.0;
    }

    int ticksThatFit(int ticksSinceLast) {
        return (int) Math.min(burnTicksRemaining, Math.min(ticksSinceLast, energy.room() / tickPower()));
    }

    boolean spend(int burntTicks) {
        burnTicksRemaining -= burntTicks;
        if (burnTicksRemaining > 0) {
            return false;
        }
        burnTicksRemaining = 0;
        aePerTick = 0;
        return true;
    }

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
            return Math.max(1, (int) (ThEConfig.BURN_TICKS.get() / 1.6F));
        }
        return Math.max(1, ThEConfig.BURN_TICKS.get() / 2);
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

    void setState(BurnState restored) {
        state = restored;
    }
}
