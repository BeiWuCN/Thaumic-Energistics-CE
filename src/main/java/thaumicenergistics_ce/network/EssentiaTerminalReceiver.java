package thaumicenergistics_ce.network;

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
     * {@code where}, or the held stack with {@code wholeStack}; says whether anything moved.
     */
    boolean fillFromNetwork(Player player, int where, ResourceLocation aspectId, boolean wholeStack);

    void deposit(Player player, int where, ItemStack claimed);
}
