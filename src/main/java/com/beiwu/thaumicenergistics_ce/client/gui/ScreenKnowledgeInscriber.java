package com.beiwu.thaumicenergistics_ce.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import com.beiwu.thaumicenergistics_ce.menu.MenuKnowledgeInscriber;
import com.beiwu.thaumicenergistics_ce.network.InscriberCostPayload;
import com.beiwu.thaumicenergistics_ce.recipe.arcane.EssentiaCrystals;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.resources.ResourceKey;

/** 知识铭刻机的界面：面板整块 blit，状态放在按钮标签上。 */
public class ScreenKnowledgeInscriber extends AbstractContainerScreen<MenuKnowledgeInscriber> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/knowledge_inscriber.png");

    private static final int WIDTH = 175;
    private static final int HEIGHT = 244;

    private static final int PANEL_WIDTH = 176;

    private static final int TITLE_X = 6;
    private static final int TITLE_Y = 5;
    private static final int INVENTORY_LABEL_X = 8;
    private static final int INVENTORY_LABEL_Y = 150;

    private static final int BUTTON_X = 137;
    private static final int BUTTON_Y = 101;

    private static final int CLEAR_X = 71;
    private static final int CLEAR_Y = 89;

    private static final int PLAYER_SLOTS = 36;

    private InscriberButton saveButton;

    private InscriberClearButton clearButton;

    private InscriberCostPayload recipeCost;

    public void acceptCost(InscriberCostPayload payload) {
        if (payload.containerId() == menu.containerId) {
            recipeCost = payload;
        }
    }

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
                        // 走菜单按钮点击，不是自定义数据包，id 和点击都搭原版的。
                        // 删除从菜单读，不从保存后清空的选择状态读。
                        minecraft.gameMode.handleInventoryButtonClick(
                                menu.containerId, menu.isDelete() ? 1 : 0);
                    }
                }));

        clearButton = addRenderableWidget(new InscriberClearButton(
                leftPos + CLEAR_X,
                topPos + CLEAR_Y,
                label("clear"),
                button -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 2);
                    }
                }));
        updateSaveButton();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        // 网格是幽灵网格，它和答案都是本侧的；服务端重做一遍。
        menu.updatePreview();
        // 样板从核心读，各井是只读槽，故本侧填它们。
        menu.refreshMirrors();
        updateSaveButton();
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos, topPos, 0);
        drawRecipeCost(graphics);
        graphics.pose().popPose();
        renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * 悬停框换成 AE2 的配色，见 {@link Ae2SlotHighlight}。
     * 不调 super：基类那版先贴一层半透明白方块，框底会发白。
     */
    private void drawRecipeCost(GuiGraphics graphics) {
        if (recipeCost == null || !recipeCost.hasRecipe() || minecraft == null || minecraft.level == null) {
            return;
        }
        String vis = recipeCost.vis() + " Vis";
        float scale = Math.min(0.75F, 24.0F / Math.max(1, font.width(vis)));
        graphics.pose().pushPose();
        graphics.pose().translate(113, 94,0);
        graphics.pose().scale(scale, scale,1);
        graphics.drawString(font, vis, -font.width(vis) / 2, 0, 0xFF60477F, false);
        graphics.pose().popPose();

        int count = recipeCost.crystals().size();
        if (count == 0) return;
        float chip = Math.min(10.0F, 50.0F / count);
        float x = 124.0F - chip;
        for (var crystal : recipeCost.crystals()) {
            var aspect = Aspects.resolve(minecraft.level,
                    ResourceKey.create(IAspect.REGISTRY_KEY, crystal.aspect()));
            if (aspect != null) {
                graphics.pose().pushPose();
                graphics.pose().translate(x, 132,0);
                graphics.pose().scale(chip / 16.0F, chip / 16.0F,1);
                graphics.renderItem(EssentiaCrystals.create(aspect, 1), 0, 0);
                String amount = Integer.toString(crystal.count());
                graphics.drawString(font, amount, 17 - font.width(amount), 10, 0xFFFFFFFF, true);
                graphics.pose().popPose();
            }
            x -= chip;
        }
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
        if (clearButton != null) {
            clearButton.active = !menu.isGridEmpty();
        }
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
