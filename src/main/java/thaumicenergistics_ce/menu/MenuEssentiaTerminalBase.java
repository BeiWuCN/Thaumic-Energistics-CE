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
import thaumicenergistics_ce.integration.ae2.essentia.EssentiaFillHelper;
import thaumicenergistics_ce.menu.slot.ContainerSlot;
import thaumicenergistics_ce.network.EssentiaTerminalReceiver;

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
        slot.setIcon(appeng.util.Icon.BACKGROUND_SINGULARITY);
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

    protected boolean essentiaAccessGranted() {
        return true;
    }

    @Override
    public boolean fillFromNetwork(Player player, int where, Identifier aspectId, boolean wholeStack) {
        if (isClientSide()) {
            return false;
        }
        if (!essentiaAccessGranted()) {
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
            return;
        }

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
                return;
            }
            EssentiaFillHelper.DepositResult left = emptyIntoNetwork(inSlot);
            if (left != null) {
                target.set(left.remaining());
                giveOverflow(player, left);
                broadcastChanges();
            }
            return;
        }

        ItemStack container = containerAt(player, where);
        if (container == null || !EssentiaFillHelper.isSupportedContainer(container)) {
            return;
        }
        EssentiaFillHelper.DepositResult left = emptyIntoNetwork(container);
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
        if (isClientSide() || !essentiaAccessGranted()) {
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
