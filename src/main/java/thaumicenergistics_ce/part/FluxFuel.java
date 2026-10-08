package thaumicenergistics_ce.part;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import org.jspecify.annotations.Nullable;

/** AE 先于燃料被取走，任一步失败即交还；不完整的支付买不到东西。 */
final class FluxFuel {

    /** 本 mod 的定价，一点 100 AE：设计上从来没给一点定过价。 */
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
            // [auram] 已经取出：放回去，别白烧掉。
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
