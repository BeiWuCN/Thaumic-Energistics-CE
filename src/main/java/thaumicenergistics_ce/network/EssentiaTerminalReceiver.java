package thaumicenergistics_ce.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 源质终端那一半的线上约定：移动的是容器内容而不是物品，
 * 这就是两个方向都不以槽位写入上路的原因。
 */
public interface EssentiaTerminalReceiver extends ThEMenuReceiver {

    /**
     * 把一份容器量的 {@code aspectId} 从网络抽进 {@code where} 处的容器，
     * {@code wholeStack} 时则抽进手持物品堆；返回是否有东西移动。
     */
    boolean fillFromNetwork(Player player, int where, ResourceLocation aspectId, boolean wholeStack);

    void deposit(Player player, int where);
}
