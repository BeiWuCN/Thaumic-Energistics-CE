package thaumicenergistics_ce.client.gui;

import appeng.api.config.ActionItems;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench;
import thaumicenergistics_ce.network.PartitionWellPayload;

/**
 * The Essentia Cell Workbench's screen.
 * An AE2 upgradeable screen, so art, slots, title and the upgrades panel come from the style; the
 * cog fills the wells from the cell, the X empties them, and a click takes one mark back out. AE2's
 * fuzzy and copy-mode switches are left out, since an essentia key has no damage or NBT to match.
 */
public class ScreenEssentiaCellWorkbench extends UpgradeableScreen<MenuEssentiaCellWorkbench> {

    /** What a well with no cell behind it is tinted with: AE2's own slot art, a little over half lit. */
    private static final float WELL_DISABLED_TINT = 0.6f;

    public ScreenEssentiaCellWorkbench(
            MenuEssentiaCellWorkbench menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        addToLeftToolbar(new ActionButton(ActionItems.COG, items -> menu.partitionToContents()));
        addToLeftToolbar(new ActionButton(ActionItems.CLOSE, items -> menu.clearPartition()));
    }

    /**
     * AE2 paints a disabled well at a fifth of its opacity and gives it no icon, which this GUI's own art
     * swallows: the same slot art in grey keeps the wells looking like wells, saying no mark can go in.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos, topPos, 0.0f);
        for (int well = 0; well < MenuEssentiaCellWorkbench.partitionSlotCount(); well++) {
            if (menu.isPartitionSlotEnabled(well)) {
                continue;
            }
            Slot slot = menu.slots.get(menu.partitionSlotIndex(well));
            // A disabled well is not active, so AE2 never takes it for the slot under the mouse and never
            // highlights it: the box is drawn here in its place, so the grey goes where the pointer is.
            if (isHovering(slot, mouseX, mouseY)) {
                renderSlotHighlight(graphics, slot, mouseX, mouseY, partialTick);
                continue;
            }
            Icon.SLOT_BACKGROUND.getBlitter()
                    .dest(slot.x - 1, slot.y - 1)
                    .color(WELL_DISABLED_TINT, WELL_DISABLED_TINT, WELL_DISABLED_TINT, 1.0f)
                    .blit(graphics);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Slot hovered = getSlotUnderMouse();
        int well = hovered == null ? -1 : menu.wellOf(hovered);
        if (well >= 0) {
            if (menu.isPartitionSlotEnabled(well)) {
                PacketDistributor.sendToServer(
                        new PartitionWellPayload(menu.containerId, well, PartitionWellPayload.CLEAR));
            }
            // The click stops here either way, so a carried item cannot land in a well.
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
