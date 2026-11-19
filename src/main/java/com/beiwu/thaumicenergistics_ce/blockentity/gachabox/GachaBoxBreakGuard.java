package com.beiwu.thaumicenergistics_ce.blockentity.gachabox;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.block.BlockGachaBox;

/**
 * 让箱子里的脑跟着它绑定的玩家：否则破坏箱子会把脑掉出来，谁都能捡。
 * 创造模式不受限制，操作员清理机器时不用敲命令。
 */
@EventBusSubscriber(modid = ThEIds.MODID)
public final class GachaBoxBreakGuard {

    private GachaBoxBreakGuard() {}

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        boolean upper = event.getState().getBlock() instanceof com.beiwu.thaumicenergistics_ce.block.BlockGachaBoxSpace;
        if (!(event.getState().getBlock() instanceof BlockGachaBox) && !upper) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isCreative()) {
            return;
        }
        if (event.getLevel().getBlockEntity(upper ? event.getPos().below() : event.getPos()) instanceof BlockEntityGachaBox box
                && box.hasJar()
                && !box.mayTakeBrain(player)) {
            event.setCanceled(true);
            player.displayClientMessage(
                    Component.translatable(
                            "block.thaumicenergistics_ce.gacha_box.bound_to_other",
                            box.ownerName() == null ? "" : box.ownerName()),
                    true);
        }
    }
}
