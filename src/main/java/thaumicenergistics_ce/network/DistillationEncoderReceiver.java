package thaumicenergistics_ce.network;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 蒸馏编码器那一半的线上约定：屏幕发送的是指令而非状态，
 * 因为两侧都从槽位同步已经携带的源物品推导出要素列表。
 */
public interface DistillationEncoderReceiver extends ThEMenuReceiver {

    void selectAspect(int index);

    void encode();

    void applySourceTemplate(ItemStack stack);

    void insertBlankFromInventory(Player player);
}
