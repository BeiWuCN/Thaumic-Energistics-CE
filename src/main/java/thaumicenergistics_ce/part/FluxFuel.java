package thaumicenergistics_ce.part;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import org.jspecify.annotations.Nullable;

/** AE is taken before the fuels and handed back if either fails, so a partial payment buys nothing. */
final class FluxFuel {

    // TECE's own rate: the design left a point's price open.
    static final double AE_PER_POINT = 100.0;

    private FluxFuel() {}

    static boolean take(
            IEnergyService energy,
            MEStorage storage,
            AEKey auram,
            AEKey ordo,
            IActionSource source,
            int points) {
        double price = AE_PER_POINT * points;
        double paid = energy.extractAEPower(price, Actionable.MODULATE, PowerMultiplier.CONFIG);
        if (paid + 1.0e-6 < price) {
            energy.injectPower(paid, Actionable.MODULATE);
            return false;
        }
        if (storage.extract(auram, points, Actionable.MODULATE, source) < points) {
            energy.injectPower(paid, Actionable.MODULATE);
            return false;
        }
        if (storage.extract(ordo, points, Actionable.MODULATE, source) < points) {
            // The auram is already out: put it back rather than burn it for nothing.
            storage.insert(auram, points, Actionable.MODULATE, source);
            energy.injectPower(paid, Actionable.MODULATE);
            return false;
        }
        return true;
    }

    static @Nullable FluxWait shortOf(
            IEnergyService energy,
            MEStorage storage,
            AEKey auram,
            AEKey ordo,
            IActionSource source,
            int points) {
        double price = AE_PER_POINT * points;
        double affordable = energy.extractAEPower(price, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (affordable + 1.0e-6 < price) {
            return FluxWait.NO_ENERGY;
        }
        boolean fuel = storage.extract(auram, points, Actionable.SIMULATE, source) >= points
                && storage.extract(ordo, points, Actionable.SIMULATE, source) >= points;
        return fuel ? null : FluxWait.LOW_ESSENTIA;
    }
}
