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
import thaumicenergistics_ce.util.ThELog;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 让玩家把 JEI 里的物品拖进蒸馏编码台的源格。
 * 源格只指定要蒸馏哪个物品，拖来的物品堆不会被取走，与 [TemplateSlot] 一样。
 * 空白格不同：要从物品栏取走一个真实样板，落在那里就被消耗掉。
 */
public class DistillationEncoderGhostIngredientHandler
        implements IGhostIngredientHandler<ScreenDistillationEncoder> {

    private static final int SLOT_SIZE = 16;

    /** 记录 JEI 向本处理器问过什么，默认关闭；环境变量按本 mod 其他诊断的命名。
     * “拖拽没反应”和“JEI 从未询问”现象相同、原因完全不同，值得开开关跑一次。 */
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
        // 空白格只在它为空且是空白样板时算数：下一次编码会消耗它。
        if (ingredient.getIngredient() instanceof ItemStack stack
                && AEItems.BLANK_PATTERN.is(stack)
                && menu.slots.get(MenuDistillationEncoder.MENU_BLANK).getItem().isEmpty()) {
            targets.add(new BlankTarget<>(menu, screen.getGuiLeft(), screen.getGuiTop()));
        }
        // 只在真开始拖拽时记录：悬停路径每帧都调用它，每帧一行会埋掉有用的那行。
        if (TRACE && doStart) {
            ThELog.LOG.info(
                    "[encoder] JEI is starting a drag; offering one target at {}", targets.get(0).getArea());
        }
        return targets;
    }

    @Override
    public void onComplete() {
        // 没有要释放的东西：井收下的是一条指令，不是一个物品。
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
                // 这是一次移动，不是幽灵写入：样板要离开玩家的物品栏，只有服务端能做这件事，
                // 所以直接发给服务端，本地不先显示。
                ClientPacketDistributor.sendToServer(new EncoderActionPayload(
                        menu.containerId, EncoderActionPayload.ACTION_INSERT_BLANK, 0));
                if (TRACE) {
                    ThELog.LOG.info("[encoder] JEI dropped a blank pattern on the blank well");
                }
            }
        }
    }

    /** 源井作为拖放目标：它收下的是一条指令，不是那个物品。 */
    private record SourceTarget<I>(MenuDistillationEncoder menu, int guiLeft, int guiTop) implements Target<I> {

        /**
         * 给 JEI 的目标矩形，单位屏幕像素。
         * 槽位 x/y 相对 GUI 角点，JEI 填矩形时不做平移，要自己加 GUI 偏移。
         */
        @Override
        public Rect2i getArea() {
            var slot = menu.slots.get(MenuDistillationEncoder.MENU_SOURCE);
            return new Rect2i(guiLeft + slot.x, guiTop + slot.y, SLOT_SIZE, SLOT_SIZE);
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
                // 该格属于机器，要通知服务端。
                // 本地先写，格和要素行随即在光标下填充，不用等一个来回。
                if (TRACE) {
                    ThELog.LOG.info(
                            "[encoder] JEI dropped {} into the source well", stack.getHoverName().getString());
                }
                menu.requestSourceTemplate(stack.copyWithCount(1));
            }
        }
    }
}
