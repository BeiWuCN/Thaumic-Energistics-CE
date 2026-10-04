package thaumicenergistics_ce.net;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Distillation Encoder's half of the wire contract: the screen sends instructions, never state, since
 * both sides derive the aspect list from the source item the slot sync already carries.
 */
public interface DistillationEncoderReceiver extends ThEMenuReceiver {

    void selectAspect(int index);

    void encode();

    void applySourceTemplate(ItemStack stack);

    void insertBlankFromInventory(Player player);
}
