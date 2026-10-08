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

/**
 * 让玩家把 JEI 里的物品直接拖进知识铭刻机的网格。
 * 该网格就是机器的输入，这是使用它的最短路径。
 * {@link #onComplete()} 里是空的，网格是幽灵网格，JEI 不会交出东西：
 * 格中只记录玩家拥有什么，付出真实原料的是合成任务。
 */
public class KnowledgeInscriberGhostIngredientHandler
        implements IGhostIngredientHandler<ScreenKnowledgeInscriber> {

    /**
     * 放置区域，单位 GUI 像素：一格内部宽 16、高 15，16 见方的方块正好压在上面；
     * 它的最后一行是壁，不是孔。
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
        // 没有要释放的东西：网格里的格子从不接收物品。
    }

    private record GridTarget<I>(MenuKnowledgeInscriber menu, int cell, int guiLeft, int guiTop)
            implements Target<I> {

        /**
         * JEI 画该目标的位置，单位屏幕像素。不是槽位的 x/y：
         * JEI 填矩形时不做平移，槽位 x/y 相对 GUI 左上角。
         */
        @Override
        public Rect2i getArea() {
            var slot = menu.slots.get(MenuKnowledgeInscriber.gridSlotIndex(cell));
            return new Rect2i(guiLeft + slot.x, guiTop + slot.y, SLOT_SIZE, SLOT_SIZE);
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
                // 用槽位不用容器：容器只到得了客户端的暂存副本。
                // [GhostGridSlot.set] 把该格发给服务端。
                menu.slots.get(MenuKnowledgeInscriber.gridSlotIndex(cell))
                        .set(stack.copyWithCount(1));
            }
        }
    }
}
