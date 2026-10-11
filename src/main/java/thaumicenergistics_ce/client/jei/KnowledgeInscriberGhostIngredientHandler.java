package thaumicenergistics_ce.client.jei;

import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.client.gui.ScreenKnowledgeInscriber;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;

public class KnowledgeInscriberGhostIngredientHandler
        implements IGhostIngredientHandler<ScreenKnowledgeInscriber> {

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
    }

    private record GridTarget<I>(MenuKnowledgeInscriber menu, int cell, int guiLeft, int guiTop)
            implements Target<I> {

        @Override
        public Rect2i getArea() {
            var slot = menu.slots.get(MenuKnowledgeInscriber.gridSlotIndex(cell));
            return new Rect2i(guiLeft + slot.x, guiTop + slot.y, SLOT_SIZE, SLOT_SIZE);
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
                menu.slots.get(MenuKnowledgeInscriber.gridSlotIndex(cell))
                        .set(stack.copyWithCount(1));
            }
        }
    }
}
