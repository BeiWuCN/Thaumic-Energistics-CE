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

    public static final appeng.menu.SlotSemantic QUANTUM_SINGULARITY =
            appeng.menu.SlotSemantics.register("THAUMICENERGISTICS_QUANTUM_SINGULARITY", true);

    private void addQuantumSlot(ITerminalHost host) {
        if (!thaumicenergistics_ce.item.QuantumTerminalSupport.enabled()
                || !(host instanceof thaumicenergistics_ce.item.QuantumWirelessTerminalMenuHost<?> quantum)) return;
        var slot = new appeng.menu.slot.AppEngSlot(quantum.singularityInventory(), 0) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return thaumicenergistics_ce.item.QuantumTerminalSupport.validSingularity(stack) && super.mayPlace(stack);
            }
            @Override
            public int getMaxStackSize() { return 1; }
        };
        slot.setIcon(appeng.client.gui.Icon.BACKGROUND_SINGULARITY);
        addSlot(slot, QUANTUM_SINGULARITY);
    }


    public MenuEssentiaTerminalBase(
            MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host);
        addQuantumSlot(host);
        updateWirelessEssentiaUpgradeSlots();
    }

    protected MenuEssentiaTerminalBase(MenuType<?> menuType, int id, Inventory playerInventory,
            ITerminalHost host, boolean bindInventory) {
        super(menuType, id, playerInventory, host, bindInventory);
        addQuantumSlot(host);
        updateWirelessEssentiaUpgradeSlots();
    }

    public void updateWirelessEssentiaUpgradeSlots() {
        if (!(getHost() instanceof thaumicenergistics_ce.item.WirelessEssentiaTerminalMenuHost)) return;
        var slots = getSlots(appeng.menu.SlotSemantics.UPGRADE);
        int remaining = thaumicenergistics_ce.item.QuantumTerminalSupport.enabled() ? 1 : 0;
        for (var slot : slots) {
            if (slot.hasItem()) remaining--;
        }
        for (var slot : slots) {
            boolean visible = slot.hasItem() || remaining-- > 0;
            ((appeng.menu.slot.AppEngSlot) slot).setSlotEnabled(visible);
        }
    }

    @Override
    public void broadcastChanges() {
        updateWirelessEssentiaUpgradeSlots();
        super.broadcastChanges();
    }

    @Override
    protected int transferStackToMenu(ItemStack stack) {
        var slots = getSlots(QUANTUM_SINGULARITY);
        if (!slots.isEmpty() && thaumicenergistics_ce.item.QuantumTerminalSupport.validSingularity(stack)) {
            var remaining = stack.copy();
            for (var slot : slots) {
                if (slot.isActive()) remaining = slot.safeInsert(remaining);
            }
            return stack.getCount() - remaining.getCount();
        }
        return super.transferStackToMenu(stack);
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
    public boolean fillFromNetwork(Player player, int where, ResourceLocation aspectId, boolean wholeStack) {
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
            ItemStack left = emptyIntoNetwork(player, inSlot);
            if (left != null) {
                target.set(left);
                // 容器在点击之下变了，客户端对该槽位的副本已过时。
                broadcastChanges();
            }
            return;
        }

        ItemStack container = containerAt(player, where);
        if (container == null || !EssentiaFillHelper.isSupportedContainer(container)) {
            return;
        }
        ItemStack left = emptyIntoNetwork(player, container);
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

    public ItemStack emptyIntoNetwork(Player player, ItemStack stack) {
        if (isClientSide()) {
            return null;
        }
        return EssentiaFillHelper.emptyIntoNetwork(storage, energySource, getActionSource(), player, stack);
    }

    private @Nullable ItemStack containerAt(Player player, int where) {
        return switch (where) {
            case ContainerSlot.CURSOR -> getCarried();
            case ContainerSlot.MAIN_HAND -> player.getMainHandItem();
            default -> null;
        };
    }
}