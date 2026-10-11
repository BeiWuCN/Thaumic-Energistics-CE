package thaumicenergistics_ce.client.jei;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.UpgradeableMenu;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import thaumicenergistics_ce.network.EssentiaBusConfigPayload;

public final class EssentiaBusGhostIngredientHandler<T extends UpgradeableScreen<? extends UpgradeableMenu<?>>>
        implements IGhostIngredientHandler<T> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(T screen, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        var menu = screen.getMenu();
        if (!(ingredient.getIngredient() instanceof AspectInstance) || !EssentiaBusConfigPayload.supports(menu)) {
            return targets;
        }
        var slots = menu.getSlots(SlotSemantics.CONFIG);
        for (int index = 0; index < slots.size(); index++) {
            var slot = slots.get(index);
            if (slot.isActive()) {
                targets.add(new MarkTarget<>(menu.containerId, index,
                        new Rect2i(screen.getGuiLeft() + slot.x, screen.getGuiTop() + slot.y, 16, 16)));
            }
        }
        return targets;
    }

    @Override
    public void onComplete() {}

    private record MarkTarget<I>(int containerId, int index, Rect2i area) implements Target<I> {
        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof AspectInstance aspect) {
                var id = aspect.aspect().unwrapKey().map(key -> key.identifier()).orElse(null);
                if (id != null) {
                    ClientPacketDistributor.sendToServer(new EssentiaBusConfigPayload(containerId, index, id));
                }
            }
        }
    }
}
