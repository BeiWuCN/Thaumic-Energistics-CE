package thaumicenergistics.integration.jei;

import appeng.api.stacks.GenericStack;
import appeng.menu.slot.AppEngSlot;
import appeng.util.ConfigMenuInventory;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.compat.jei.ingredient.AspectIngredientType;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics.client.ScreenEssentiaCellWorkbench;
import thaumicenergistics.integration.ae2.AEssentiaKey;
import thaumicenergistics.menu.MenuEssentiaCellWorkbench;

/**
 * Lets the player drag an aspect from JEI into an Essentia Cell Workbench partition well.
 *
 * <p>Partitioning a cell by hand means picking the same aspect out of a terminal once per well; dragging
 * it from JEI's list is the interaction a player already expects from every other filter grid in AE2, and
 * the wells are the same kind of grid - AE2's own fake slots, holding keys rather than items.
 *
 * <p>Only aspects are offered a target. A bus or a cell cannot hold an item, so an item dragged from JEI
 * has nowhere to go, and drawing drop targets for it would invite the player to drop something that cannot
 * be stored.
 *
 * <p>The placement goes through {@link Slot#set} with an {@link ItemStack}, which is AE2's own way of
 * putting a key into a fake slot: the key rides in the stack as a data component and the slot's inventory
 * unwraps it. Writing the partition inventory directly would change only this side - a fake slot's
 * {@code set} is what sends the action packet the server applies.
 */
public class CellWorkbenchGhostIngredientHandler
        implements IGhostIngredientHandler<ScreenEssentiaCellWorkbench> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(
            ScreenEssentiaCellWorkbench screen, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        if (!(ingredient.getIngredient() instanceof AspectInstance)) {
            return targets;
        }
        MenuEssentiaCellWorkbench menu = screen.getMenu();
        for (int well = 0; well < MenuEssentiaCellWorkbench.partitionSlotCount(); well++) {
            Target<I> target = WellTarget.of(menu, well, screen.getGuiLeft(), screen.getGuiTop());
            if (target != null) {
                targets.add(target);
            }
        }
        return targets;
    }

    @Override
    public void onComplete() {
        // Nothing to release: a partition well never took an item from the player.
    }

    /** One partition well, as a drop target. */
    private record WellTarget<I>(MenuEssentiaCellWorkbench menu, int well, int guiLeft, int guiTop)
            implements Target<I> {

        /**
         * Builds a target for a partition well, or returns {@code null} if that menu index is not one.
         *
         * <p>The index arithmetic here happens to be right - this menu is a plain
         * {@code AbstractContainerMenu} that adds the player's slots first - but "happens to be right" is
         * how the buses' grid was wrong, so the well is confirmed to be a well rather than trusted to be
         * one. A config slot is the only kind backed by AE2's {@link ConfigMenuInventory}, so requiring
         * that means a bad index yields no target instead of a target drawn in the wrong place.
         */
        static <I> WellTarget<I> of(MenuEssentiaCellWorkbench menu, int well, int guiLeft, int guiTop) {
            Slot slot = menu.slots.get(MenuEssentiaCellWorkbench.partitionSlotIndex(well));
            if (slot instanceof AppEngSlot appEngSlot
                    && appEngSlot.getInventory() instanceof ConfigMenuInventory) {
                return new WellTarget<>(menu, well, guiLeft, guiTop);
            }
            return null;
        }

        /**
         * Where JEI draws this target, in <em>screen</em> pixels.
         *
         * <p>The GUI's offset is added because JEI fills this rectangle with no translation of its own -
         * it is an absolute screen position, while a slot's x and y are relative to the GUI's corner.
         * Without it every drop target lands a whole GUI up and to the left of its well.
         */
        @Override
        public Rect2i getArea() {
            Slot slot = menu.slots.get(MenuEssentiaCellWorkbench.partitionSlotIndex(well));
            return new Rect2i(guiLeft + slot.x, guiTop + slot.y, 16, 16);
        }

        @Override
        public void accept(I ingredient) {
            if (!(ingredient instanceof AspectInstance aspect)) {
                return;
            }
            AEssentiaKey key = AEssentiaKey.of(aspect.aspect());
            if (key == null) {
                // Not registry-backed: there is no id to partition the cell to.
                return;
            }
            // One, because a partition entry is a type rather than an amount - how much the cell holds is
            // decided by its size, not by the partition.
            ItemStack wrapped = GenericStack.wrapInItemStack(key, 1);
            menu.slots.get(MenuEssentiaCellWorkbench.partitionSlotIndex(well)).set(wrapped);
        }
    }
}
