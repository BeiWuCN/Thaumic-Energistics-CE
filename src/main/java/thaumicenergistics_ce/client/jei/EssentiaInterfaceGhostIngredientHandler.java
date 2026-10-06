package thaumicenergistics_ce.client.jei;

import appeng.client.gui.implementations.InterfaceScreen;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.InterfaceMenu;
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
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.network.EssentiaInterfaceMarkPayload;
import thaumicenergistics_ce.util.ThELog;

/**
 * Lets the player drag an aspect onto the config row of an ME interface that carries our access
 * card. Both host forms share AE2's one interface screen, so one registration serves the block and
 * the part. Without the card there is not one target: a drag shows no drop point, not a swallowing
 * slot, and the screen is taken raw as InterfaceScreen, since JEI pairs a Class with a handler of
 * that same type.
 */
public class EssentiaInterfaceGhostIngredientHandler implements IGhostIngredientHandler<InterfaceScreen> {

    private static final int SLOT_PIXELS = 16;

    @Override
    public <I> List<Target<I>> getTargetsTyped(
            InterfaceScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        // Aspects only: this card moves essentia, so a dragged item has nowhere to go.
        if (!(ingredient.getIngredient() instanceof AspectInstance)) {
            return targets;
        }
        if (!(screen.getMenu() instanceof InterfaceMenu menu)
                || !menu.getUpgrades().isInstalled(ModItems.ESSENTIA_ACCESS_CARD.get())) {
            return targets;
        }
        addRow(targets, menu, screen);
        return targets;
    }

    @Override
    public void onComplete() {
        // Nothing to release: an interface slot never took an item from the player.
    }

    /** Offers every live slot of the config row. */
    private static <I> void addRow(List<Target<I>> targets, InterfaceMenu menu, InterfaceScreen screen) {
        for (Slot slot : menu.getSlots(SlotSemantics.CONFIG)) {
            MarkTarget<I> target = MarkTarget.of(menu, screen, slot);
            if (target != null) {
                targets.add(target);
            }
        }
    }

    /** One drop point, sent to the server: the client never writes the interface's own config row. */
    private record MarkTarget<I>(int index, int x, int y, int containerId) implements Target<I> {

        /**
         * A target for one config slot, or {@code null}: it must be a live {@link AppEngSlot} over a
         * {@link ConfigMenuInventory}, since a locked row sits off-panel and the index is not an offset.
         */
        static <I> MarkTarget<I> of(InterfaceMenu menu, InterfaceScreen screen, Slot slot) {
            if (!(slot instanceof AppEngSlot appEngSlot)
                    || !(appEngSlot.getInventory() instanceof ConfigMenuInventory)
                    || !appEngSlot.isActive()) {
                return null;
            }
            int index = menu.getSlots(SlotSemantics.CONFIG).indexOf(slot);
            if (index < 0) {
                return null;
            }
            return new MarkTarget<>(index, screen.getGuiLeft() + slot.x, screen.getGuiTop() + slot.y,
                    menu.containerId);
        }

        /**
         * Where JEI draws this target, in screen pixels: a slot's x and y are relative to the GUI
         * corner, and JEI fills the rectangle with no translation of its own.
         */
        @Override
        public Rect2i getArea() {
            return new Rect2i(x, y, SLOT_PIXELS, SLOT_PIXELS);
        }

        @Override
        public void accept(I ingredient) {
            if (!(ingredient instanceof AspectInstance aspect)) {
                ThELog.LOG.warn(
                        "[essentia-interface] drag produced {} which is not an AspectInstance",
                        ingredient == null ? "null" : ingredient.getClass().getName());
                return;
            }
            ResourceLocation id = aspect.aspect().unwrapKey().map(key -> key.location()).orElse(null);
            if (id == null) {
                // An aspect with no id is not one the server could look up either.
                ThELog.LOG.warn("[essentia-interface] drag produced an aspect with no registry id");
                return;
            }
            ThELog.LOG.info("[essentia-interface] sending config slot {} <- {}", index, id);
            PacketDistributor.sendToServer(new EssentiaInterfaceMarkPayload(containerId, index, id));
        }
    }
}
