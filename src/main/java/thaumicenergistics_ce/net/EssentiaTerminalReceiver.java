package thaumicenergistics_ce.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Essentia Terminal's half of the wire contract: move container contents rather than items, which is
 * why neither direction travels as a slot write.
 */
public interface EssentiaTerminalReceiver extends ThEMenuReceiver {

    boolean fillFromNetwork(Player player, int where, ResourceLocation aspectId);

    void deposit(Player player, int where, ItemStack claimed);
}
