package thaumicenergistics_ce.integration.jei;

import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.blockentity.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.client.ScreenKnowledgeInscriber;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;

/**
 * Lets the player drag an item from JEI straight into the Knowledge Inscriber's grid.
 *
 * <p>The grid is the machine's input, so this is the shortest path to using it: drag the recipe's
 * ingredients in one at a time, or press JEI's transfer button to have them all placed at once.
 *
 * <p>There is no bookkeeping in {@link #onComplete()} because there is none to do. The grid is a ghost
 * grid, so JEI is not being asked to hand anything over - the cells note what the player has, and the
 * real ingredients are paid for by the crafting job.
 */
public class KnowledgeInscriberGhostIngredientHandler
        implements IGhostIngredientHandler<ScreenKnowledgeInscriber> {

    /**
     * Drop area, in GUI pixels.
     *
     * <p>A well's interior is 16 wide and 15 tall, so a 16-square sits on it; the last row of the well is
     * its own wall and is not part of the hole.
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

    /** One grid cell, as a drop target. */
    private record GridTarget<I>(MenuKnowledgeInscriber menu, int cell, int guiLeft, int guiTop)
            implements Target<I> {

        /**
         * Where JEI should draw this target, in <em>screen</em> pixels.
         *
         * <p>Not the slot's own x and y. JEI fills this rectangle through {@code guiGraphics.fill} with
         * no translation - it is treated as an absolute screen position - while a slot's x and y are
         * relative to the GUI's top-left. Returning the raw slot coordinates therefore drew every drop
         * target a whole GUI up and to the left of the well it belonged to; the GUI's own offset is what
         * was missing.
         */
        @Override
        public Rect2i getArea() {
            var slot = menu.slots.get(MenuKnowledgeInscriber.gridSlotIndex(cell));
            return new Rect2i(guiLeft + slot.x, guiTop + slot.y, SLOT_SIZE, SLOT_SIZE);
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
                // The slot, and not the container: writing the container only reaches the client's
                // scratch copy of the grid. GhostGridSlot.set sends the cell to the server.
                menu.slots.get(MenuKnowledgeInscriber.gridSlotIndex(cell))
                        .set(stack.copyWithCount(1));
            }
        }
    }
}
