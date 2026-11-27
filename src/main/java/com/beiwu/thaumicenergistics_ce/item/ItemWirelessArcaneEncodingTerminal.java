package com.beiwu.thaumicenergistics_ce.item;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.ItemMenuHostLocator;
import java.util.function.DoubleSupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.registry.TECEMenuTypes;

/**
 * 无线奥术编码终端：界面、按钮与编码逻辑全部继承面板版，
 * 九格网格、知识核心和选中项存在物品组件里，走哪带哪。
 */
public class ItemWirelessArcaneEncodingTerminal extends WirelessTerminalItem {

    public static final double POWER_CAPACITY = 200_000;

    public ItemWirelessArcaneEncodingTerminal(DoubleSupplier powerCapacity, Item.Properties properties) {
        super(powerCapacity, properties);
    }

    /** 和无线合成终端一样：不要求先绑线缆上的终端，直接开自己的网格。 */
    @Override
    protected boolean checkPreconditions(ItemStack stack) {
        return true;
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return QuantumTerminalSupport.upgrades(stack, (changed, upgrades) -> {});
    }

    @Override
    public MenuType<?> getMenuType() {
        return TECEMenuTypes.WIRELESS_ARCANE_ENCODING_TERMINAL.get();
    }

    @Override
    public @Nullable WirelessTerminalMenuHost<?> getMenuHost(
            Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hitResult) {
        return new WirelessArcaneEncodingTerminalMenuHost(
                this, player, locator, (p, subMenu) -> this.openFromInventory(p, locator, true));
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable TooltipContext context,
            java.util.List<Component> tooltip,
            TooltipFlag flag) {
        var original = new java.util.ArrayList<Component>();
        super.appendHoverText(stack, context, original, flag);
        original.forEach(QuantumTerminalSupport.connectionTooltip(stack, tooltip::add));
    }
}
