package thaumicenergistics_ce.part;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import org.jspecify.annotations.Nullable;

/**
 * What a cycle costs the drawing end: a point's worth of auram and ordo, and a hundred AE a point. The
 * AE is handed over before either fuel is touched and given back if the fuel does not follow, so a
 * partial payment can never buy a free cycle.
 */
final class FluxFuel {

    /** TECE's own rate: the design left the price of a point open. */
    static final double AE_PER_POINT = 100.0;

    private FluxFuel() {}

    /** Pays for {@code points} of flux and takes both fuels, or leaves the network as it found it. */
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

    /** The reason a cycle cannot be paid for, or {@code null} when it can. */
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
