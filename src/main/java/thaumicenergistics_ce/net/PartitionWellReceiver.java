package thaumicenergistics_ce.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * The partition-well half of the wire contract shared by the cell workbench menus: an aspect is set by id,
 * because a key written into a fake slot would stop at the screen it was written on.
 */
public interface PartitionWellReceiver extends ThEMenuReceiver {

    void setPartitionWell(int well, ResourceLocation aspectId, Player player);
}
