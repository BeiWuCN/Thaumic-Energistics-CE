package thaumicenergistics_ce.network;

import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 知识铭刻机那一半的线上约定。网格在菜单槽位列表里的位置是菜单的事实，
 * 不是数据包的事实：载荷携带容器索引，由接收者换算，两者不会漂开。
 */
public interface KnowledgeInscriberReceiver extends ThEMenuReceiver {

    int gridSlotStart();

    int gridSlotCount();

    void setGridCell(Player player, int cell, ItemStack stack);

    void applyGridFill(Player player, List<ItemStack> cells, int count);
}
