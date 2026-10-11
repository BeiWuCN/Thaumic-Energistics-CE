package thaumicenergistics_ce.network;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

public interface PartitionWellReceiver extends ThEMenuReceiver {

    void setPartitionWell(int well, Identifier aspectId, Player player);
}
