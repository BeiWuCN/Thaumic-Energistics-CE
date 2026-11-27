package com.beiwu.thaumicenergistics_ce.integration.ae2wtlib;

import appeng.menu.locator.ItemMenuHostLocator;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import com.beiwu.thaumicenergistics_ce.registry.TECEMenuTypes;

public class AE2WTEssentiaTerminalItem extends ItemWT {

    private static final String DESCRIPTION_ID = "item.thaumicenergistics_ce.wireless_essentia_terminal";

    private static final int UPGRADE_SLOTS = 2;

    @Override
    public String getDescriptionId() {
        return DESCRIPTION_ID;
    }

    @Override
    public appeng.api.upgrades.IUpgradeInventory getUpgrades(ItemStack stack) {
        return appeng.api.upgrades.UpgradeInventories.forItem(stack, UPGRADE_SLOTS, (changed, upgrades) ->
                setAEMaxPowerMultiplier(changed,
                        1 + appeng.api.upgrades.Upgrades.getEnergyCardMultiplier(upgrades)));
    }

    @Override
    public MenuType<?> getMenuType(ItemMenuHostLocator locator, Player player) {
        return TECEMenuTypes.WIRELESS_ESSENTIA_TERMINAL.get();
    }
}
