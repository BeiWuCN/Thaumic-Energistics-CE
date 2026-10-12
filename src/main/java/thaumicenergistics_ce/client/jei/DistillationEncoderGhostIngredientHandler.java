package thaumicenergistics_ce.client.jei;

import appeng.core.definitions.AEItems;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.client.gui.ScreenDistillationEncoder;
import thaumicenergistics_ce.menu.MenuDistillationEncoder;
import thaumicenergistics_ce.network.EncoderActionPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class DistillationEncoderGhostIngredientHandler
        implements IGhostIngredientHandler<ScreenDistillationEncoder> {

    private static final int SLOT_SIZE = 16;

    @Override
    public <I> List<Target<I>> getTargetsTyped(
            ScreenDistillationEncoder screen, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        if (!(ingredient.getIngredient() instanceof ItemStack)) {
            return targets;
        }
        MenuDistillationEncoder menu = screen.getMenu();
        targets.add(new SourceTarget<>(menu, screen.getGuiLeft(), screen.getGuiTop()));
        if (ingredient.getIngredient() instanceof ItemStack stack
                && AEItems.BLANK_PATTERN.is(stack)
                && menu.slots.get(MenuDistillationEncoder.MENU_BLANK).getItem().isEmpty()) {
            targets.add(new BlankTarget<>(menu, screen.getGuiLeft(), screen.getGuiTop()));
        }

        return targets;
    }

    @Override
    public void onComplete() {
    }

    private record BlankTarget<I>(MenuDistillationEncoder menu, int guiLeft, int guiTop) implements Target<I> {
        @Override
        public Rect2i getArea() {
            var slot = menu.slots.get(MenuDistillationEncoder.MENU_BLANK);
            return new Rect2i(guiLeft + slot.x, guiTop + slot.y, SLOT_SIZE, SLOT_SIZE);
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack stack && AEItems.BLANK_PATTERN.is(stack)) {
                ClientPacketDistributor.sendToServer(new EncoderActionPayload(
                        menu.containerId, EncoderActionPayload.ACTION_INSERT_BLANK, 0));

            }
        }
    }

    private record SourceTarget<I>(MenuDistillationEncoder menu, int guiLeft, int guiTop) implements Target<I> {

        @Override
        public Rect2i getArea() {
            var slot = menu.slots.get(MenuDistillationEncoder.MENU_SOURCE);
            return new Rect2i(guiLeft + slot.x, guiTop + slot.y, SLOT_SIZE, SLOT_SIZE);
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {

                menu.requestSourceTemplate(stack.copyWithCount(1));
            }
        }
    }
}
