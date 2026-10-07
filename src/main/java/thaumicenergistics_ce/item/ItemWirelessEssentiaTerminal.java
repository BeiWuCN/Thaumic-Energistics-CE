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
 * 无线源质终端：一个能从玩家所在任何位置接入网络的终端。
 * 它在每个方面都是 AE2 自己的无线终端——耗电、电池、范围
 * 检查——因为它就是 AE2 的；只有两处不同：打开哪个菜单，以及它
 * 提供哪些键类型。正是键类型限制让它成为源质终端，否则它会列出
 * 一切。
 */
public class ItemWirelessEssentiaTerminal extends WirelessTerminalItem {

    /**
     * 终端持有的 AE 量：二十万，与 AE2 自己的无线合成终端一致，
     * 因为比玩家已经随身携带的终端更早耗尽会是一种降级。
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
