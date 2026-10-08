package thaumicenergistics_ce.network;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * 源质终端那一半的线上约定：移动的是容器内容，不是物品，
 * 两个方向都不以槽位写入上路。
 */
public interface EssentiaTerminalReceiver extends ThEMenuReceiver {

    /**
     * 把一份容器量的 {@code aspectId} 从网络抽进 {@code where} 处的容器，
     * {@code wholeStack} 时抽进手持物品堆；返回有没有东西移动。
     */
    boolean fillFromNetwork(Player player, int where, Identifier aspectId, boolean wholeStack);

    void deposit(Player player, int where);
}
