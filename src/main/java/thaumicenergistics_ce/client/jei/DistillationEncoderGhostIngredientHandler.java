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
 * 让玩家把 JEI 里的物品拖进蒸馏编码台的源格。
 * 该格只是指定要蒸馏哪个物品：拖来的物品堆不会被取走，与 [TemplateSlot] 一样。
 * 不过两个格并不是同一种放下——源格接受一条指示，而空白格要从物品栏里
 * 取走一个真实的样板，因为落在那里就被消耗掉了。
 */
public class DistillationEncoderGhostIngredientHandler
        implements IGhostIngredientHandler<ScreenDistillationEncoder> {

    private static final int SLOT_SIZE = 16;

    /** 是否记录 JEI 向本处理器询问过什么；除非像本 mod 其他诊断那样命名，
     * 否则关闭。“拖拽没反应”和“JEI 从未询问”在界面上一样，但作为 bug
     * 毫无共同点，值得用开关跑那一次定性测试。 */
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
        // 空白格也算，但只在它为空时、且只对空白样板：它是下一次编码会消耗掉的
        // 真实槽位，所以别的什么都不该放在那里。
        if (ingredient.getIngredient() instanceof ItemStack stack
                && AEItems.BLANK_PATTERN.is(stack)
                && menu.slots.get(MenuDistillationEncoder.MENU_BLANK).getItem().isEmpty()) {
            targets.add(new BlankTarget<>(menu, screen.getGuiLeft(), screen.getGuiTop()));
        }
        // 只有 JEI 真正开始拖拽时才记录：悬停路径在光标停留于某个原料上的每一帧
        // 都会调用它，每帧一行会把真正有用的那行埋掉。
        if (TRACE && doStart) {
            ThELog.LOG.info(
                    "[encoder] JEI is starting a drag; offering one target at {}", targets.get(0).getArea());
        }
        return targets;
    }

    @Override
    public void onComplete() {
        // 没有要释放的东西：该格接收的是指示，不是物品。
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
                // 这是一次移动，不是幽灵写入：样板必须离开玩家的物品栏，而这只有服务端
                // 能做，所以直接发给服务端，本地先不显示任何东西。
                PacketDistributor.sendToServer(new EncoderActionPayload(
                        menu.containerId, EncoderActionPayload.ACTION_INSERT_BLANK, 0));
                if (TRACE) {
                    ThELog.LOG.info("[encoder] JEI dropped a blank pattern on the blank well");
                }
            }
        }
    }

    /** 源格作为放置目标：它接收一条指示，而不是物品。 */
    private record SourceTarget<I>(MenuDistillationEncoder menu, int guiLeft, int guiTop) implements Target<I> {

        /**
         * JEI 绘制该目标的位置，单位为屏幕像素：要加上 GUI 的偏移，因为 JEI 填充
         * 这个矩形时自己不做任何平移，而槽位的 x 和 y 是相对 GUI 角点的。
         */
        @Override
        public Rect2i getArea() {
            var slot = menu.slots.get(MenuDistillationEncoder.MENU_SOURCE);
            return new Rect2i(guiLeft + slot.x, guiTop + slot.y, SLOT_SIZE, SLOT_SIZE);
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
                // 该格属于机器，所以要告诉服务端——但写入先在这里发生，
                // 这样格和要素行会在光标下立即填充，而不用等一个来回。
                if (TRACE) {
                    ThELog.LOG.info(
                            "[encoder] JEI dropped {} into the source well", stack.getHoverName().getString());
                }
                menu.requestSourceTemplate(stack.copyWithCount(1));
            }
        }
    }
}
