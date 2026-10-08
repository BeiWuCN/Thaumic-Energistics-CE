package thaumicenergistics_ce.menu;

import appeng.api.storage.ITerminalHost;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.essentia.EssentiaFillHelper;
import thaumicenergistics_ce.menu.slot.ContainerSlot;
import thaumicenergistics_ce.network.EssentiaTerminalReceiver;

/**
 * 任何搬运手持罐或瓶内容物而非物品的终端中，属于源质的那一半。
 * 源质终端和无线奥术合成终端都要它；子类继承而非复制，
 * 该手势需要这个菜单的携带堆、槽位与玩家判定。
 * 每次动作都查询它，从不缓存，卡片一离开槽位就把这些手势一起带走。
 */
public abstract class MenuEssentiaTerminalBase extends MEStorageMenu implements EssentiaTerminalReceiver {

    public MenuEssentiaTerminalBase(
            MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host);
    }

    protected MenuEssentiaTerminalBase(MenuType<?> menuType, int id, Inventory playerInventory,
            ITerminalHost host, boolean bindInventory) {
        super(menuType, id, playerInventory, host, bindInventory);
    }

    public boolean isServerSide() {
        return !isClientSide();
    }

    @Override
    public int containerId() {
        return this.containerId;
    }

    /** 由子类终端覆写：只有它的访问卡在槽里时才提供这些手势。 */
    protected boolean essentiaAccessGranted() {
        return true;
    }

    @Override
    public boolean fillFromNetwork(Player player, int where, Identifier aspectId, boolean wholeStack) {
        if (isClientSide() || !essentiaAccessGranted()) {
            return false;
        }
        ItemStack container = containerAt(player, where);
        if (container == null || !EssentiaFillHelper.isSupportedContainer(container)) {
            return false;
        }
        // 一轮消耗手持堆里的一个物品，交回一个已填充的；
        // 整堆点击就是重复这一轮：第一次被拒时停下，也在堆的最后一个物品处停下。
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
            // 已填充容器会被另一个物品堆替换，客户端对这两处的副本都过时了。
            broadcastChanges();
        }
        return moved;
    }

    @Override
    public void deposit(Player player, int where) {
        if (isClientSide() || !essentiaAccessGranted()) {
            return;
        }

        // 按菜单槽位：对玩家槽位 shift 右键，会清空放在里面的容器。
        if (where >= 0) {
            if (where >= slots.size()) {
                return;
            }
            Slot target = slots.get(where);
            // 只限玩家自己的槽位，AE2 拥有的槽位无法从这里清空。
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
            // 已填充容器会被另一个物品堆替换，客户端对这两处的副本都过时了。
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

    public ItemStack emptyIntoNetwork(ItemStack stack) {
        if (isClientSide()) {
            return null;
        }
        return EssentiaFillHelper.emptyIntoNetwork(storage, energySource, getActionSource(), stack);
    }

    private @Nullable ItemStack containerAt(Player player, int where) {
        return switch (where) {
            case ContainerSlot.CURSOR -> getCarried();
            case ContainerSlot.MAIN_HAND -> player.getMainHandItem();
            default -> null;
        };
    }
}
