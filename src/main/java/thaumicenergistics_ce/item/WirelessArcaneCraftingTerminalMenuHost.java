package thaumicenergistics_ce.item;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.storage.ILinkStatus;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import java.util.function.BiConsumer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ArcaneTerminalHost;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import thaumicenergistics_ce.util.ThELog;

/**
 * 无线奥术合成终端背后的菜单宿主：AE2 的无线终端宿主，
 * 再加上 AE2 完全没有概念的东西：这个物品所配对的已放置终端。
 * 实现 {@link ArcaneTerminalHost} 才让菜单构建已放置终端自己的网格、
 * 法杖槽与水晶，于是两个界面显示的是同一个状态，不是两份副本。
 *
 * <p><b>刻意不改写 {@code getLinkStatus()} 的内容。</b>这里曾经在「已连上网络但没配对终端」时
 * 返回 {@code ILinkStatus.ofDisconnected("未绑定到奥术合成终端")}，只想让界面把这件事说出来——
 * 那等于把说明文字当成了开关：AE2 的 {@code MEStorageMenu.canInteractWithGrid()} 就是
 * {@code getLinkStatus().connected()}，而 {@code handleInteraction} 开头第一句是
 * 「不能交互网格就直接 return」。于是在没配对、或配对的那一块没加载（
 * {@code pairedTerminal} 里的 {@code !level.isLoaded(pos)}）时，网络里的东西一件也拿不出来、
 * 产物格点了也没反应，界面却照旧把物品列表画出来——就是玩家看到的「看得见、动不了」。
 * 而且列表还在恰恰证明 AE2 自己的链接是好的：{@code getStorageFromStack} 在没连上时
 * 返回的是空存储 {@code NullInventory}，不是旧缓存。
 *
 * <p>要再提示「未绑定」，走界面或工具提示，不要动链接状态；
 * {@code gui.thaumicenergistics_ce.wireless_arcane_crafting_terminal.not_bound} 这个键
 * 仍留在语言文件里备用。
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

    /**
     * 只观察，不改写：原样返回 AE2 给的链接状态，只在它变化时往日志里写一行。
     * 无线的取物/放物由 {@code MEStorageMenu.canInteractWithGrid()}（就是它）把门，
     * 而 {@code statusDescription()} 是 AE2 自己的说法：没电、没连上、还是超出范围。
     * 界面上未必看得见这行字，日志里一定有，省得靠猜。
     */
    private String lastLinkStatus = "";

    /** 上一次记过的「这块电池是不是空的」；只在翻转时写一行，免得每刻刷屏。 */
    private boolean lastChargeWasEmpty = true;

    /** 界面打开后先写一份底数，日志里没有这一行就没法判断。 */
    private boolean chargeLogged;

    @Override
    public ILinkStatus getLinkStatus() {
        ILinkStatus status = super.getLinkStatus();
        String text = status.connected() ? "connected" : status.statusDescription().getString();
        if (!text.equals(this.lastLinkStatus)) {
            this.lastLinkStatus = text;
            ThELog.LOG.info("[无线奥术合成终端] AE2 链接状态：{}", text);
        }
        logCharge();
        return status;
    }

    /**
     * 无线的取物、放物、倒源质都走 AE2 的 {@code StorageHelper.poweredInsert/poweredExtraction}，
     * 那两条路要的电来自 {@code MEStorageMenu.energySource}；无线终端的宿主自己就是
     * {@code IEnergySource}（{@code IPortableTerminal} 继承它），所以这份电就是<b>这个物品的电池</b>，
     * 与所连网络里有多少电无关。
     *
     * <p>创造模式只免掉「待机耗电」（{@code ItemMenuHost.consumeIdlePower} 第一句就是它），
     * 不免取物放物的电。电池空了就会正好出现这四个症状：列表看得见、物品拿不出来也放不进去、
     * 源质能取不能倒——取源质走的是不经电力的 {@code storage.extract}。
     * 这里只在「有电／没电」翻转时报一次。
     */
    private void logCharge() {
        if (getPlayer().level().isClientSide()) {
            return;
        }
        double available = extractAEPower(1000, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        boolean empty = available < 1.0;
        if (!this.chargeLogged || empty != this.lastChargeWasEmpty) {
            this.chargeLogged = true;
            this.lastChargeWasEmpty = empty;
            ThELog.LOG.info("[无线奥术合成终端] 终端电池：可用 {} AE，每刻耗电 {}{}",
                    available, getPowerDrainPerTick(),
                    empty ? "——空电池：取物/放物/倒源质都会静默失败，得先把终端放进 AE2 充电器" : "");
        }
    }

    @Override
    public @Nullable PartArcaneCraftingTerminal arcaneTerminal() {
        return ItemWirelessArcaneCraftingTerminal.pairedTerminal(getPlayer().level(), getItemStack());
    }

}
