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
 * <ul>
 * <li>AE2's own wireless terminal in every respect - the power drain, the battery, the range check -
 * because it <em>is</em> AE2's; two things change: which menu opens and which key types it offers.
 * <li>The key-type restriction is what makes it an essentia terminal: otherwise it lists everything.
 * </ul>
 */
public class ItemWirelessEssentiaTerminal extends WirelessTerminalItem {

    /**
     * How much AE the terminal holds: two hundred thousand, matching AE2's own wireless crafting terminal,
     * because one that ran flat sooner than the terminal players already carry would be a downgrade.
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
