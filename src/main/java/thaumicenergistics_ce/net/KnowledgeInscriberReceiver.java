package thaumicenergistics_ce.net;

import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Knowledge Inscriber's half of the wire contract. The grid's position inside the menu's slot list is
 * the menu's fact, not the packet's: the payload carries a container index and asks the receiver to
 * translate it, so the two cannot drift apart.
 */
public interface KnowledgeInscriberReceiver extends ThEMenuReceiver {

    int gridSlotStart();

    int gridSlotCount();

    void setGridCell(Player player, int cell, ItemStack stack);

    void applyGridFill(Player player, List<ItemStack> cells, int count);
}
