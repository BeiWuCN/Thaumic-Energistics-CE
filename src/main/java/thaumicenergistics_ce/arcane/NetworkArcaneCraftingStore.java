package thaumicenergistics_ce.arcane;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneCraftingStore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * The ME network, presented to Thaumaturge as the place an arcane craft gets its ingredients.
 *
 * <p>This is what lets the terminal craft at all. Thaumaturge's transaction asks its store to
 * {@link #reserve} the ingredients a recipe needs and to {@link Reservation#commit} them once the craft
 * succeeds; a normal workbench reserves from its own grid, and this one reserves from the network. The
 * split matters: <b>reserve</b> only checks, and <b>commit</b> is the only thing that moves an item. That
 * ordering is what makes a failed craft cost nothing - if the reservation checks pass and the craft then
 * fails, nothing was taken and {@link Reservation#close} has nothing to undo.
 *
 * <p>Energy is paid through {@link StorageHelper}, exactly as AE2's own crafting terminal does, so pulling
 * ingredients out of the network costs the same here as anywhere else. A craft that cannot pay for the
 * extraction fails at the reserve step rather than half-completing.
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

    /**
     * A reservation that holds nothing, for when the network is unavailable.
     *
     * <p>An object rather than a null, because Thaumaturge's transaction calls methods on whatever the store
     * returns - a null would turn "this craft cannot happen" into a crash, and there is already a proper way
     * to say no: a reservation that reports itself invalid.
     */
    private static final IArcaneCraftingStore.Reservation NOTHING = new IArcaneCraftingStore.Reservation() {
        @Override
        public boolean isValid() {
            return false;
        }

        @Override
        public void commit(ItemStack output, List<ItemStack> remainders, AspectList crystals) {
            // Nothing was reserved, so there is nothing to commit.
        }

        @Override
        public void close() {
            // Nothing to release.
        }
    };

    /**
     * Works out what the network can contribute, without taking anything.
     *
     * <p>Reservation is simulated in full: every ingredient is checked against what is left after the
     * ingredients before it have been spoken for. Checking each against the whole network instead would
     * accept a recipe needing three iron when the network holds two, because each of the three would look
     * satisfiable on its own.
     */
    @Override
    public IArcaneCraftingStore.Reservation reserve(List<ItemStack> ingredients) {
        if (storage == null || energy == null || source == null) {
            return NOTHING;
        }
        // What is left to spend, so two ingredients of the same item cannot both spend the same unit.
        var remaining = new java.util.HashMap<AEItemKey, Long>();
        List<ItemStack> taken = new ArrayList<>();

        for (ItemStack ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(ingredient);
            if (key == null) {
                return NOTHING;
            }
            long want = ingredient.getCount();
            long left = remaining.computeIfAbsent(key,
                    k -> {
                        long available = storage.extract(k, Long.MAX_VALUE, Actionable.SIMULATE, source);
                        return available;
                    });
            if (left < want) {
                return NOTHING;
            }
            remaining.put(key, left - want);
            taken.add(ingredient.copy());
        }
        return new Reservation(taken);
    }

    /** Whether the network was available at all. */
    public boolean isUsable() {
        return storage != null && energy != null && source != null;
    }

    /**
     * A checked claim on the network, which either becomes a real extraction or is dropped.
     *
     * <p>{@code close} does nothing, and that is not laziness: nothing has been taken yet, so there is
     * nothing to release. The reservation is a promise about what the network held at the moment it was
     * asked, and if the craft never happens the promise simply lapses. A store that extracted on
     * {@code reserve} and refunded on {@code close} would move items twice for every craft and would lose
     * them outright if the game stopped in between.
     */
    private final class Reservation implements IArcaneCraftingStore.Reservation {

        private final List<ItemStack> claimed;

        /** True once the items have actually been extracted, so a second commit cannot charge twice. */
        private boolean committed;

        private Reservation(List<ItemStack> claimed) {
            this.claimed = claimed;
        }

        @Override
        public boolean isValid() {
            return !committed;
        }

        /**
         * Takes the reserved ingredients out of the network.
         *
         * <p>The output and remainders are ignored deliberately. The output goes into the terminal's result
         * slot and the remainders into its grid, both of which the caller owns and does after this returns -
         * a store that wrote into the grid would be reaching into a container it was only asked to supply.
         *
         * <p>An extraction that comes up short is not rolled back. It means the network changed underneath
         * the reservation - something else took the items between the check and the commit - and the honest
         * outcome is that the craft loses those items rather than silently duplicating what it could not
         * pay for.
         */
        @Override
        public void commit(ItemStack output, List<ItemStack> remainders, AspectList crystals) {
            if (committed) {
                return;
            }
            committed = true;
            for (ItemStack ingredient : claimed) {
                AEItemKey key = AEItemKey.of(ingredient);
                if (key == null) {
                    continue;
                }
                StorageHelper.poweredExtraction(
                        energy, storage, key, ingredient.getCount(), source);
            }
        }

        @Override
        public void close() {
            // Nothing was taken on reserve - see the class note.
        }
    }
}
