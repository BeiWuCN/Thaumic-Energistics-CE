package thaumicenergistics_ce.arcane;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneCraftingStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * The ME network, presented to Thaumaturge as the place an arcane craft gets its ingredients.
 *
 * Thaumaturge asks the store to {@link #consume} the grid twice: once with {@code simulate} true to
 * check the network can pay, and once for real when the craft goes through. Only the second call
 * takes anything, which is what makes a failed craft cost nothing.
 *
 * <p>One item leaves the network per occupied grid cell, whatever the cell holds: the terminal's
 * grid is the recipe's shape, not the payment, and {@code TerminalArcaneCraftingInput} already
 * counts each cell as a single ingredient when it matches.
 *
 * <p>Energy is paid through {@link StorageHelper}, as AE2's own crafting terminal does, so a craft
 * that cannot pay for the extraction fails at the check rather than half-completing.
 */
public final class NetworkArcaneCraftingStore implements IArcaneCraftingStore {

    private final MEStorage storage;
    private final IEnergySource energy;
    private final IActionSource source;

    public NetworkArcaneCraftingStore(MEStorage storage, IEnergySource energy, IActionSource source) {
        this.storage = storage;
        this.energy = energy;
        this.source = source;
    }

    /** Whether the network was available at all. */
    public boolean isUsable() {
        return storage != null && energy != null && source != null;
    }

    /**
     * Checks that the network can supply one of every item on the grid, and takes them when it can.
     *
     * <p>Every extraction is simulated first, each against what the earlier cells left, or a recipe
     * needing two iron would be accepted by a network holding one. Nothing is taken during the check,
     * so a craft that fails later costs nothing.
     *
     * @param consumption what the craft uses up; only {@link Consumption#grid()} concerns the network
     * @param simulate    true to only check that the network holds the items
     * @return whether the network held them all (and, when not simulating, they were taken)
     */
    @Override
    public boolean consume(Consumption consumption, boolean simulate) {
        if (!isUsable()) {
            return false;
        }
        // What is left to spend, so two cells holding the same item cannot both spend the same unit.
        var remaining = new HashMap<AEItemKey, Long>();
        List<AEItemKey> claimed = new ArrayList<>();

        for (ItemStack cell : consumption.grid()) {
            if (cell.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(cell);
            if (key == null) {
                return false;
            }
            long left = remaining.computeIfAbsent(key,
                    k -> storage.extract(k, Long.MAX_VALUE, Actionable.SIMULATE, source));
            if (left < 1) {
                return false;
            }
            remaining.put(key, left - 1);
            claimed.add(key);
        }
        if (simulate) {
            return true;
        }
        // An extraction that comes up short is not rolled back: the network changed underneath the
        // check, and losing those items beats duplicating what the craft could not pay for.
        for (AEItemKey key : claimed) {
            StorageHelper.poweredExtraction(energy, storage, key, 1, source);
        }
        return true;
    }
}
