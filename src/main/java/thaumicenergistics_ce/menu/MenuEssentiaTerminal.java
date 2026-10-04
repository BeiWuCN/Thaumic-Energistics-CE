package thaumicenergistics_ce.menu;

import appeng.api.storage.ITerminalHost;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.essentia.EssentiaFillHelper;
import thaumicenergistics_ce.network.ContainerSlot;

/**
 * The Essentia Terminal's menu, shared by the cable part and the wireless item.
 *
 * <ul>
 * <li>Everything ordinary comes from AE2's {@link MEStorageMenu}: the item list, search and sort, the
 * crafting grid, the upgrade slots. Essentia only needs its key type registered.
 * <li>Moving essentia lives here rather than in the screen, because it does move things:
 * {@link #fillFromNetwork} and {@link #deposit}.
 * <li>Which key types are offered is not decided here but by the host's {@code KeyTypeSelection}, so the
 * cable part can present an essentia-only terminal while AE2 still handles the list.
 * <li>Both directions act only on the container the player holds, refusing anything that is not a jar or a
 * phial.
 * </ul>
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
     * Takes essentia out of the network and into the player's container. Server side only.
     * @param where the cursor or the main hand - see {@link ContainerSlot}; {@code stack} is only a hint, so
     *     the container is looked up on the server rather than taken from the packet
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
            // A filled container is replaced by another stack, so the client's copy of both places is stale.
            broadcastChanges();
        }
        return moved;
    }

    /**
     * Empties an essentia container into the network. Server side only, working from what the server holds:
     * the payload's stack only locates the container meant, so a moved inventory cannot be handed over.
     * @param where the cursor, the main hand, or a menu slot id
     * @param claimed what the client says it was holding, never trusted
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
            // Only the player's own slots, so no slot AE2 owns can be emptied from here.
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
            player.setItemInHand(InteractionHand.MAIN_HAND, left);
        }
        broadcastChanges();
    }

    /**
     * The stack a place names, or {@code null} when that place is not one this menu will move: the cursor and
     * the main hand, the only places "the container I am using" can be, and neither is a slot.
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
     * @return {@code null} when the stack is not a container the terminal handles
     */
    public ItemStack emptyIntoNetwork(ItemStack stack) {
        if (isClientSide()) {
            return null;
        }
        return EssentiaFillHelper.emptyIntoNetwork(storage, energySource, getActionSource(), stack);
    }
}
