package thaumicenergistics_ce.network;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

public interface EssentiaTerminalReceiver extends ThEMenuReceiver {

    boolean fillFromNetwork(Player player, int where, Identifier aspectId, boolean wholeStack);

    void deposit(Player player, int where);
}
