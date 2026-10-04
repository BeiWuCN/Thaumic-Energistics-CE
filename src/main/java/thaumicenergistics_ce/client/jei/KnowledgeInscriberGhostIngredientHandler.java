package thaumicenergistics_ce.client.jei;

import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.blockentity.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.client.gui.ScreenKnowledgeInscriber;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;

/**
 * Lets the player drag an item from JEI straight into the Knowledge Inscriber's grid.
 * <ul>
 * <li>The grid is the machine's input, so this is the shortest path to using it.</li>
 * <li>Nothing in {@link #onComplete()}: the grid is a ghost grid, so JEI hands nothing over - the cells
 * only note what the player has, and the crafting job pays for the real ingredients.</li>
 * </ul>
 */
public class KnowledgeInscriberGhostIngredientHandler
        implements IGhostIngredientHandler<ScreenKnowledgeInscriber> {

    /**
     * Drop area, in GUI pixels: a well's interior is 16 wide and 15 tall, so a 16-square sits on it -
     * its last row is wall, not hole.
     */
    private static final int SLOT_SIZE = 16;

    @Override
    public <I> List<Target<I>> getTargetsTyped(
            ScreenKnowledgeInscriber screen, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        if (!(ingredient.getIngredient() instanceof ItemStack)) {
            return targets;
        }
        MenuKnowledgeInscriber menu = screen.getMenu();
        for (int cell = 0; cell < BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT; cell++) {
            targets.add(new GridTarget<>(menu, cell, screen.getGuiLeft(), screen.getGuiTop()));
        }
        return targets;
    }

    @Override
    public void onComplete() {
        // Nothing to release: a grid cell never took an item.
    }

    private record GridTarget<I>(MenuKnowledgeInscriber menu, int cell, int guiLeft, int guiTop)
            implements Target<I> {

        /**
         * Where JEI should draw this target, in <em>screen</em> pixels. Not the slot's x/y: JEI fills the
         * rectangle with no translation, while slot x/y are relative to the GUI's top-left.
         */
        @Override
        public Rect2i getArea() {
            var slot = menu.slots.get(MenuKnowledgeInscriber.gridSlotIndex(cell));
            return new Rect2i(guiLeft + slot.x, guiTop + slot.y, SLOT_SIZE, SLOT_SIZE);
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
                // The slot, not the container: the container only reaches the client's scratch copy.
                // GhostGridSlot.set sends the cell to the server.
                menu.slots.get(MenuKnowledgeInscriber.gridSlotIndex(cell))
                        .set(stack.copyWithCount(1));
            }
        }
    }
}
