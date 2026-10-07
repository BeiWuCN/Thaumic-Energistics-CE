package thaumicenergistics_ce.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;

/** 知识铭刻机的界面：一整块面板 blit 上去，状态显示在按钮标签上。 */
public class ScreenKnowledgeInscriber extends AbstractContainerScreen<MenuKnowledgeInscriber> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/knowledge_inscriber.png");

    private static final int WIDTH = 175;
    private static final int HEIGHT = 244;

    /** 美术图宽 208；窗口只显示其中的面板部分。 */
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
        saveButton = addRenderableWidget(new InscriberButton(
                leftPos + BUTTON_X,
                topPos + BUTTON_Y,
                buttonLabel(),
                button -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        // 走菜单按钮点击，而不是自定义数据包——id 和点击都沿用原版机制。
                        // 删除状态从菜单读取，而不是从保存后会清空的选择状态读取。
                        minecraft.gameMode.handleInventoryButtonClick(
                                menu.containerId, menu.isDelete() ? 1 : 0);
                    }
                }));
        updateSaveButton();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        // 该网格是幽灵网格，所以它和结果都在本侧；服务端会重做一遍。
        menu.updatePreview();
        // 样板从核心读出，而各格是只读槽位，所以由本侧填充它们。
        menu.refreshMirrors();
        updateSaveButton();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, PANEL_WIDTH, HEIGHT);
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
     * 按钮所带的标签，按机器状态细分。
     * “No Core”和“Invalid”保持分开：合并后，插入核心时仍显示“No Core”。
     */
    private Component buttonLabel() {
        if (!menu.hasCore()) {
            return label("no_core");
        }
        // 保存和删除问的是同一个网格的同一个问题，读取方式与点击时将要执行的动作一致。
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
