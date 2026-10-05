package thaumicenergistics_ce.menu;

import net.minecraft.world.entity.player.Player;

/**
 * The menu button's server action: hand the grid to the machine to store, or ask it to delete what the
 * grid already stored, then let the client side catch up.
 * The status is pushed after the machine has re-resolved, since a store clears the grid.
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
        // A save clears the grid, so the cached resolution must catch up before the status is pushed.
        menu.inscriber.refreshResolution();
        menu.broadcastChanges();
        menu.updatePreview();
    }
}
