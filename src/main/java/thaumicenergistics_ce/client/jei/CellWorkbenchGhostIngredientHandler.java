package thaumicenergistics_ce.client.jei;

import appeng.menu.slot.AppEngSlot;
import appeng.util.ConfigMenuInventory;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics_ce.client.gui.ScreenEssentiaCellWorkbench;
import thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench;
import thaumicenergistics_ce.network.PartitionWellPayload;

/**
 * Lets the player drag an aspect from JEI into an Essentia Cell Workbench partition well.
 * Dragging is what a player expects from every other filter grid in AE2, and these wells are the
 * same kind: only aspects are offered a target, because the wells hold keys and an item has nowhere
 * to go. The mark is sent to the server, not written into the slot, in PartitionWellPayload.
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
         * Builds a target for a partition well, or {@code null} for anything else: AE2's config inventory
         * backs a well, and a cell in the workbench is what makes one writable.
         */
        static <I> WellTarget<I> of(MenuEssentiaCellWorkbench menu, int well, int guiLeft, int guiTop) {
            Slot slot = menu.slots.get(menu.partitionSlotIndex(well));
            if (slot instanceof AppEngSlot appEngSlot
                    && menu.isPartitionSlotEnabled(well)
                    && appEngSlot.getInventory() instanceof ConfigMenuInventory) {
                return new WellTarget<>(menu, well, guiLeft, guiTop);
            }
            return null;
        }

        /**
         * Where JEI draws this target, in screen pixels: the GUI's offset is added because JEI fills
         * this rectangle with no translation of its own, while a slot's x and y are relative to the corner.
         */
        @Override
        public Rect2i getArea() {
            Slot slot = menu.slots.get(menu.partitionSlotIndex(well));
            return new Rect2i(guiLeft + slot.x, guiTop + slot.y, 16, 16);
        }

        @Override
        public void accept(I ingredient) {
            if (!(ingredient instanceof AspectInstance aspect)) {
                return;
            }
            ResourceLocation id = aspect.aspect().unwrapKey().map(key -> key.location()).orElse(null);
            if (id == null) {
                // Not registry-backed: there is no id to send, and the server could not store a mark it
                // cannot name.
                return;
            }
            // To the server, because this is the only write that leaves this screen: the well itself would
            // keep the mark until the server answered with its own, empty partition.
            PacketDistributor.sendToServer(new PartitionWellPayload(menu.containerId, well, id));
        }
    }
}
