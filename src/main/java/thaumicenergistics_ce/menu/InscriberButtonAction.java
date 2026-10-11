package thaumicenergistics_ce.menu;

import net.minecraft.world.entity.player.Player;

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
        menu.inscriber.refreshResolution();
        menu.broadcastChanges();
        menu.updatePreview();
    }
}
