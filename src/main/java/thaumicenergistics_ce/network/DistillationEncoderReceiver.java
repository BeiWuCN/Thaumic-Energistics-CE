package thaumicenergistics_ce.network;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface DistillationEncoderReceiver extends ThEMenuReceiver {

    void selectAspect(int index);

    void encode();

    void applySourceTemplate(ItemStack stack);

    void insertBlankFromInventory(Player player);
}
