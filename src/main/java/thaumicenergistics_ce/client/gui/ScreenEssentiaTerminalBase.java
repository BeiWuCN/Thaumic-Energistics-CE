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
 * The jar and phial gestures of any terminal screen that has them, inherited rather than copied by
 * the two screens that need them, because the gestures read hoveredSlot, which no helper can see.
 * The row under the cursor says where from and the container says which way: an empty one is filled
 * from the row, a filled one is emptied into the network from a row as well as from a blank cell.
 * Shift keeps AE2's meaning - the whole held stack on either click, and the container where it lies
 * on a shift right click over the player's own slots. Nothing is drawn here.
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
                log("deposit requested: {} from menu slot {}", inSlot.getHoverName().getString(),
                        menu.slots.indexOf(hoveredSlot));
                PacketDistributor.sendToServer(new EssentiaDepositPayload(
                        menu.containerId, menu.slots.indexOf(hoveredSlot)));
                return true;
            }
            return false;
        }

        ItemStack container = heldContainer();
        if (container == null) {
            return false;
        }
        AEssentiaKey overKey = null;
        if (hoveredSlot instanceof RepoSlot repoSlot) {
            var entry = repoSlot.getEntry();
            if (entry != null && entry.getWhat() instanceof AEssentiaKey key) {
                overKey = key;
            }
        }
        if (overKey != null && EssentiaFillHelper.isContainerEmpty(container)) {
            // An empty container on a row that has something to give is a fill, on either button: the
            // container decides which way, so the button only decides between one item and the stack.
            log("fill requested: {} into the {} of {} from {}", overKey.getId(),
                    container.getHoverName().getString(),
                    heldName(), hasShiftDown() ? "the whole held stack" : "one item");
            PacketDistributor.sendToServer(new EssentiaFillPayload(
                    menu.containerId, overKey.getId(), whereHeld(), container, hasShiftDown()));
            return true;
        }
        if (!(hoveredSlot instanceof RepoSlot)) {
            // Not a cell of the grid at all: the click belongs to AE2, whatever is held.
            return false;
        }
        // A filled container is emptied into the network from a row exactly as from a cell that holds
        // nothing: the row decides where from, the container decides which way.
        log("deposit requested: {} from {}, over a repo cell", container.getHoverName().getString(),
                heldName());
        PacketDistributor.sendToServer(new EssentiaDepositPayload(
                menu.containerId, whereHeld()));
        return true;
    }

    private boolean handleLeftClick() {
        ItemStack container = heldContainer();
        if (container == null || !(hoveredSlot instanceof RepoSlot repoSlot)) {
            return false;
        }
        var entry = repoSlot.getEntry();
        boolean overEssentia = entry != null && entry.getWhat() instanceof AEssentiaKey;
        if (EssentiaFillHelper.isContainerEmpty(container)) {
            if (!overEssentia) {
                // An empty container and nothing to take: an entry of another kind is an ordinary item
                // click, and a cell that holds nothing has nowhere to take from.
                return false;
            }
            AEssentiaKey key = (AEssentiaKey) entry.getWhat();
            // Shift turns the same click into "the whole held stack": filled as far as the network pays
            // for, with the ones it could not cover left where they are.
            log("fill requested: {} into the {} of {} from {}", key.getId(),
                    container.getHoverName().getString(),
                    heldName(), hasShiftDown() ? "the whole held stack" : "one item");
            PacketDistributor.sendToServer(new EssentiaFillPayload(
                    menu.containerId, key.getId(), whereHeld(), container, hasShiftDown()));
            return true;
        }
        // A filled container is emptied into the network from a row exactly as from a cell that holds
        // nothing: the container decides the direction, so the row under the cursor is not asked.
        log("deposit requested: {} from {}, over a repo cell", container.getHoverName().getString(),
                heldName());
        PacketDistributor.sendToServer(new EssentiaDepositPayload(menu.containerId, whereHeld()));
        return true;
    }

    private static void log(String message, Object... args) {
        ThELog.LOG.info(TAG + message, args);
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

    /** The place {@link #whereHeld()} names, in words, for the log lines. */
    private String heldName() {
        return menu.getCarried().isEmpty() ? "the main hand" : "the cursor";
    }
}
