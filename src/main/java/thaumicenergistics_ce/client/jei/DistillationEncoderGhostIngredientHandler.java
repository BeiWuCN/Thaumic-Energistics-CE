package thaumicenergistics_ce.client.jei;

import appeng.core.definitions.AEItems;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics_ce.client.gui.ScreenDistillationEncoder;
import thaumicenergistics_ce.menu.MenuDistillationEncoder;
import thaumicenergistics_ce.network.EncoderActionPayload;
import thaumicenergistics_ce.util.ThELog;

/**
 * Lets the player drag an item from JEI into the Distillation Encoder's source well.
 * <ul>
 *   <li>The well names the item to distil; the dragged stack is <em>not</em> taken, per {@code TemplateSlot}.
 *   <li><b>The two wells are not the same kind of drop:</b> the source well takes an instruction, while
 *       the blank well takes a real pattern out of the inventory, because what lands there is spent.
 * </ul>
 */
public class DistillationEncoderGhostIngredientHandler
        implements IGhostIngredientHandler<ScreenDistillationEncoder> {

    private static final int SLOT_SIZE = 16;

    /** Whether to log what JEI asks this handler for; off unless named like this mod's other
     * diagnostics. "The drag does nothing" and "JEI never asked" look identical at the screen
     * but have nothing in common as bugs, so the run that settles it is worth a switch. */
    static final boolean TRACE = System.getenv("THAUMICENERGISTICS_ENCODER_TRACE") != null;

    @Override
    public <I> List<Target<I>> getTargetsTyped(
            ScreenDistillationEncoder screen, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        if (!(ingredient.getIngredient() instanceof ItemStack)) {
            return targets;
        }
        MenuDistillationEncoder menu = screen.getMenu();
        targets.add(new SourceTarget<>(menu, screen.getGuiLeft(), screen.getGuiTop()));
        // The blank well too, but only while it is empty and only for a blank pattern: it is a
        // real slot the next encode spends, so nothing else belongs there.
        if (ingredient.getIngredient() instanceof ItemStack stack
                && AEItems.BLANK_PATTERN.is(stack)
                && menu.slots.get(MenuDistillationEncoder.MENU_BLANK).getItem().isEmpty()) {
            targets.add(new BlankTarget<>(menu, screen.getGuiLeft(), screen.getGuiTop()));
        }
        // Logged only when JEI is really beginning a drag: the hover path calls this every frame the
        // cursor spends over an ingredient, and a line each time would bury the one that matters.
        if (TRACE && doStart) {
            ThELog.LOG.info(
                    "[encoder] JEI is starting a drag; offering one target at {}", targets.get(0).getArea());
        }
        return targets;
    }

    @Override
    public void onComplete() {
        // Nothing to release: the well took an instruction, not an item.
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
                // A move, not a ghost write: the pattern has to leave the player's inventory, and only
                // the server may do that, so it goes to the server with nothing shown locally first.
                PacketDistributor.sendToServer(new EncoderActionPayload(
                        menu.containerId, EncoderActionPayload.ACTION_INSERT_BLANK, 0));
                if (TRACE) {
                    ThELog.LOG.info("[encoder] JEI dropped a blank pattern on the blank well");
                }
            }
        }
    }

    /** The source well, as a drop target: it takes an instruction, not the item. */
    private record SourceTarget<I>(MenuDistillationEncoder menu, int guiLeft, int guiTop) implements Target<I> {

        /**
         * Where JEI draws this target, in <em>screen</em> pixels: the GUI's offset is added because JEI fills
         * this rectangle with no translation of its own, while a slot's x and y are relative to the corner.
         */
        @Override
        public Rect2i getArea() {
            var slot = menu.slots.get(MenuDistillationEncoder.MENU_SOURCE);
            return new Rect2i(guiLeft + slot.x, guiTop + slot.y, SLOT_SIZE, SLOT_SIZE);
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
                // The well is the machine's, so the server is told - but the write happens here first,
                // so the well and the aspect row fill under the cursor instead of a round trip later.
                if (TRACE) {
                    ThELog.LOG.info(
                            "[encoder] JEI dropped {} into the source well", stack.getHoverName().getString());
                }
                menu.requestSourceTemplate(stack.copyWithCount(1));
            }
        }
    }
}
