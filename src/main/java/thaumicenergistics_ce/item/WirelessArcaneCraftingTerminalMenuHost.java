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
import thaumicenergistics_ce.recipe.arcane.ArcaneTerminalHost;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import thaumicenergistics_ce.util.ThELog;

public class WirelessArcaneCraftingTerminalMenuHost
        extends QuantumWirelessTerminalMenuHost<ItemWirelessArcaneCraftingTerminal> implements ArcaneTerminalHost {

    public WirelessArcaneCraftingTerminalMenuHost(
            ItemWirelessArcaneCraftingTerminal item,
            Player player,
            ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
    }

    private String lastLinkStatus = "";

    private boolean lastChargeWasEmpty = true;

    private boolean chargeLogged;

    @Override
    public ILinkStatus getLinkStatus() {
        ILinkStatus status = super.getLinkStatus();
        if (status.connected() && arcaneTerminal() == null) {
            status = ILinkStatus.ofDisconnected(net.minecraft.network.chat.Component.translatable(
                    "gui.thaumicenergistics_ce.wireless_arcane_crafting_terminal.not_bound"));
        }
        String text = status.connected() ? "connected" : status.statusDescription().getString();
        if (!text.equals(this.lastLinkStatus)) {
            this.lastLinkStatus = text;
            ThELog.LOG.info("[无线奥术合成终端] AE2 链接状态：{}", text);
        }
        logCharge();
        return status;
    }

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
        var part = ItemWirelessArcaneCraftingTerminal.pairedTerminal(getPlayer().level(), getItemStack(), quantumConnected());
        var connected = getActionableNode();
        var bound = part == null ? null : part.getGridNode();
        return connected != null && bound != null && connected.getGrid() == bound.getGrid() ? part : null;
    }

}
