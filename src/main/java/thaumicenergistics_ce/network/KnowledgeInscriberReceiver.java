package thaumicenergistics_ce.network;

import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 知识铭刻机那一半的线上约定。网格在菜单槽位列表中的位置是
 * 菜单的事实而非数据包的事实：载荷携带容器索引并请求接收者
 * 换算它，因此两者不会漂开。
 */
public interface KnowledgeInscriberReceiver extends ThEMenuReceiver {

    int gridSlotStart();

    int gridSlotCount();

    void setGridCell(Player player, int cell, ItemStack stack);

    void applyGridFill(Player player, List<ItemStack> cells, int count);
}
