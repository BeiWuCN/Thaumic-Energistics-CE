package com.beiwu.thaumicenergistics_ce.client.gui;

import appeng.api.config.ActionItems;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.core.localization.GuiText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import com.beiwu.thaumicenergistics_ce.util.EssentiaContainers;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import net.neoforged.neoforge.network.PacketDistributor;
import com.beiwu.thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench;
import com.beiwu.thaumicenergistics_ce.network.PartitionWellPayload;

/**
 * 源质元件工作台的界面。AE2 可升级界面，美术、槽位、标题和升级面板都来自样式；
 * 齿轮从元件填井，X 清空，点一个标记就把它取回。AE2 的模糊模式开关没留，
 * 源质 [AEKey] 没有耐久或 NBT 可匹配。
 */
public class ScreenEssentiaCellWorkbench extends UpgradeableScreen<MenuEssentiaCellWorkbench> {

    /** 背后没有元件的井用这个色调：AE2 自己的槽位美术，亮度略高于一半。 */
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

    /**
     * AE2 把禁用的井画成五分之一不透明度且不给图标，本界面的美术会把它吞掉：
     * 同一张槽位图改灰，井还像井，又表示这里放不进标记。
     */
    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        // AE2 draws tooltips after this foreground pass.
        Slot cell = menu.cellSlot();
        if (cell != null && cell.getItem().isEmpty()) {
            Icon.BACKGROUND_STORAGE_CELL.getBlitter().dest(cell.x, cell.y).blit(graphics);
        }
        for (int well = 0; well < MenuEssentiaCellWorkbench.partitionSlotCount(); well++) {
            if (menu.isPartitionSlotEnabled(well)) {
                continue;
            }
            Slot slot = menu.slots.get(menu.partitionSlotIndex(well));
            // 禁用的井不是 active，AE2 从不把它当成鼠标下的槽位，也不给它高亮：
            // 这里代它画这个框，灰色跟着指针走。
            if (isHovering(slot, mouseX, mouseY)) {
                renderSlotHighlight(graphics, slot, mouseX, mouseY, 0.0f);
                continue;
            }
            Icon.SLOT_BACKGROUND.getBlitter()
                    .dest(slot.x - 1, slot.y - 1)
                    .color(WELL_DISABLED_TINT, WELL_DISABLED_TINT, WELL_DISABLED_TINT, 1.0f)
                    .blit(graphics);
        }
    }

    @Override
    public void drawTooltip(GuiGraphics graphics, int mouseX, int mouseY, java.util.List<Component> lines) {
        Slot hovered = getSlotUnderMouse();
        int well = hovered == null ? -1 : menu.wellOf(hovered);
        var contained = EssentiaContainers.contained(menu.getCarried());
        if (well >= 0 && menu.isPartitionSlotEnabled(well) && contained != null
                && contained.what() instanceof AEssentiaKey key) {
            lines = java.util.List.of(Component.translatable(
                    "gui.thaumicenergistics_ce.cell_workbench.mark_essentia", key.getDisplayName()));
        }
        super.drawTooltip(graphics, mouseX, mouseY, lines);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Slot hovered = getSlotUnderMouse();
        int well = hovered == null ? -1 : menu.wellOf(hovered);
        if (well >= 0) {
            if (menu.isPartitionSlotEnabled(well)) {
                var carried = menu.getCarried();
                var contained = EssentiaContainers.contained(carried);
                if (carried.isEmpty()) {
                    PacketDistributor.sendToServer(
                            new PartitionWellPayload(menu.containerId, well, PartitionWellPayload.CLEAR));
                } else if (contained != null
                        && contained.what() instanceof AEssentiaKey key) {
                    PacketDistributor.sendToServer(new PartitionWellPayload(menu.containerId, well, key.getId()));
                }
            }
            // 两种走法点击都在此止步，手上拿的物品落不进井里。
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
