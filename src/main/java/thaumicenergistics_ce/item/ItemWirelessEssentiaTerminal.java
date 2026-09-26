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
import thaumicenergistics_ce.init.ModMenuTypes;

/**
 * The Wireless Essentia Terminal: a terminal that reaches the network from anywhere the player is.
 *
 * <p>AE2's own wireless terminal in every respect - linking to a network, the power drain, the battery,
 * the range check, returning from a submenu - because it <em>is</em> AE2's: this extends
 * {@link WirelessTerminalItem} and changes two things. Which menu opens, and which key types that menu
 * offers.
 *
 * <p>The key-type restriction is what makes it an essentia terminal rather than a second ME terminal.
 * Without it a player would open a wireless terminal listing everything the network holds, which is AE2's
 * item, not this one. See {@link WirelessEssentiaTerminalMenuHost}.
 */
public class ItemWirelessEssentiaTerminal extends WirelessTerminalItem {

    /**
     * How much AE the terminal holds.
     *
     * <p>Two hundred thousand, matching AE2's own wireless crafting terminal: a wireless terminal that ran
     * flat sooner than the one players already carry would be a downgrade rather than a variant.
     */
    public static final double POWER_CAPACITY = 200_000;

    public ItemWirelessEssentiaTerminal(DoubleSupplier powerCapacity, Item.Properties properties) {
        super(powerCapacity, properties);
    }

    @Override
    public MenuType<?> getMenuType() {
        return ModMenuTypes.WIRELESS_ESSENTIA_TERMINAL.get();
    }

    @Override
    public @Nullable WirelessTerminalMenuHost<?> getMenuHost(
            Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hitResult) {
        return new WirelessEssentiaTerminalMenuHost(
                this, player, locator, (p, subMenu) -> this.openFromInventory(p, locator, true));
    }
}
