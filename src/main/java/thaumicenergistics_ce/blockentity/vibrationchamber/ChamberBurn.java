package thaumicenergistics_ce.blockentity.vibrationchamber;

import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber.BurnState;

/**
 * The chamber's burn: what one unit of fuel is worth and how much of it is left.
 * <ul>
 *   <li>Potentia burns 1.6x duration and power, ignis at the base rate, everything else at half.
 *   <li>{@link BurnState} is worked out from the room left in the energy slot, never from the state before.
 *   <li>{@link #update} tells the client when the state turns; the caller is told when a unit burns out.
 * </ul>
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

    /** Whether the burn may run at all: burning, or idle with fuel waiting. */
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
     * Reads the state off the room left and the burn, and tells the client when it turned. "Full" is a
     * level, not a latch, so the state, and the line the screen draws from it, follows the gauge.
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

    /** One tick of the burn, or of the smallest burn possible; never zero, so room/rate is a tick count. */
    double tickPower() {
        return burnTicksRemaining > 0 ? Math.max(aePerTick, 1.0) : BASE_AE_PER_TICK / 2.0;
    }

    /** Only ticks whose power fits are burnt, the rest later: the unit freezes, it does not restart. */
    int ticksThatFit(int ticksSinceLast) {
        return (int) Math.min(burnTicksRemaining, Math.min(ticksSinceLast, energy.room() / tickPower()));
    }

    /** Takes the burnt ticks off the unit; true once it ran out, which the caller tells the client. */
    boolean spend(int burntTicks) {
        burnTicksRemaining -= burntTicks;
        if (burnTicksRemaining > 0) {
            return false;
        }
        burnTicksRemaining = 0;
        aePerTick = 0;
        return true;
    }

    /** Lights the unit: one unit of fuel out of the slot, and the aspect's ticks and rate into the burn. */
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

    /** The state a reload starts in; the caller works it out from the slot rather than reading it back. */
    void setState(BurnState restored) {
        state = restored;
    }
}
