package thaumicenergistics_ce.client.gui;

import appeng.api.config.ActionItems;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.util.Icon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench;
import thaumicenergistics_ce.network.PartitionWellPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 源质元件工作台的界面。AE2 可升级界面，美术、槽位、标题和升级面板都来自样式；
 * 齿轮从元件填井，X 清空，点一个标记就把它取回。AE2 的模糊和复制模式开关没留，
 * 源质 [AEKey] 没有耐久或 NBT 可匹配。
 */
public class ScreenEssentiaCellWorkbench extends UpgradeableScreen<MenuEssentiaCellWorkbench> {

    /** 背后没有元件的井用这个色调：AE2 自己的槽位美术，亮度略高于一半。 */
    private static final float WELL_DISABLED_TINT = 0.6f;

    public ScreenEssentiaCellWorkbench(
            MenuEssentiaCellWorkbench menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        addToLeftToolbar(new ActionButton(ActionItems.COG, items -> menu.partitionToContents()));
        addToLeftToolbar(new ActionButton(ActionItems.CLOSE, items -> menu.clearPartition()));
    }

    /**
     * AE2 把禁用的井画成五分之一不透明度、还不给图标，本界面自己的美术图会把它吞掉：
     * 同一套槽位美术调成灰色，井看着还是井，又说明放不进标记。
     */
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
            // 禁用的井不是 active，AE2 从不把它当成鼠标下的槽位，也不给它高亮：
            // 这里代它画这个框，灰色跟着指针走。
            if (isHovering(slot, mouseX, mouseY)) {
                renderSlotHighlight(graphics, slot, mouseX, mouseY, partialTick);
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
                ClientPacketDistributor.sendToServer(
                        new PartitionWellPayload(menu.containerId, well, PartitionWellPayload.CLEAR));
            }
            // 两种走法点击都在此止步，手上拿的物品落不进井里。
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
