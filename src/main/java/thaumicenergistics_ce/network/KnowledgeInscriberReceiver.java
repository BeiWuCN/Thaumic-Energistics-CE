package thaumicenergistics_ce.network;

import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface KnowledgeInscriberReceiver extends ThEMenuReceiver {

    int gridSlotStart();

    int gridSlotCount();

    void setGridCell(Player player, int cell, ItemStack stack);

    void applyGridFill(Player player, List<ItemStack> cells, int count);
}
