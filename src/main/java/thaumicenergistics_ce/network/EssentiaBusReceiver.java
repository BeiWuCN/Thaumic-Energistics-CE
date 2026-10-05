package thaumicenergistics_ce.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * The config-slot half of the wire contract shared by the essentia bus menus: an aspect is set by id,
 * because a non-item key written through a slot would be dropped before it reached the filter.
 */
public interface EssentiaBusReceiver extends ThEMenuReceiver {

    void setConfigAspect(int configSlot, ResourceLocation aspectId, Player player);

    String configFor(int configSlot);
}
