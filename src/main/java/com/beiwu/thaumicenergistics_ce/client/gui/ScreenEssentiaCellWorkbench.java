package com.beiwu.thaumicenergistics_ce.client.gui;

import appeng.api.config.ActionItems;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.util.Icon;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import com.beiwu.thaumicenergistics_ce.util.EssentiaContainers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import appeng.client.gui.widgets.ToggleButton;
import appeng.core.localization.GuiText;
import com.beiwu.thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench;
import com.beiwu.thaumicenergistics_ce.network.PartitionWellPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 源质元件工作台的界面。AE2 可升级界面，美术、槽位、标题和升级面板都来自样式；
 * 齿轮从元件填井，X 清空，点一个标记就把它取回。AE2 的模糊模式开关没留，
 * 源质 [AEKey] 没有耐久或 NBT 可匹配。
 */
public class ScreenEssentiaCellWorkbench extends UpgradeableScreen<MenuEssentiaCellWorkbench> {

    private static final float WELL_DISABLED_TINT = 0.6f;
    private final ToggleButton copyMode;

    public ScreenEssentiaCellWorkbench(
            MenuEssentiaCellWorkbench menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        addToLeftToolbar(new ActionButton(ActionItems.COG, items -> menu.partitionToContents()));
        addToLeftToolbar(new ActionButton(ActionItems.CLOSE, items -> menu.clearPartition()));
        copyMode = addToLeftToolbar(new ToggleButton(
                Icon.COPY_MODE_ON, Icon.COPY_MODE_OFF,
                GuiText.CopyMode.text(), GuiText.CopyModeDesc.text(),
                button -> menu.toggleCopyMode()));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        copyMode.setState(menu.keepOnRemove);
    }

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

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushMatrix();
        graphics.pose().translate(leftPos, topPos);
        for (int well = 0; well < MenuEssentiaCellWorkbench.partitionSlotCount(); well++) {
            if (menu.isPartitionSlotEnabled(well)) {
                continue;
            }
            Slot slot = menu.slots.get(menu.partitionSlotIndex(well));
            if (isHovering(slot, mouseX, mouseY)) {
                renderSlotHighlight(graphics, slot, mouseX, mouseY, 0.0f);
                continue;
            }
            Blitter.icon(Icon.SLOT_BACKGROUND)
                    .dest(slot.x - 1, slot.y - 1)
                    .color(WELL_DISABLED_TINT, WELL_DISABLED_TINT, WELL_DISABLED_TINT, 1.0f)
                    .blit(graphics);
        }
        graphics.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Slot hovered = getSlotUnderMouse();
        int well = hovered == null ? -1 : menu.wellOf(hovered);
        if (well >= 0) {
            if (menu.isPartitionSlotEnabled(well)) {
                var carried = menu.getCarried();
                var contained = EssentiaContainers.contained(carried);
                if (carried.isEmpty()) {
                    ClientPacketDistributor.sendToServer(
                            new PartitionWellPayload(menu.containerId, well, PartitionWellPayload.CLEAR));
                } else if (contained != null
                        && contained.what() instanceof AEssentiaKey key) {
                    ClientPacketDistributor.sendToServer(new PartitionWellPayload(menu.containerId, well, key.getId()));
                }
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected guideme.PageAnchor getHelpTopic() {
        return TECEGuide.page("essentia-storage");
    }

    @Override
    protected void openHelp() {
        TECEGuide.open(menu.getPlayer(), "essentia-storage");
    }

}
