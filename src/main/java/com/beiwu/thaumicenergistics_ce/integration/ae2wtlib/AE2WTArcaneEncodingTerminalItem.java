package com.beiwu.thaumicenergistics_ce.integration.ae2wtlib;

import appeng.menu.locator.ItemMenuHostLocator;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import com.beiwu.thaumicenergistics_ce.registry.TECEMenuTypes;

/**
 * 装了 AE2WTLib 时的无线奥术编码终端物品，与合成终端同一范式：
 * 继承 {@code ItemWT} 纳入通用终端体系，界面、按钮与编码逻辑全部继承面板版。
 */
public class AE2WTArcaneEncodingTerminalItem extends ItemWT {

    /** 理由同 {@link AE2WTArcaneCraftingTerminalItem#getDescriptionId()}。 */
    private static final String DESCRIPTION_ID = "item.thaumicenergistics_ce.wireless_arcane_encoding_terminal";

    /**
     * 2 格：源质访问卡 1 张 + 能量卡。面板版编码终端一个升级槽也没有，
     * 无线形态至少要让玩家能装进那张卡并把能量卡配上，所以不是 1。
     * 槽数要与 {@code WTDefinition.upgradeCount} 一致：AE2WTLib 用后者当
     * 卡片可装上限（能量卡上限也来自它），两者对不上就会出现「槽有空位但卡报满」。
     */
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
        return TECEMenuTypes.WIRELESS_ARCANE_ENCODING_TERMINAL.get();
    }
}
