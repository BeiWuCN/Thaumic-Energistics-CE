package com.beiwu.thaumicenergistics_ce.client.jei;

import appeng.menu.SlotSemantics;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import com.beiwu.thaumicenergistics_ce.client.gui.ScreenEssentiaLevelEmitter;
import com.beiwu.thaumicenergistics_ce.menu.MenuEssentiaLevelEmitter;

/**
 * 让玩家把 JEI 里的要素拖进 ME源质发信器的配置槽。
 * 槽位本身收的是装满源质的容器物品，玩家手上拖的是要素配料，所以不走槽位点击，
 * 直接让菜单把要素写进配置槽。
 */
public class EssentiaLevelEmitterGhostIngredientHandler
        implements IGhostIngredientHandler<ScreenEssentiaLevelEmitter> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(
            ScreenEssentiaLevelEmitter screen, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        if (!(ingredient.getIngredient() instanceof AspectInstance)) {
            return targets;
        }
        MenuEssentiaLevelEmitter menu = screen.getMenu();
        for (Slot slot : menu.getSlots(SlotSemantics.CONFIG)) {
            if (slot.isActive()) {
                targets.add(new FilterTarget<>(menu,
                        new Rect2i(screen.getGuiLeft() + slot.x, screen.getGuiTop() + slot.y, 16, 16)));
            }
        }
        return targets;
    }

    @Override
    public void onComplete() {
        // 没有东西可释放：这个槽从不收玩家手上的物品。
    }

    private record FilterTarget<I>(MenuEssentiaLevelEmitter menu, Rect2i area) implements Target<I> {

        /** JEI 的矩形是屏幕像素：槽位的 x、y 相对角点，得加上 GUI 偏移。 */
        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof AspectInstance aspect) {
                aspect.aspect().unwrapKey()
                        .map(key -> key.location())
                        .ifPresent(menu::setFilter);
            }
        }
    }
}
