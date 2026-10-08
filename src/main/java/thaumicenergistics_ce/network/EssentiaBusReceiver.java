package thaumicenergistics_ce.network;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * 源质总线各菜单共用的线上契约里管配置槽位的那一半：要素按 id 设置，
 * 因为经槽位写入的非物品键，到不了过滤器就被丢掉。
 */
public interface EssentiaBusReceiver extends ThEMenuReceiver {

    void setConfigAspect(int configSlot, Identifier aspectId, Player player);

    String configFor(int configSlot);
}
