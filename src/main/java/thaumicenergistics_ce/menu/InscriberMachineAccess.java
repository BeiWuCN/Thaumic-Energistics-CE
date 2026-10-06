package thaumicenergistics_ce.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;

/**
 * The menu's reads of the machine behind it: the level both sides resolve against, the core the slot
 * holds, and whether the block is still there to be used.
 * A client menu has no machine, so each read answers from the menu's own slots instead.
 */
final class InscriberMachineAccess {

    private InscriberMachineAccess() {}

    static @Nullable Level level(MenuKnowledgeInscriber menu) {
        if (menu.inscriber != null) {
            return menu.inscriber.getLevel();
        }
        return menu.playerInventory.player.level();
    }

    static @Nullable HandlerKnowledgeCore handler(MenuKnowledgeInscriber menu) {
        Level level = level(menu);
        if (level == null) {
            return null;
        }
        ItemStack core = slotStack(menu, MenuKnowledgeInscriber.IDX_CORE);
        return HandlerKnowledgeCore.of(core, level.registryAccess());
    }

    static ItemStack slotStack(MenuKnowledgeInscriber menu, int index) {
        if (index < 0 || index >= menu.slots.size()) {
            return ItemStack.EMPTY;
        }
        return menu.slots.get(index).getItem();
    }

    static boolean stillValid(MenuKnowledgeInscriber menu, Player player) {
        if (menu.inscriber == null) {
            return true;
        }
        var level = menu.inscriber.getLevel();
        var pos = menu.inscriber.getBlockPos();
        return level != null
                && level.getBlockEntity(pos) == menu.inscriber
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
