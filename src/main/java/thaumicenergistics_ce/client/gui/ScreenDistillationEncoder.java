package thaumicenergistics_ce.client.gui;

import com.leclowndu93150.thaumaturge.api.aspect.AspectComponents;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.menu.MenuDistillationEncoder;
import thaumicenergistics_ce.menu.slot.AspectSelectSlot;
import thaumicenergistics_ce.network.EncoderActionPayload;

/**
 * 蒸馏编码台的界面：整体 blit 本 mod 自己的美术图，槽位由菜单放在美术图画的坐标上。
 * 不走 AE2 的界面样式系统：它只解析 AE2 命名空间里的样式文档，附加模组的贴图引用不到。
 */
public class ScreenDistillationEncoder extends AbstractContainerScreen<MenuDistillationEncoder> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/distillation_encoder.png");

    private static final int WIDTH = 176;

    /** 234 行不是 229：美术图的不透明像素占 y=0..233，最后五行是底部斜面。 */
    private static final int HEIGHT = 234;

    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_X = 8;
    private static final int INVENTORY_LABEL_Y = HEIGHT - 94;

    /** 要素在格里的绘制大小，和 Thaumaturge 自己的 GUI 要素尺寸一致。 */
    private static final int ASPECT_SIZE = 16;

    /** Encode 按钮左上角，单位是面板像素：格子之间那条 34x14 的裸面板。 */
    private static final int BUTTON_X = 140;
    private static final int BUTTON_Y = 94;

    private EncodeButton encodeButton;

    public ScreenDistillationEncoder(
            MenuDistillationEncoder menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.titleLabelX = TITLE_X;
        this.titleLabelY = TITLE_Y;
        this.inventoryLabelX = INVENTORY_LABEL_X;
        this.inventoryLabelY = INVENTORY_LABEL_Y;
    }

    @Override
    protected void init() {
        super.init();
        encodeButton = addRenderableWidget(new EncodeButton(
                leftPos + BUTTON_X,
                topPos + BUTTON_Y,
                Component.translatable("thaumicenergistics_ce.gui.encode"),
                button -> menu.sendAction(EncoderActionPayload.ACTION_ENCODE, 0)));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (encodeButton != null) {
            encodeButton.active = menu.canEncode();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // 这一行来自源物品，客户端菜单从不知道它什么时候到。
        menu.ensureAspects();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        renderAspectTooltip(graphics, mouseX, mouseY);
    }

    /**
     * 面板贴图归 [extractBackground]，不归 [extractContents]：这一版把一帧拆成两半，
     * 槽里的物品、悬停的那个槽位和 tooltip 都在 [extractContents] 里，占住它又不调 super，
     * 物品和 tooltip 就一起没了。
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, WIDTH, HEIGHT, 256, 256);

        // 要素全未发现的物品行是空的，也没有解释，界面看着像坏了。
        // 这里把该行清空，不画淡，免得盖住这条提示。
        if (menu.sourceRevealsNothing()) {
            graphics.centeredText(
                    font,
                    Component.translatable("thaumicenergistics_ce.gui.distillation_encoder.not_scanned"),
                    leftPos + 73,
                    topPos + 78,
                    0xFFAAAAAA);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        List<Holder<IAspect>> aspects = menu.aspects();
        for (Slot slot : menu.slots) {
            if (slot instanceof AspectSelectSlot aspectSlot && aspectSlot.aspectIndex() < 0) {
                // 选中的格：槽位里什么都不放，这里存的是选择，不是物品堆。
                Holder<IAspect> picked = menu.pickedAspect();
                if (picked != null) {
                    // 故意不写数量：这一格就是选择，数量由源格显示。
                    AspectRendering.renderGui(graphics, font, slot.x, slot.y, picked, 0.0F);
                }
                continue;
            }
            if (slot instanceof AspectSelectSlot aspectSlot && aspectSlot.isFilled()) {
                int index = aspectSlot.aspectIndex();
                // 未发现的要素在行里占位但不画：画出图标就等于宣称玩家已经知道它。
                if (index < 0 || index >= aspects.size() || !menu.isAspectRevealed(index)) {
                    continue;
                }
                // 用槽位坐标，不用屏幕坐标：[renderLabels] 已在面板偏移的位姿里跑，
                // 再加一次 [leftPos] 会让每个图标的距离翻倍。
                int x = slot.x;
                int y = slot.y;
                // 数量交给渲染器画在格的角上：样板的产出数量。
                AspectRendering.renderGui(graphics, font, x, y, aspects.get(index), menu.aspectAmountFor(index));

                if (aspectSlot.isSelected()) {
                    // 选中的井画一圈框，往里缩一格正好压在边框上；直接画线，不用美术图。
                    graphics.outline(x - 1, y - 1, ASPECT_SIZE + 2, ASPECT_SIZE + 2, 0xFFFFD700);
                }
            }
        }
    }

    private void renderAspectTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (hoveredSlot instanceof AspectSelectSlot aspectSlot) {
            int index = aspectSlot.aspectIndex();
            List<Holder<IAspect>> aspects = menu.aspects();
            if (index >= 0 && index < aspects.size() && menu.isAspectRevealed(index)) {
                var name = AspectComponents.trueName(aspects.get(index));
                graphics.setTooltipForNextFrame(font, name, mouseX, mouseY);
            }
            return;
        }
        // 两个样板槽是普通物品槽，tooltip 走原版。
    }

}
