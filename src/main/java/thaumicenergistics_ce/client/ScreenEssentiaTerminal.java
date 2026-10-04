package thaumicenergistics_ce.client;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
import appeng.client.gui.style.ScreenStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.essentia.EssentiaFillHelper;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.menu.MenuEssentiaTerminal;
import thaumicenergistics_ce.menu.slot.ContainerSlot;
import thaumicenergistics_ce.network.EssentiaDepositPayload;
import thaumicenergistics_ce.network.EssentiaFillPayload;

/**
 * The Essentia Terminal's screen: AE2's terminal wholesale, plus two gestures of its own.
 * <ul>
 *   <li>Right-click with a filled jar or phial empties it into the network, left-click an entry with
 *       an empty one draws that aspect out, shift-right-click empties a container where it lies.
 *   <li>A held container is never inserted: AE2's entry click means <em>insert the cursor</em>.
 * </ul>
 */
public class ScreenEssentiaTerminal extends MEStorageScreen<MenuEssentiaTerminal> {

    /** Diagnostic tag: one line per container click, never per tick. */
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

    /** Refuses the drag path into AE2's grid handler, which would scatter the cursor's container over the
     * list one item at a time. */
    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        if (slot instanceof RepoSlot && cursorIsContainer()) {
            // Silently: slotClicked fires once per slot the cursor crosses, so a drag would log per cell.
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    /** Right-click: empty a filled container into the network.
     * @return whether the click was ours */
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

    /** Left-click: draw the clicked aspect out of the network and into the container. */
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
                ThaumicEnergistics.LOG.info(TAG + "entry {} is not essentia ({}), so a held container"
                        + " cannot be drawn from here", entry.getWhat().getId(),
                        entry.getWhat().getClass().getSimpleName());
            }
            if (!hasShiftDown()
                    && EssentiaFillHelper.isContainerEmpty(container)
                    && entry != null
                    && entry.getWhat() instanceof AEssentiaKey key) {
                PacketDistributor.sendToServer(
                        new EssentiaFillPayload(menu.containerId, key.getId(), whereHeld(), container));
                return true;
            }
            // Still a grid entry: AE2 would insert what the cursor holds, one item per click. A jar or
            // a phial goes in only through the two gestures above.
            if (cursorIsContainer()) {
                ThaumicEnergistics.LOG.info(TAG + "entry click refused: the cursor holds a container,"
                        + " which is never inserted into the network");
                return true;
            }
            // The cursor is empty, so AE2's click is a withdrawal: nothing to leak.
            return false;
        }
        return false;
    }

    /** Whether the stack on the cursor is a jar or a phial - the one AE2 would insert. */
    private boolean cursorIsContainer() {
        ItemStack carried = menu.getCarried();
        return !carried.isEmpty() && EssentiaFillHelper.isSupportedContainer(carried);
    }

    /** The cursor's stack, or the main hand when the cursor is empty - {@link #whereHeld} picks the same.
     * @return the container, or {@code null} when neither holds a jar or a phial */
    private ItemStack heldContainer() {
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            return EssentiaFillHelper.isSupportedContainer(carried) ? carried : null;
        }
        ItemStack mainHand = menu.getPlayerInventory().player.getMainHandItem();
        return EssentiaFillHelper.isSupportedContainer(mainHand) ? mainHand : null;
    }

    /** Which of the two places {@link #heldContainer} took its stack from. */
    private int whereHeld() {
        return menu.getCarried().isEmpty() ? ContainerSlot.MAIN_HAND : ContainerSlot.CURSOR;
    }
}
