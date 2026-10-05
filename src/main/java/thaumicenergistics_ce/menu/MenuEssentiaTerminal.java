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
import thaumicenergistics_ce.menu.slot.ContainerSlot;
import thaumicenergistics_ce.net.EssentiaTerminalReceiver;

/**
 * The Essentia Terminal's menu, shared by the cable part and the wireless item.
 * <ul>
 *   <li>Everything ordinary comes from AE2's {@link MEStorageMenu}; essentia only adds its key type.
 *   <li>Moving essentia lives here, not in the screen, and acts only on the container the player
 *       holds; which key types are offered is the host's {@code KeyTypeSelection}.
 * </ul>
 */
public class MenuEssentiaTerminal extends MEStorageMenu implements EssentiaTerminalReceiver {

    public MenuEssentiaTerminal(MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host);
    }

    public boolean isServerSide() {
        return !isClientSide();
    }

    @Override
    public boolean fillFromNetwork(Player player, int where, ResourceLocation aspectId, boolean wholeStack) {
        if (isClientSide()) {
            return false;
        }
        ItemStack container = containerAt(player, where);
        if (container == null || !EssentiaFillHelper.isSupportedContainer(container)) {
            return false;
        }
        // One turn spends one item of the held stack and hands back a filled one, so the whole-stack
        // click is that turn repeated: it stops at the first refusal and at the last item of the stack.
        int turns = wholeStack ? container.getCount() : 1;
        boolean moved = false;
        for (int turn = 0; turn < turns; turn++) {
            if (!EssentiaFillHelper.isContainerEmpty(container)) {
                break;
            }
            if (!EssentiaFillHelper.fillFromNetwork(
                    player.level(), storage, energySource, getActionSource(), player, container, aspectId)) {
                break;
            }
            moved = true;
        }
        if (moved) {
            // A filled container is replaced by another stack, so the client's copy of both places is stale.
            broadcastChanges();
        }
        return moved;
    }

    @Override
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

    @Override
    public int containerId() {
        return containerId;
    }

    private ItemStack containerAt(Player player, int where) {
        return switch (where) {
            case ContainerSlot.CURSOR -> getCarried();
            case ContainerSlot.MAIN_HAND -> player.getMainHandItem();
            default -> null;
        };
    }

    public ItemStack emptyIntoNetwork(ItemStack stack) {
        if (isClientSide()) {
            return null;
        }
        return EssentiaFillHelper.emptyIntoNetwork(storage, energySource, getActionSource(), stack);
    }
}
