package thaumicenergistics_ce.integration.jei;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.core.definitions.AEItems;
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
 * <ul>
 *   <li>Thaumaturge registers {@link AspectIngredientType}; JEI drops only into declared targets.
 *   <li>Written against the buses' shared menu, so one registration serves both directions.
 *   <li>{@link Slot#set} with a stack carrying the key as a data component is AE2's non-item route.
 * </ul>
 */
public class EssentiaBusGhostIngredientHandler<T extends UpgradeableScreen<? extends MenuEssentiaBus<?>>>
        implements IGhostIngredientHandler<T> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(
            T screen,
            ITypedIngredient<I> ingredient,
            boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        // Aspects only: a bus moves essentia, so a dragged item has nowhere to go.
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
        // Client and server decide from their own upgrade inventory, and a disagreement silently
        // shows as a target that eats the drop.
        if (!targets.isEmpty()) {
            ThaumicEnergistics.LOG.info(
                    "[bus-config] offering {} target(s) of {} config slot(s); {} capacity card(s) installed",
                    targets.size(), menu.getConfigSlotCount(),
                    menu.getUpgrades().getInstalledUpgrades(AEItems.CAPACITY_CARD));
        }
        return targets;
    }

    @Override
    public void onComplete() {
        // Nothing to release: a config slot never took an item from the player.
    }

    private record ConfigTarget<I>(MenuEssentiaBus<?> menu, int index, int guiLeft, int guiTop)
            implements Target<I> {

        /**
         * A target for one config slot, or {@code null}: it must be a live {@link AppEngSlot} over a
         * {@link ConfigMenuInventory}, since locked rows sit off-panel and the index is not a fixed offset.
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
         * Where JEI draws this target, in <em>screen</em> pixels: a slot's x and y are relative to the GUI
         * corner, and JEI fills the rectangle with no translation of its own.
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
            // Sent to the server rather than written into the slot: AE2 config slots unwrap an item stack
            // through AEItemKey and an essentia key is not an item, so the entry would vanish.
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
