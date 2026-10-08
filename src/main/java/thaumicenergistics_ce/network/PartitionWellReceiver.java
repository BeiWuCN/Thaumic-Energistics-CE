package thaumicenergistics_ce.network;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * 存储元件工作台各菜单共用的线上约定里分区槽位那一半：要素按 id 设置，
 * 写进 fake 槽位的键只留在这一个屏幕里。
 */
public interface PartitionWellReceiver extends ThEMenuReceiver {

    void setPartitionWell(int well, Identifier aspectId, Player player);
}
