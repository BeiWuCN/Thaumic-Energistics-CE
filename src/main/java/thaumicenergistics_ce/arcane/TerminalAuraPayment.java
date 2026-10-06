package thaumicenergistics_ce.arcane;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableItem;
import appeng.api.upgrades.IUpgradeableObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.init.ModItems;

/**
 * What one aura payment costs: aura at a place, either bought with AE or taken as aura straight.
 * Shared, so the placed and the wireless terminal cannot drift apart on the exchange rate. The
 * place is what the two differ by: the placed terminal drains the chunk it stands in, the wireless
 * one the chunk the player stands in, since a carried workbench has no block of its own.
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

    /**
     * Whether the terminal stack carries the vis connection card: only then does a craft take its untyped
     * vis from the aura. A stack whose upgrades cannot be read answers false and keeps the power path.
     */
    public static boolean visConnectionInstalled(ItemStack terminal) {
        if (terminal.isEmpty() || !(terminal.getItem() instanceof IUpgradeableItem upgradeable)) {
            return false;
        }
        IUpgradeInventory upgrades = upgradeable.getUpgrades(terminal);
        return upgrades != null && upgrades.isInstalled(ModItems.VIS_CONNECTION_CARD.get());
    }

    /**
     * Whether the machine the player has open carries the vis connection card; the carried terminal and
     * the one placed on a cable answer through their own upgrade inventory.
     */
    public static boolean visConnectionInstalled(IUpgradeableObject machine) {
        IUpgradeInventory upgrades = machine.getUpgrades();
        return upgrades != null && upgrades.isInstalled(ModItems.VIS_CONNECTION_CARD.get());
    }

    /**
     * Drains aura at {@code where} for a terminal holding the vis connection card. No energy source takes
     * part, so this craft's untyped vis costs the network nothing.
     * @return the centivis supplied, never more than {@code needCentivis}
     */
    public static int payAura(Level level, BlockPos where, int needCentivis, boolean simulate) {
        if (needCentivis <= 0 || level == null || level.isClientSide()) {
            return 0;
        }
        float available = TcAura.drainVis(level, where, (float) needCentivis / CENTIVIS_PER_VIS, true);
        if (available <= 0.0F) {
            return 0;
        }
        int offered = Math.min(needCentivis, Math.round(available * CENTIVIS_PER_VIS));
        if (offered <= 0) {
            return 0;
        }
        if (simulate) {
            return offered;
        }
        // Committed as the amount the pass above saw, not as a fresh reading: Thaumaturge throws when one
        // craft's two aura passes disagree, so the aura is deliberately only asked once.
        TcAura.drainVis(level, where, (float) offered / CENTIVIS_PER_VIS, false);
        return offered;
    }
}
