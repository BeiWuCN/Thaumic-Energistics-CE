package thaumicenergistics_ce.client.gui;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
import appeng.client.gui.style.ScreenStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics_ce.essentia.EssentiaFillHelper;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.menu.MenuEssentiaTerminal;
import thaumicenergistics_ce.menu.slot.ContainerSlot;
import thaumicenergistics_ce.network.EssentiaDepositPayload;
import thaumicenergistics_ce.network.EssentiaFillPayload;
import thaumicenergistics_ce.util.ThELog;

/**
 * The Essentia Terminal's screen: AE2's terminal wholesale, plus two gestures of its own.
 * <ul>
 *   <li>Right-click empties a held jar or phial into the network, left-click an entry fills one from it;
 *       shift takes the whole held stack, and shift-right-click empties where the container lies.
 *   <li>A held container is never inserted: AE2's entry click means <em>insert the cursor</em>.
 * </ul>
 */
public class ScreenEssentiaTerminal extends MEStorageScreen<MenuEssentiaTerminal> {

    private static final String TAG = "[essentia-terminal] ";

    public ScreenEssentiaTerminal(
            MenuEssentiaTerminal menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && handleRightClick()) {
            return true;
        }
        if (button == 0 && handleLeftClick()) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        if (slot instanceof RepoSlot && cursorIsContainer()) {
            // Silently: slotClicked fires once per slot the cursor crosses, so a drag would log per cell.
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    private boolean handleRightClick() {
        // Shift-right-click on a player slot: empty that slot's container where it lies.
        if (hasShiftDown() && hoveredSlot != null && menu.isPlayerSideSlot(hoveredSlot)) {
            ItemStack inSlot = hoveredSlot.getItem();
            if (EssentiaFillHelper.isSupportedContainer(inSlot)) {
                // The menu's slot id, not the inventory index: AE2 puts view-cell and upgrade slots ahead
                // of the player's, and the server resolves this against its own slot list.
                PacketDistributor.sendToServer(new EssentiaDepositPayload(
                        menu.containerId, menu.slots.indexOf(hoveredSlot), inSlot));
                return true;
            }
            return false;
        }

        ItemStack container = heldContainer();
        if (container == null) {
            return false;
        }
        if (!EssentiaFillHelper.isContainerEmpty(container)) {
            // Filled: the contents go into the network; empty has nothing to deposit.
            PacketDistributor.sendToServer(new EssentiaDepositPayload(
                    menu.containerId, whereHeld(), container));
            return true;
        }
        // Swallow it: AE2 reads the right-click as "put this item in the network".
        return true;
    }

    private boolean handleLeftClick() {
        ItemStack container = heldContainer();
        if (container == null) {
            return false;
        }
        if (hoveredSlot instanceof RepoSlot repoSlot) {
            var entry = repoSlot.getEntry();
            if (cursorIsContainer() && entry != null && !(entry.getWhat() instanceof AEssentiaKey)) {
                // Logged only in the surprising case: a container on the cursor over a non-essentia
                // entry, where the insertion leak used to happen.
                ThELog.LOG.info(TAG + "entry {} is not essentia ({}), so a held container"
                        + " cannot be drawn from here", entry.getWhat().getId(),
                        entry.getWhat().getClass().getSimpleName());
            }
            if (entry != null
                    && entry.getWhat() instanceof AEssentiaKey key
                    && EssentiaFillHelper.isContainerEmpty(container)) {
                // Shift turns the same click into "the whole held stack": filled as far as the network pays
                // for, with the ones it could not cover left where they are.
                PacketDistributor.sendToServer(new EssentiaFillPayload(
                        menu.containerId, key.getId(), whereHeld(), container, hasShiftDown()));
                return true;
            }
            // Still a grid entry: AE2 would insert what the cursor holds, one item per click. A jar or
            // a phial goes in only through the two gestures above.
            if (cursorIsContainer()) {
                ThELog.LOG.info(TAG + "entry click refused: the cursor holds a container,"
                        + " which is never inserted into the network");
                return true;
            }
            // The cursor is empty, so AE2's click is a withdrawal: nothing to leak.
            return false;
        }
        return false;
    }

    private boolean cursorIsContainer() {
        ItemStack carried = menu.getCarried();
        return !carried.isEmpty() && EssentiaFillHelper.isSupportedContainer(carried);
    }

    private ItemStack heldContainer() {
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            return EssentiaFillHelper.isSupportedContainer(carried) ? carried : null;
        }
        ItemStack mainHand = menu.getPlayerInventory().player.getMainHandItem();
        return EssentiaFillHelper.isSupportedContainer(mainHand) ? mainHand : null;
    }

    private int whereHeld() {
        return menu.getCarried().isEmpty() ? ContainerSlot.MAIN_HAND : ContainerSlot.CURSOR;
    }
}
