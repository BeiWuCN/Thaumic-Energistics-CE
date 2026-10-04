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
 * <ul>
 *   <li>{@link #consume} runs twice, once simulated and once for real: only the second takes anything,
 *       which is what makes a failed craft cost nothing.
 *   <li>One item leaves the network per occupied grid cell; energy is paid through {@link StorageHelper}.
 * </ul>
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

    public boolean isUsable() {
        return storage != null && energy != null && source != null;
    }

    /**
     * Checks that the network can supply one of every item on the grid, and takes them when it can.
     * Each extraction is simulated against what the earlier cells left, or two iron would be paid by one.
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
        for (AEItemKey key : claimed) {
            StorageHelper.poweredExtraction(energy, storage, key, 1, source);
        }
        return true;
    }
}
