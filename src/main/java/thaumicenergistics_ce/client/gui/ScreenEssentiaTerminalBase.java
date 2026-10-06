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
import thaumicenergistics_ce.menu.MenuEssentiaTerminalBase;
import thaumicenergistics_ce.menu.slot.ContainerSlot;
import thaumicenergistics_ce.network.EssentiaDepositPayload;
import thaumicenergistics_ce.network.EssentiaFillPayload;
import thaumicenergistics_ce.util.ThELog;

/**
 * The jar and phial gestures of any terminal screen that has them.
 * <ul>
 *   <li>Two screens need it: the essentia terminal and the wireless arcane crafting terminal.
 *   <li>Inherited rather than copied: the gestures read {@code hoveredSlot}, which no helper can see.
 *   <li>Nothing here is drawn: a screen that says no to {@link #essentiaGesturesAtAll()} is AE2's own.
 * </ul>
 */
public abstract class ScreenEssentiaTerminalBase<M extends MenuEssentiaTerminalBase>
        extends MEStorageScreen<M> {

    /** Both screens log under this tag, and the gestures that use it are the same code in each. */
    protected static final String TAG = "[essentia-terminal] ";

    protected ScreenEssentiaTerminalBase(
            M menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    /** Whether this screen offers the jar and phial gestures at all; only a terminal behind a card does. */
    protected abstract boolean essentiaGesturesAtAll();

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!essentiaGesturesAtAll()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
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
        if (essentiaGesturesAtAll() && slot instanceof RepoSlot repoSlot && cursorIsContainer()) {
            var entry = repoSlot.getEntry();
            if (entry != null && entry.getWhat() instanceof AEssentiaKey) {
                // Ours, with a container on the cursor: the gestures are the only way in, so AE2's own
                // slot click does not run. Silently - it fires once per cell the cursor crosses.
                return;
            }
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
        boolean overEssentia = false;
        if (hoveredSlot instanceof RepoSlot repoSlot) {
            var entry = repoSlot.getEntry();
            overEssentia = entry != null && entry.getWhat() instanceof AEssentiaKey;
        }
        if (!overEssentia) {
            // Empty space, or an entry of another kind: the click belongs to AE2, so a jar or phial in
            // hand is put into the network like any other item.
            return false;
        }
        if (EssentiaFillHelper.isContainerEmpty(container)) {
            // Ours and empty: AE2's right-click would insert the container, and the empty one is the tool
            // for a left-click, so this click stops here.
            return true;
        }
        // Ours and filled: the contents go into the network, from the cursor or from the main hand.
        PacketDistributor.sendToServer(new EssentiaDepositPayload(
                menu.containerId, whereHeld(), container));
        return true;
    }

    private boolean handleLeftClick() {
        ItemStack container = heldContainer();
        if (container == null || !(hoveredSlot instanceof RepoSlot repoSlot)) {
            return false;
        }
        var entry = repoSlot.getEntry();
        if (entry == null) {
            return false;
        }
        if (entry.getWhat() instanceof AEssentiaKey key && EssentiaFillHelper.isContainerEmpty(container)) {
            // Shift turns the same click into "the whole held stack": filled as far as the network pays
            // for, with the ones it could not cover left where they are.
            PacketDistributor.sendToServer(new EssentiaFillPayload(
                    menu.containerId, key.getId(), whereHeld(), container, hasShiftDown()));
            return true;
        }
        if (!(entry.getWhat() instanceof AEssentiaKey)) {
            // An entry of another kind: the container is an ordinary item here, so AE2's click still runs.
            return false;
        }
        if (cursorIsContainer()) {
            // Ours, with a jar or phial on the cursor: AE2 would insert the container with the essentia
            // still inside it, and the gesture for that is shift-right-click, so the click stops here.
            ThELog.LOG.info(TAG + "entry click refused: the cursor holds a container,"
                    + " which is never inserted into the network");
            return true;
        }
        // The container is in the main hand, not on the cursor, so AE2's click is a withdrawal.
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
