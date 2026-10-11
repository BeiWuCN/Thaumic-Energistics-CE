package thaumicenergistics_ce.item;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.features.Locatables;
import appeng.api.ids.AEComponents;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.storage.ILinkStatus;
import appeng.api.storage.MEStorage;
import appeng.api.storage.SupplierStorage;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.contents.StackDependentSupplier;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.me.storage.NullInventory;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.SupplierInternalInventory;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.registry.ModDataComponents;

public class QuantumWirelessTerminalMenuHost<T extends WirelessTerminalItem> extends WirelessTerminalMenuHost<T> {
    private @Nullable IGridNode quantumNode;
    private final MEStorage quantumStorage;
    private final InternalInventory singularityInventory;

    public QuantumWirelessTerminalMenuHost(T item, Player player, ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
        quantumStorage = new SupplierStorage(() -> {
            var node = getActionableNode();
            return node == null ? NullInventory.of() : node.getGrid().getStorageService().getInventory();
        });
        singularityInventory = new SupplierInternalInventory<>(new StackDependentSupplier<>(this::getItemStack, stack -> {
            var inv = new AppEngInternalInventory(new InternalInventoryHost() {
                @Override
                public void saveChangedInventory(AppEngInternalInventory inventory) {
                    stack.set(ModDataComponents.QUANTUM_SINGULARITY.get(), inventory.toItemContainerContents());
                }
                @Override
                public boolean isClientSide() { return player.level().isClientSide(); }
            }, 1, 1);
            inv.fromItemContainerContents(stack.getOrDefault(ModDataComponents.QUANTUM_SINGULARITY.get(), ItemContainerContents.EMPTY));
            inv.setFilter(new appeng.util.inv.filter.IAEItemFilter() {
                @Override
                public boolean allowInsert(InternalInventory inventory, int slot, net.minecraft.world.item.ItemStack candidate) {
                    return QuantumTerminalSupport.validSingularity(candidate);
                }
            });
            return inv;
        }));
    }

    public InternalInventory singularityInventory() { return singularityInventory; }

    public boolean quantumConnected() { return quantumNode != null; }

    @Override
    protected void updateConnectedAccessPoint() {
        super.updateConnectedAccessPoint();
        quantumNode = null;
        if (!QuantumTerminalSupport.enabled() || getPlayer().level().isClientSide()
                || !getUpgrades().isInstalled(QuantumTerminalSupport.card())) return;
        var singularity = QuantumTerminalSupport.singularity(getItemStack());
        if (!QuantumTerminalSupport.validSingularity(singularity)) return;
        long frequency = singularity.get(AEComponents.ENTANGLED_SINGULARITY_ID);
        var bridge = Locatables.quantumNetworkBridges().get(getPlayer().level(), frequency);
        if (bridge == null) bridge = Locatables.quantumNetworkBridges().get(getPlayer().level(), -frequency);
        if (bridge == null) return;
        var node = bridge.getActionableNode();
        if (node == null || !node.getGrid().getEnergyService().isNetworkPowered()) return;
        var linked = getItem().getLinkedGrid(getItemStack(), getPlayer().level(), null);
        if (linked != null && linked != node.getGrid()) return;
        quantumNode = node;
    }

    @Override
    public @Nullable IGridNode getActionableNode() {
        return quantumNode != null ? quantumNode : super.getActionableNode();
    }

    @Override
    public MEStorage getInventory() {
        return quantumStorage == null ? super.getInventory() : quantumStorage;
    }

    @Override
    public ILinkStatus getLinkStatus() {
        if (quantumNode == null) return super.getLinkStatus();
        return consumeIdlePower(Actionable.SIMULATE) ? ILinkStatus.ofConnected()
                : ILinkStatus.ofDisconnected(Component.translatable("gui.ae2.OutOfPower"));
    }

    @Override
    protected double getPowerDrainPerTick() {
        return quantumNode == null ? super.getPowerDrainPerTick() : 22.5;
    }

    @Override
    public boolean consumeIdlePower(Actionable action) {
        if (quantumNode != null && action == Actionable.SIMULATE) {
            var energy = quantumNode.getGrid().getEnergyService();
            double safe = Math.max(0, energy.getStoredPower() - energy.getMaxStoredPower() / 2);
            double missing = getItem().getAEMaxPower(getItemStack()) - getItem().getAECurrentPower(getItemStack());
            if (safe > 0 && missing > 0) {
                double paid = energy.extractAEPower(Math.min(safe, missing), Actionable.MODULATE, PowerMultiplier.ONE);
                getItem().injectAEPower(getItemStack(), paid, Actionable.MODULATE);
            }
        }
        return super.consumeIdlePower(action);
    }
}
