package com.beiwu.thaumicenergistics_ce.network;

import net.minecraft.resources.ResourceLocation;
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
    boolean fillFromNetwork(Player player, int where, ResourceLocation aspectId, boolean wholeStack);

    /**
     * 桶同款：光标空着时，从网络取一个空瓶并灌满一整瓶，放到光标上。
     * 够不够一瓶、有没有空瓶都由服务端重新判；返回有没有交出一瓶。
     */
    boolean takeFilledPhial(Player player, ResourceLocation aspectId);

    void deposit(Player player, int where);
}
