package thaumicenergistics_ce.client.gui;

import com.leclowndu93150.thaumaturge.api.aspect.AspectComponents;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.menu.MenuDistillationEncoder;
import thaumicenergistics_ce.menu.slot.AspectSelectSlot;
import thaumicenergistics_ce.network.EncoderActionPayload;

/**
 * 蒸馏编码台的界面：整体 blit 本 mod 自己的美术图，槽位由菜单放在美术图所画的坐标上。
 * 之所以直接绘制而不用 AE2 的界面样式系统，是因为它只在 AE2 的命名空间内解析样式
 * 文档，附加模组的贴图没法被它引用。
 */
public class ScreenDistillationEncoder extends AbstractContainerScreen<MenuDistillationEncoder> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/distillation_encoder.png");

    private static final int WIDTH = 176;

    /** 234 行而不是 229：美术图的不透明像素占 y=0..233，最后五行是底部斜面。 */
    private static final int HEIGHT = 234;

    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_X = 8;
    private static final int INVENTORY_LABEL_Y = HEIGHT - 94;

    /** 要素在格中绘制的大小。与 Thaumaturge 自己的 GUI 要素尺寸一致。 */
    private static final int ASPECT_SIZE = 16;

    /** Encode 按钮的左上角，单位为面板像素：格与格之间那条 34x14 的裸面板。 */
    private static final int BUTTON_X = 140;
    private static final int BUTTON_Y = 94;

    private EncodeButton encodeButton;

    public ScreenDistillationEncoder(
            MenuDistillationEncoder menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 这一行来自源物品，而客户端菜单永远不会被告知它何时到达。
        menu.ensureAspects();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderAspectTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, WIDTH, HEIGHT);

        // 要素全未发现的物品，行是空的，也没有任何东西解释，界面看起来就像坏了。
        // 这里把该行清空而不是画淡，这样就没有东西盖住这条提示。
        if (menu.sourceRevealsNothing()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("thaumicenergistics_ce.gui.distillation_encoder.not_scanned"),
                    leftPos + 73,
                    topPos + 78,
                    0xFFAAAAAA);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);

        List<Holder<IAspect>> aspects = menu.aspects();
        for (Slot slot : menu.slots) {
            if (slot instanceof AspectSelectSlot aspectSlot && aspectSlot.aspectIndex() < 0) {
                // 选中的格：它的槽位什么都不放，因为这里放的是选择，不是物品堆。
                Holder<IAspect> picked = menu.pickedAspect();
                if (picked != null) {
                    // 故意不写数量：这一格本身就是选择，数量由源格显示。
                    AspectRendering.renderGui(graphics, font, slot.x, slot.y, picked, 0.0F);
                }
                continue;
            }
            if (slot instanceof AspectSelectSlot aspectSlot && aspectSlot.isFilled()) {
                int index = aspectSlot.aspectIndex();
                // 未发现的要素在行里保留位置，但不绘制——画出图标就等于宣称玩家拥有
                // 其并不具备的知识。
                if (index < 0 || index >= aspects.size() || !menu.isAspectRevealed(index)) {
                    continue;
                }
                // 用槽位坐标而不是屏幕坐标：[renderLabels] 已经在面板偏移的位姿内运行，
                // 再加一次 [leftPos] 会让每个图标的距离翻倍。
                int x = slot.x;
                int y = slot.y;
                // 数量交给渲染器，由它画在格的角上：即样板的产出数量。
                AspectRendering.renderGui(graphics, font, x, y, aspects.get(index), menu.aspectAmountFor(index));

                if (aspectSlot.isSelected()) {
                    // 选中的格四周加一圈边框，向内缩一格压在边界上；这是绘制出来的，不用美术图。
                    graphics.renderOutline(x - 1, y - 1, ASPECT_SIZE + 2, ASPECT_SIZE + 2, 0xFFFFD700);
                }
            }
        }
    }

    private void renderAspectTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot instanceof AspectSelectSlot aspectSlot) {
            int index = aspectSlot.aspectIndex();
            List<Holder<IAspect>> aspects = menu.aspects();
            if (index >= 0 && index < aspects.size() && menu.isAspectRevealed(index)) {
                var name = AspectComponents.trueName(aspects.get(index));
                graphics.renderTooltip(font, name, mouseX, mouseY);
            }
            return;
        }
        // 两个样板槽是普通物品槽，tooltip 由原版提供。
    }

}
