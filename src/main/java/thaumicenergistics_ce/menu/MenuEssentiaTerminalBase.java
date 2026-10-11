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
import thaumicenergistics_ce.util.ThELog;

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

    protected boolean essentiaAccessGranted() {
        return true;
    }

    @Override
    public boolean fillFromNetwork(Player player, int where, Identifier aspectId, boolean wholeStack) {
        if (isClientSide()) {
            return false;
        }
        if (!essentiaAccessGranted()) {
            ThELog.LOG.info("[源质手势] 从网络装填被拒：这个终端里没插源质访问卡");
            return false;
        }
        ItemStack container = containerAt(player, where);
        if (container == null || !EssentiaFillHelper.isSupportedContainer(container)) {
            return false;
        }
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
            broadcastChanges();
        }
        return moved;
    }

    @Override
    public void deposit(Player player, int where) {
        if (isClientSide()) {
            return;
        }
        if (!essentiaAccessGranted()) {
            ThELog.LOG.info("[源质手势] 倒回网络被拒：这个终端里没插源质访问卡");
            return;
        }
        ThELog.LOG.info("[源质手势] 收到倒回请求 where={}", where);

        if (where >= 0) {
            if (where >= slots.size()) {
                return;
            }
            Slot target = slots.get(where);
            if (!isPlayerSideSlot(target)) {
                return;
            }
            ItemStack inSlot = target.getItem();
            if (!EssentiaFillHelper.isSupportedContainer(inSlot)) {
                ThELog.LOG.info("[源质手势] 倒回被拒：槽位 {} 里不是源质容器（{}）", where, inSlot);
                return;
            }
            EssentiaFillHelper.DepositResult left = emptyIntoNetwork(inSlot);
            ThELog.LOG.info("[源质手势] 槽位 {} 倒回结果：{} -> {}", where, inSlot, left);
            if (left != null) {
                target.set(left.remaining());
                giveOverflow(player, left);
                broadcastChanges();
            }
            return;
        }

        ItemStack container = containerAt(player, where);
        if (container == null || !EssentiaFillHelper.isSupportedContainer(container)) {
            ThELog.LOG.info("[源质手势] 倒回被拒：{} 那一侧不是源质容器（{}）",
                    where == ContainerSlot.CURSOR ? "光标" : "主手", container);
            return;
        }
        EssentiaFillHelper.DepositResult left = emptyIntoNetwork(container);
        ThELog.LOG.info("[源质手势] 倒回结果：{} -> {}", container, left);
        if (left == null) {
            return;
        }
        if (where == ContainerSlot.CURSOR) {
            setCarried(left.remaining());
        } else {
            player.setItemInHand(InteractionHand.MAIN_HAND, left.remaining());
        }
        giveOverflow(player, left);
        broadcastChanges();
    }

    public EssentiaFillHelper.DepositResult emptyIntoNetwork(ItemStack stack) {
        if (isClientSide()) {
            return null;
        }
        return EssentiaFillHelper.emptyIntoNetwork(storage, energySource, getActionSource(), stack);
    }

    private static void giveOverflow(Player player, EssentiaFillHelper.DepositResult result) {
        for (ItemStack stack : result.overflow()) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    private @Nullable ItemStack containerAt(Player player, int where) {
        return switch (where) {
            case ContainerSlot.CURSOR -> getCarried();
            case ContainerSlot.MAIN_HAND -> player.getMainHandItem();
            default -> null;
        };
    }
}
