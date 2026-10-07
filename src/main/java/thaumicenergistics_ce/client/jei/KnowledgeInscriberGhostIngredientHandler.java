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
 * 该网格就是机器的输入，所以这是使用它的最短路径。
 * {@link #onComplete()} 里什么都没有，因为该网格是幽灵网格，JEI 不会交出任何东西：
 * 格中只记录玩家拥有什么，而合成任务才会付出真实原料。
 */
public class KnowledgeInscriberGhostIngredientHandler
        implements IGhostIngredientHandler<ScreenKnowledgeInscriber> {

    /**
     * 放置区域，单位为 GUI 像素：一格的内部宽 16、高 15，所以 16 见方的方块正好
     * 压在它上面——它的最后一行是壁，不是孔。
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
        // 没有要释放的东西：网格中的格子从不接收物品。
    }

    private record GridTarget<I>(MenuKnowledgeInscriber menu, int cell, int guiLeft, int guiTop)
            implements Target<I> {

        /**
         * JEI 应当绘制该目标的位置，单位为屏幕像素。不是槽位的 x/y：JEI 填充矩形时
         * 不做平移，而槽位的 x/y 是相对 GUI 左上角的。
         */
        @Override
        public Rect2i getArea() {
            var slot = menu.slots.get(MenuKnowledgeInscriber.gridSlotIndex(cell));
            return new Rect2i(guiLeft + slot.x, guiTop + slot.y, SLOT_SIZE, SLOT_SIZE);
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
                // 用槽位而不是容器：容器只到得了客户端的暂存副本。
                // [GhostGridSlot.set] 会把该格发给服务端。
                menu.slots.get(MenuKnowledgeInscriber.gridSlotIndex(cell))
                        .set(stack.copyWithCount(1));
            }
        }
    }
}
