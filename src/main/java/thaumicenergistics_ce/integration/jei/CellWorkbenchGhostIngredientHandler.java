package thaumicenergistics_ce.integration.jei;

import appeng.api.stacks.GenericStack;
import appeng.menu.slot.AppEngSlot;
import appeng.util.ConfigMenuInventory;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.client.gui.ScreenEssentiaCellWorkbench;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench;

/**
 * Lets the player drag an aspect from JEI into an Essentia Cell Workbench partition well.
 * <ul>
 *   <li>Dragging is what a player expects from every other filter grid in AE2; these are the same kind.
 *   <li>Only aspects are offered a target: the wells hold keys, so an item has nowhere to go.
 *   <li>Writing the inventory directly changes only this side: a fake slot's {@code set} sends the packet.
 * </ul>
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

    private record WellTarget<I>(MenuEssentiaCellWorkbench menu, int well, int guiLeft, int guiTop)
            implements Target<I> {

        /**
         * Builds a target for a partition well, or {@code null} if that menu index is not a well: a config
         * slot is the only kind backed by AE2's {@link ConfigMenuInventory}, so a bad index yields nothing.
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
         * Where JEI draws this target, in <em>screen</em> pixels: the GUI's offset is added because JEI fills
         * this rectangle with no translation of its own, while a slot's x and y are relative to the corner.
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
            // One, because a partition entry is a type rather than an amount - how much the cell
            // holds is decided by its size, not by the partition.
            ItemStack wrapped = GenericStack.wrapInItemStack(key, 1);
            menu.slots.get(MenuEssentiaCellWorkbench.partitionSlotIndex(well)).set(wrapped);
        }
    }
}
