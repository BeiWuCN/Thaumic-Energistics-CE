package thaumicenergistics_ce.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 存储元件工作台各菜单共用的线上约定中分区槽位那一半：要素按 id 设置，
 * 因为写进 fake 槽位的键会止步于它被写入的那个屏幕。
 */
public interface PartitionWellReceiver extends ThEMenuReceiver {

    void setPartitionWell(int well, ResourceLocation aspectId, Player player);
}
