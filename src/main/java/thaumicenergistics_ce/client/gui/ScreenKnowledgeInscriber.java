package thaumicenergistics_ce.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;

/** 知识铭刻机的界面：面板整块 blit，状态放在按钮标签上。 */
public class ScreenKnowledgeInscriber extends AbstractContainerScreen<MenuKnowledgeInscriber> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/knowledge_inscriber.png");

    private static final int WIDTH = 175;
    private static final int HEIGHT = 244;

    /** 美术宽 208；窗口只显示它中的面板部分。 */
    private static final int PANEL_WIDTH = 208;

    private static final int TITLE_X = 6;
    private static final int TITLE_Y = 5;
    private static final int INVENTORY_LABEL_X = 8;
    private static final int INVENTORY_LABEL_Y = 150;

    private static final int BUTTON_X = 109;
    private static final int BUTTON_Y = 90;

    private static final int PLAYER_SLOTS = 36;

    private InscriberButton saveButton;

    public ScreenKnowledgeInscriber(MenuKnowledgeInscriber menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.titleLabelX = TITLE_X;
        this.titleLabelY = TITLE_Y;
        this.inventoryLabelX = INVENTORY_LABEL_X;
        this.inventoryLabelY = INVENTORY_LABEL_Y;
    }

    @Override
    protected void init() {
        super.init();
        saveButton = addRenderableWidget(new InscriberButton(
                leftPos + BUTTON_X,
                topPos + BUTTON_Y,
                buttonLabel(),
                button -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        // 走菜单按钮点击，不是自定义数据包，id 和点击都搭原版的。
                        // 删除从菜单读，不从保存后清空的选择状态读。
                        minecraft.gameMode.handleInventoryButtonClick(
                                menu.containerId, menu.isDelete() ? 1 : 0);
                    }
                }));
        updateSaveButton();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // 网格是幽灵网格，它和答案都是本侧的；服务端重做一遍。
        menu.updatePreview();
        // 样板从核心读，各井是只读槽，故本侧填它们。
        menu.refreshMirrors();
        updateSaveButton();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * 悬停框画成 AE2 的样子：一像素浅青细框加半透明蓝底。
     * 26.1.2 的原版高亮是私有的白方块 sprite，覆写不了也拦不住，
     * 只能在它之后照 AE2 的画法再补一遍，见 {@link Ae2SlotHighlight}。
     */
    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractContents(graphics, mouseX, mouseY, partialTick);
        Slot hovered = this.hoveredSlot;
        if (hovered == null) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(leftPos, topPos);
        Ae2SlotHighlight.render(graphics, hovered);
        graphics.pose().popMatrix();
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
                RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, PANEL_WIDTH, HEIGHT, 256, 256);
    }


    private void updateSaveButton() {
        if (saveButton == null) {
            return;
        }
        Component next = buttonLabel();
        saveButton.setMessage(next);
        saveButton.active = menu.isActionable();
    }

    /**
     * 按钮带的标签，按机器状态细分。
     * 「No Core」和「Invalid」得分开：并成一个，插核心后仍显示「No Core」。
     */
    private Component buttonLabel() {
        if (!menu.hasCore()) {
            return label("no_core");
        }
        // 保存和删除问的是同一个网格的同一个问题，读取方式按点击将要执行的动作。
        if (menu.isDelete()) {
            return label("delete");
        }
        String key = switch (menu.buttonState()) {
            case BlockEntityKnowledgeInscriber.STATUS_ACTIONABLE -> "save";
            case BlockEntityKnowledgeInscriber.STATUS_CORE_FULL -> "full";
            case BlockEntityKnowledgeInscriber.STATUS_RESEARCH_LOCKED -> "locked";
            default -> "invalid";
        };
        return label(key);
    }

    private static Component label(String key) {
        return Component.translatable("thaumicenergistics_ce.gui.knowledge_inscriber." + key);
    }
}
