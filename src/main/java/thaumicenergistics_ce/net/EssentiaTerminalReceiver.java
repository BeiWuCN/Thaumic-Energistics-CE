package thaumicenergistics_ce.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Essentia Terminal's half of the wire contract: move container contents rather than items, which is
 * why neither direction travels as a slot write.
 */
public interface EssentiaTerminalReceiver extends ThEMenuReceiver {

    /**
     * Draws one container's worth of {@code aspectId} out of the network into the container at
     * {@code where}; with {@code wholeStack} it keeps going for as many items as the held stack holds and
     * the network can pay for. Returns whether anything moved.
     */
    boolean fillFromNetwork(Player player, int where, ResourceLocation aspectId, boolean wholeStack);

    void deposit(Player player, int where, ItemStack claimed);
}
