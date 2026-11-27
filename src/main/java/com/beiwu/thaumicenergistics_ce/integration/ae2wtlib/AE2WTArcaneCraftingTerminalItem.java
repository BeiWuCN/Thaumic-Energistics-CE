package com.beiwu.thaumicenergistics_ce.integration.ae2wtlib;

import appeng.menu.locator.ItemMenuHostLocator;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import com.beiwu.thaumicenergistics_ce.registry.TECEMenuTypes;

/**
 * 装了 AE2WTLib 时的无线奥术合成终端物品：继承 {@code ItemWT} 才进得了通用终端
 * （{@code WTDefinition} 的物品字段类型是 ItemWT，无绕道）。
 *
 * <p>只在 {@link AE2WTIntegration} 的工厂里实例化，普通注册类不出现本类名，
 * 没装 AE2WTLib 时这份字节码从不被加载。
 */
public class AE2WTArcaneCraftingTerminalItem extends ItemWT {

    /**
     * {@code ItemWT} 的无参构造自建 {@code Item.Properties}，拿不到注册时注入的物品 id，
     * 描述键会停在 {@code item.minecraft.air}，所有界面都把它显示成空气。所以显式给键。
     */
    private static final String DESCRIPTION_ID = "item.thaumicenergistics_ce.wireless_arcane_crafting_terminal";

    /**
     * 升级槽数 2，与基类默认值一样，但这里是特意写出来的：终端能装的卡有
     * 量子桥卡 / 源质访问卡 / 魔力连接卡三张，而只给两个槽——就是要玩家取舍。
     * 这个 2 必须与 {@code WTDefinition.upgradeCount(2)} 对齐：后者是能量卡与
     * 源质访问卡的可装上限（由 AE2WTLib 的 UpgradeHelper 登记），两者无关但该一致。
     */
    private static final int UPGRADE_SLOTS = 2;

    @Override
    public String getDescriptionId() {
        return DESCRIPTION_ID;
    }

    /** 能量卡上限随槽位重算，与 AE2 无线终端基类同一做法。 */
    @Override
    public appeng.api.upgrades.IUpgradeInventory getUpgrades(ItemStack stack) {
        return appeng.api.upgrades.UpgradeInventories.forItem(stack, UPGRADE_SLOTS, (changed, upgrades) ->
                setAEMaxPowerMultiplier(changed,
                        1 + appeng.api.upgrades.Upgrades.getEnergyCardMultiplier(upgrades)));
    }

    /**
     * 通用终端切到奥术模式时打开这个菜单。菜单类型与独立实现共用同一个，
     * 因为两者的宿主都实现了 {@code WirelessArcaneCraftingHost}，菜单只看这个接口。
     */
    @Override
    public MenuType<?> getMenuType(ItemMenuHostLocator locator, Player player) {
        return TECEMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get();
    }
}
