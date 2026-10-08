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
 * 无线源质终端：从玩家所在的任何位置接入网络。
 * 耗电、电池、范围检查都是 AE2 自己的，它本来就是 AE2 的无线终端；
 * 只有两处不同：打开哪个菜单，以及提供哪些键类型。
 * 限制键类型才让它成为源质终端，否则什么都会列出来。
 */
public class ItemWirelessEssentiaTerminal extends WirelessTerminalItem {

    /**
     * 终端持有的 AE 量 20 万，与 AE2 自己的无线合成终端一致：
     * 比玩家已经随身带的终端更早耗尽是一种降级。
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
