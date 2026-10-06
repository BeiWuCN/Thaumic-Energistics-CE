package thaumicenergistics_ce.arcane;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;

/**
 * What one aura payment costs: aura at a place, bought with AE. Shared, so the placed and the wireless
 * terminal cannot drift apart on the exchange rate.
 * <ul>
 *   <li>The place is what the two differ by: the placed terminal drains the chunk it stands in, the
 *       wireless one the chunk the player stands in, since a carried workbench has no block of its own.
 * </ul>
 */
public final class TerminalAuraPayment {

    public static final double AE_PER_VIS = 1_000.0;

    public static final int CENTIVIS_PER_VIS = 100;

    private TerminalAuraPayment() {}

    /**
     * Drains aura at {@code where} and pays for it from {@code energy}, simulated then committed;
     * all or nothing, so a short commit cannot throw out of Thaumaturge's payment handler.
     * @return the centivis supplied, never more than {@code needCentivis}
     */
    public static int pay(
            Level level, BlockPos where, @Nullable IEnergySource energy, int needCentivis, boolean simulate) {
        if (needCentivis <= 0 || energy == null) {
            return 0;
        }
        float wanted = (float) needCentivis / CENTIVIS_PER_VIS;
        float available = TcAura.drainVis(level, where, wanted, true);
        if (available <= 0.0F) {
            return 0;
        }
        int offered = Math.min(needCentivis, Math.round(available * CENTIVIS_PER_VIS));
        double cost = AE_PER_VIS * offered / CENTIVIS_PER_VIS;

        double payable = energy.extractAEPower(cost, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (payable < cost) {
            offered = (int) Math.floor(payable / AE_PER_VIS * CENTIVIS_PER_VIS);
            if (offered <= 0) {
                return 0;
            }
            cost = AE_PER_VIS * offered / CENTIVIS_PER_VIS;
        }
        if (simulate) {
            return offered;
        }
        if (energy.extractAEPower(cost, Actionable.SIMULATE, PowerMultiplier.CONFIG) < cost) {
            return 0;
        }
        TcAura.drainVis(level, where, (float) offered / CENTIVIS_PER_VIS, false);
        energy.extractAEPower(cost, Actionable.MODULATE, PowerMultiplier.CONFIG);
        return offered;
    }
}
