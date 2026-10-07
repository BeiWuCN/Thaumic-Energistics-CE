package thaumicenergistics_ce.item;

import appeng.api.storage.ILinkStatus;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ArcaneTerminalHost;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 无线奥术合成终端背后的菜单宿主：AE2 的无线终端宿主，再加上
 * AE2 完全没有概念的一样东西——这个物品所配对的已放置终端。实现
 * {@link ArcaneTerminalHost} 才使菜单构建已放置终端自己的网格、
 * 法杖槽与水晶，于是两个界面显示的是同一个状态而非两份副本。
 */
public class WirelessArcaneCraftingTerminalMenuHost
        extends WirelessTerminalMenuHost<ItemWirelessArcaneCraftingTerminal> implements ArcaneTerminalHost {

    public WirelessArcaneCraftingTerminalMenuHost(
            ItemWirelessArcaneCraftingTerminal item,
            Player player,
            ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
    }

    @Override
    public @Nullable PartArcaneCraftingTerminal arcaneTerminal() {
        return ItemWirelessArcaneCraftingTerminal.pairedTerminal(getPlayer().level(), getItemStack());
    }

    /**
     * 一个已在网络上、但没有配对任何已放置终端的终端，会在界面已经
     * 显示 "not connected" 的地方说明这一点：这件物品正是 AE2 自己的链接状态无法知晓的。
     */
    @Override
    public ILinkStatus getLinkStatus() {
        ILinkStatus status = super.getLinkStatus();
        if (!status.connected() || arcaneTerminal() != null) {
            return status;
        }
        return ILinkStatus.ofDisconnected(Component.translatable(
                "gui.thaumicenergistics_ce.wireless_arcane_crafting_terminal.not_bound"));
    }
}
