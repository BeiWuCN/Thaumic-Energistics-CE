package thaumicenergistics.menu;

import appeng.api.storage.ITerminalHost;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics.essentia.EssentiaFillHelper;
import thaumicenergistics.network.ContainerSlot;

/**
 * The Essentia Terminal's menu, shared by the cable part and the wireless item.
 *
 * <p>Everything ordinary about it comes from AE2's {@link MEStorageMenu}: the terminal's item list, the
 * search and sort controls, the crafting grid, the upgrade slots. Essentia needs no code of its own to be
 * <em>shown</em> - registering the key type was the whole of that, because the terminal lists whatever
 * keys the network holds and gets their names and amounts from the key type.
 *
 * <p>What is left is getting essentia in and out, and that lives here rather than in the screen because
 * it moves things: {@link #fillFromNetwork} and {@link #deposit}.
 *
 * <p>Which key types the terminal offers is not decided here. It comes from the host's
 * {@code KeyTypeSelection}, so the cable part can present an essentia-only terminal while AE2's own
 * machinery still handles the list.
 *
 * <p>Both directions act on the container the player holds - the cursor stack, or the main hand when the
 * cursor is empty - and neither touches anything else. A stack that is not a jar or a phial is refused
 * before any essentia moves; see {@link EssentiaFillHelper#isSupportedContainer}.
 */
public class MenuEssentiaTerminal extends MEStorageMenu {

    public MenuEssentiaTerminal(MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host);
    }

    /** True on the side that owns the network. */
    public boolean isServerSide() {
        return !isClientSide();
    }

    /**
     * Takes essentia out of the network and into the player's container.
     *
     * <p>Server side only. The container is looked up on the server rather than taken from the packet:
     * {@code stack} is only a hint about what the client meant to hold, and this fills what is actually
     * there.
     *
     * @param where the cursor or the main hand - see {@link ContainerSlot}
     * @return whether anything was transferred
     */
    public boolean fillFromNetwork(Player player, int where, ResourceLocation aspectId) {
        if (isClientSide()) {
            return false;
        }
        ItemStack container = containerAt(player, where);
        if (container == null || !EssentiaFillHelper.isSupportedContainer(container)) {
            return false;
        }
        boolean moved = EssentiaFillHelper.fillFromNetwork(
                player.level(), storage, energySource, getActionSource(), player, container, aspectId);
        if (moved) {
            // A filled container replaces the one held - a phial comes back as another stack - so the
            // client's copy of both places is stale.
            broadcastChanges();
        }
        return moved;
    }

    /**
     * Empties an essentia container into the network.
     *
     * <p>Server side only, and it works from what the server holds rather than from what the client sent:
     * the payload's stack is only used to find the container the player meant. A client that named a place
     * the server does not agree about - because the inventory moved on between the click and the packet -
     * would otherwise be able to hand over a container that is not there.
     *
     * @param player the player whose menu this is
     * @param where the cursor, the main hand, or a menu slot id
     * @param claimed what the client says it was holding
     */
    public void deposit(Player player, int where, ItemStack claimed) {
        if (isClientSide()) {
            return;
        }

        // A menu slot: shift-right-click on a player slot empties the container sitting in it.
        if (where >= 0) {
            if (where >= slots.size()) {
                return;
            }
            Slot target = slots.get(where);
            // Only the player's own slots, so a slot AE2 owns - an upgrade slot, the crafting grid - can
            // never be emptied from here.
            if (!isPlayerSideSlot(target)) {
                return;
            }
            ItemStack inSlot = target.getItem();
            if (!EssentiaFillHelper.isSupportedContainer(inSlot)) {
                return;
            }
            ItemStack left = emptyIntoNetwork(inSlot);
            if (left != null) {
                target.set(left);
                // The container changed underneath the click, so the client's copy of that slot is stale.
                broadcastChanges();
            }
            return;
        }

        ItemStack container = containerAt(player, where);
        if (container == null || !EssentiaFillHelper.isSupportedContainer(container)) {
            return;
        }
        ItemStack left = emptyIntoNetwork(container);
        if (left == null) {
            return;
        }
        if (where == ContainerSlot.CURSOR) {
            setCarried(left);
        } else {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, left);
        }
        broadcastChanges();
    }

    /**
     * The stack a place names, or {@code null} when that place is not one this menu will move.
     *
     * <p>Only the cursor and the main hand are offered. Those are the two places "the container I am
     * using" can be, and neither is a menu slot, which is why they need ids of their own.
     */
    private ItemStack containerAt(Player player, int where) {
        return switch (where) {
            case ContainerSlot.CURSOR -> getCarried();
            case ContainerSlot.MAIN_HAND -> player.getMainHandItem();
            default -> null;
        };
    }

    /**
     * Empties an essentia container into the network.
     *
     * @return {@code null} when the stack is not a container the terminal handles
     */
    public ItemStack emptyIntoNetwork(ItemStack stack) {
        if (isClientSide()) {
            return null;
        }
        return EssentiaFillHelper.emptyIntoNetwork(storage, energySource, getActionSource(), stack);
    }
}
