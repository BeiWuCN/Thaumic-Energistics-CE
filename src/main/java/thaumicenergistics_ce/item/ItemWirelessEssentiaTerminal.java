package thaumicenergistics_ce.item;

import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.ItemMenuHostLocator;
import java.util.function.DoubleSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.registry.TECEMenuTypes;

public class ItemWirelessEssentiaTerminal extends WirelessTerminalItem {

    public static final double POWER_CAPACITY = 200_000;

    public ItemWirelessEssentiaTerminal(DoubleSupplier powerCapacity, Item.Properties properties) {
        super(powerCapacity, properties);
    }

    @Override
    public appeng.api.upgrades.IUpgradeInventory getUpgrades(net.minecraft.world.item.ItemStack stack) {
        return QuantumTerminalSupport.upgrades(stack, (changed, upgrades) ->
                setAEMaxPowerMultiplier(changed, 1 + appeng.api.upgrades.Upgrades.getEnergyCardMultiplier(upgrades)));
    }

    @Override
    public MenuType<?> getMenuType() {
        return TECEMenuTypes.WIRELESS_ESSENTIA_TERMINAL.get();
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<net.minecraft.network.chat.Component> lines,
            net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, display, QuantumTerminalSupport.connectionTooltip(stack, lines), flag);
    }

    @Override
    public @Nullable WirelessTerminalMenuHost<?> getMenuHost(
            Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hitResult) {
        return new WirelessEssentiaTerminalMenuHost(
                this, player, locator, (p, subMenu) -> this.openFromInventory(p, locator, true));
    }
}
