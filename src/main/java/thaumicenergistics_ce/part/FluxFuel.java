package thaumicenergistics_ce.part;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import org.jspecify.annotations.Nullable;

/** AE 先于燃料被取走，任一步失败即交还，所以不完整的支付什么都买不到。 */
final class FluxFuel {

    // TECE 自己的费率：设计并未定下一点的价钱。
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
            // [auram] 已经取出：放回去，而不是白白烧掉。
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
