package thaumicenergistics_ce.menu;

import net.minecraft.world.entity.player.Player;

/**
 * 菜单按钮的服务端动作：把网格交给机器存储，或让它删掉已存储的内容，再让客户端跟上。
 * 状态在机器重新解析之后推送：一次存储会清空网格。
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
        // 保存会清空网格；推送状态之前要先刷新缓存的解析结果。
        menu.inscriber.refreshResolution();
        menu.broadcastChanges();
        menu.updatePreview();
    }
}
