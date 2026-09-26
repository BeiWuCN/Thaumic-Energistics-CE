package thaumicenergistics_ce.integration.jei;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.menu.slot.AppEngSlot;
import appeng.util.ConfigMenuInventory;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.compat.jei.ingredient.AspectIngredientType;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.menu.MenuEssentiaBus;
import thaumicenergistics_ce.network.EssentiaBusConfigPayload;

/**
 * Lets the player drag an aspect from JEI into an essentia bus's config slots.
 *
 * <p>Essentia is already an ingredient JEI knows: Thaumaturge registers {@link AspectIngredientType}, so
 * aspects appear in JEI's list and can be dragged like anything else. Without a handler like this one a
 * drag has nowhere to land - JEI only drops into targets an addon declares, and does nothing at all when
 * none are registered.
 *
 * <p>Written against the buses' shared menu rather than one bus, so the import and export buses both get
 * it from one registration. Their config grids are the same grid addressed the same way; only what they
 * do with the entries differs.
 *
 * <p>The insertion goes through {@link Slot#set} with an {@link ItemStack}, which is not a workaround:
 * AE2's own way of putting a non-item key into a slot is to wrap it in an item stack carrying the key as
 * a data component, and the slot's inventory unwraps it on the way in. Writing the config inventory
 * directly instead would only change this side - a fake slot's {@code set} is what sends the action
 * packet the server applies, and without it the entry would appear and then vanish.
 */
public class EssentiaBusGhostIngredientHandler<T extends UpgradeableScreen<? extends MenuEssentiaBus<?>>>
        implements IGhostIngredientHandler<T> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(
            T screen,
            ITypedIngredient<I> ingredient,
            boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        // Only aspects. A bus moves essentia, so an item dragged from JEI has nowhere to go, and offering
        // it a target would invite the player to drop something that cannot be stored.
        if (!(ingredient.getIngredient() instanceof AspectInstance)) {
            return targets;
        }
        MenuEssentiaBus<?> menu = screen.getMenu();
        for (int slot = 0; slot < menu.getConfigSlotCount(); slot++) {
            Target<I> target = ConfigTarget.of(menu, slot, screen.getGuiLeft(), screen.getGuiTop());
            if (target != null) {
                targets.add(target);
            }
        }
        // Logged because "the highlight is a lie" is a failure mode with no other symptom: the client decides
        // which cells to offer from its own copy of the upgrade inventory, and the server decides whether to
        // accept from its copy. If those two ever disagree the player sees a green target that eats the drop.
        if (!targets.isEmpty()) {
            ThaumicEnergistics.LOG.info(
                    "[bus-config] offering {} target(s) of {} config slot(s); {} capacity card(s) installed",
                    targets.size(), menu.getConfigSlotCount(),
                    menu.getUpgrades().getInstalledUpgrades(appeng.core.definitions.AEItems.CAPACITY_CARD));
        }
        return targets;
    }

    @Override
    public void onComplete() {
        // Nothing to release: a config slot never took an item from the player.
    }

    /** One config slot, as a drop target. */
    private record ConfigTarget<I>(MenuEssentiaBus<?> menu, int index, int guiLeft, int guiTop)
            implements Target<I> {

        /**
         * Builds a target for a config slot, or returns {@code null} if that slot is not droppable.
         *
         * <p>Two guards, and the second one is the one that took two attempts.
         *
         * <p>The first is the bug this handler already shipped once: the menu index of a config slot used to
         * be a fixed offset past the player's inventory, which was wrong, because {@code UpgradeableMenu}
         * registers the config grid before the player's slots. Requiring the slot to be backed by AE2's
         * {@link ConfigMenuInventory} - the wrapper only a config slot has - means a wrong index produces no
         * target rather than a confidently misplaced one.
         *
         * <p>The second is the row lock. The config grid is 2 rows plus 5 more unlocked by capacity cards,
         * and the slots for the locked rows exist in the menu from the start: AE2 hides them by moving them
         * off the panel. Offering all 7 rows drew the drop highlight over rows the player cannot see, which
         * is the "five extra rows of green boxes" that was reported first.
         *
         * <p>Asking the slot rather than the menu, because the slot is what carries the answer: a locked row
         * is <b>inactive</b>, and {@code AppEngSlot.isActive()} is AE2's own accessor for that. Reaching for
         * {@code menu.isSlotEnabled} instead silenced the handler completely - the method is there and
         * public, and calling it from this side produced no targets at all, which is a worse state than the
         * one it was meant to fix.
         */
        static <I> ConfigTarget<I> of(MenuEssentiaBus<?> menu, int index, int guiLeft, int guiTop) {
            Slot slot = menu.slots.get(menu.configSlotIndex(index));
            if (slot instanceof AppEngSlot appEngSlot
                    && appEngSlot.getInventory() instanceof ConfigMenuInventory
                    && appEngSlot.isActive()) {
                return new ConfigTarget<>(menu, index, guiLeft, guiTop);
            }
            return null;
        }

        /**
         * Where JEI draws this target, in <em>screen</em> pixels.
         *
         * <p>The GUI's offset is added because JEI fills this rectangle with no translation of its own - it
         * is an absolute screen position, while a slot's x and y are relative to the GUI's corner. Without
         * it every drop target lands a whole GUI up and to the left of the slot it belongs to.
         */
        @Override
        public Rect2i getArea() {
            Slot slot = menu.slots.get(menu.configSlotIndex(index));
            return new Rect2i(guiLeft + slot.x, guiTop + slot.y, 16, 16);
        }

        @Override
        public void accept(I ingredient) {
            if (!(ingredient instanceof AspectInstance aspect)) {
                ThaumicEnergistics.LOG.warn(
                        "[bus-config] drag produced {} which is not an AspectInstance",
                        ingredient == null ? "null" : ingredient.getClass().getName());
                return;
            }
            // Sent to the server rather than written into the slot.
            //
            // The slot-write route is the usual one and it does not work here: an AE2 config slot takes an
            // item stack carrying the key as a component and unwraps it through AEItemKey, and an essentia
            // key is not an item, so the entry appeared and then vanished when the server answered. The
            // packet carries the aspect id and the server writes its own config inventory. See
            // EssentiaBusConfigPayload.
            ResourceLocation id = aspect.aspect().unwrapKey()
                    .map(key -> key.location())
                    .orElse(null);
            if (id == null) {
                // An aspect with no id is not one the server could look up either.
                ThaumicEnergistics.LOG.warn("[bus-config] drag produced an aspect with no registry id");
                return;
            }
            ThaumicEnergistics.LOG.info(
                    "[bus-config] sending slot {} <- {} for menu {}", index, id, menu.containerId);
            PacketDistributor.sendToServer(new EssentiaBusConfigPayload(menu.containerId, index, id));
        }
    }
}
