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
import thaumicenergistics_ce.network.ContainerSlot;
import thaumicenergistics_ce.network.EssentiaDepositPayload;
import thaumicenergistics_ce.network.EssentiaFillPayload;

/**
 * The Essentia Terminal's screen.
 *
 * <p>Everything visible is AE2's: the item list, the search box, the sort buttons, the scrollbar - the
 * whole terminal, driven by the same menu. This class only adds the two gestures, because essentia does
 * not travel as an item and AE2's click handling has no operation for "move the contents of the thing I am
 * holding".
 *
 * <p>Both gestures need a jar or a phial. With anything else held - an essentia crystal, a label, an
 * ordinary item - the screen handles no clicks at all and AE2 behaves exactly as it always does, so
 * moving items in and out of the network is untouched:
 *
 * <ul>
 *   <li><b>Right-click</b> anywhere with a <b>filled</b> container empties it into the network. A jar stays
 *       a jar; a phial comes back as empty glass.
 *   <li><b>Left-click</b> a <b>network entry</b> with an <b>empty</b> container draws that aspect out of
 *       the network and into the container - a phial in one go, a jar as far as it will fill.
 *   <li><b>Shift-right-click</b> a container <b>in a player slot</b> empties that slot's container, where
 *       it lies.
 * </ul>
 *
 * <p>The two refusals are deliberate. A container with something in it is not filled, because the second
 * aspect would overwrite the first; a container with nothing in it is not emptied, because there is
 * nothing to empty.
 *
 * <h2>A held container is never inserted into the network</h2>
 *
 * <p>AE2's left-click on a grid entry is <em>insert what the cursor holds</em>, and that is exactly what
 * must not happen to a jar or a phial: one click on an entry that is not essentia used to pull a single
 * phial out of the carried stack and into the network. Holding a container on the cursor therefore
 * swallows the grid-entry click unless it is the fill gesture above - and {@link #slotClicked} covers the
 * drag path, which reaches the same AE2 code without passing through a click. That is a deliberate
 * deviation from the reference build, which leaves left-click to AE2 and has the leak.
 */
public class ScreenEssentiaTerminal extends MEStorageScreen<MenuEssentiaTerminal> {

    /** Diagnostic tag. One line per click that involves a container, and nothing per tick. */
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

    /**
     * The drag path into AE2's grid handler, which would scatter the cursor's container over the list one
     * item at a time. Dragging is not one of the two gestures, so it is refused outright.
     */
    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        if (slot instanceof RepoSlot && cursorIsContainer()) {
            // Deliberately silent: slotClicked fires once per slot the cursor crosses, so a drag across
            // the list would write a line per cell. Nothing happened, and the click path logs the one
            // line per player action that is worth having.
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    /** Right-click: empty a filled container into the network. @return whether the click was ours */
    private boolean handleRightClick() {
        // Shift-right-click on a player slot: empty that slot's container, where it lies.
        if (hasShiftDown() && hoveredSlot != null && menu.isPlayerSideSlot(hoveredSlot)) {
            ItemStack inSlot = hoveredSlot.getItem();
            if (EssentiaFillHelper.isSupportedContainer(inSlot)) {
                // The menu's slot id, not the slot's index inside the player's inventory: AE2 puts view-cell
                // and upgrade slots in front of the player's, so the two numbers differ and the server
                // resolves this against its own slot list.
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
            // Filled: its contents go into the network. A container with nothing in it has nothing to
            // deposit, and is left to the left-click and to AE2.
            PacketDistributor.sendToServer(new EssentiaDepositPayload(
                    menu.containerId, whereHeld(), container));
            return true;
        }
        // An empty container right-clicked on an entry would otherwise be read as "put this item in the
        // network", which is not what a jar or a phial is for here. Swallow it.
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
                // Logged only in the surprising case - a container on the cursor over an entry that is not
                // essentia, which is exactly where the insertion leak used to happen.
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
            // Not the fill gesture, and still a grid entry: AE2 would insert whatever the cursor holds,
            // one item per click. A jar or a phial goes in through nothing but the two gestures above.
            if (cursorIsContainer()) {
                ThaumicEnergistics.LOG.info(TAG + "entry click refused: the cursor holds a container,"
                        + " which is never inserted into the network");
                return true;
            }
            // The cursor is empty, so AE2's click is a withdrawal and there is nothing to leak.
            return false;
        }
        return false;
    }

    /** Whether the stack on the cursor is a jar or a phial - the one AE2 would insert. */
    private boolean cursorIsContainer() {
        ItemStack carried = menu.getCarried();
        return !carried.isEmpty() && EssentiaFillHelper.isSupportedContainer(carried);
    }

    /**
     * The container the gesture acts on: what the cursor holds, or the main hand when the cursor is empty.
     *
     * <p>The main hand is a fallback rather than a second source, so the two never disagree about which
     * stack is meant - {@link #whereHeld} is the same choice, made once for the packet.
     *
     * @return the container, or {@code null} when neither place holds a jar or a phial
     */
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
