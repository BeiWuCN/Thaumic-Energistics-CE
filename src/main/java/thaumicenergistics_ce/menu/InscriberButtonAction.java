package thaumicenergistics_ce.menu;

import net.minecraft.world.entity.player.Player;

/**
 * 菜单按钮的服务端动作：把网格交给机器存储，或让它删除网格
 * 已经存储的内容，然后让客户端一侧跟上。
 * 状态在机器重新解析之后推送，因为一次存储会清空网格。
 */
final class InscriberButtonAction {

    private InscriberButtonAction() {}

    static void run(MenuKnowledgeInscriber menu, Player player, boolean delete) {
        if (menu.inscriber == null) {
            return;
        }
        if (delete) {
            menu.inscriber.deleteStored(player);
        } else {
            menu.inscriber.save(player);
        }
        // 保存会清空网格，所以在推送状态之前，缓存的解析结果必须先跟上。
        menu.inscriber.refreshResolution();
        menu.broadcastChanges();
        menu.updatePreview();
    }
}
