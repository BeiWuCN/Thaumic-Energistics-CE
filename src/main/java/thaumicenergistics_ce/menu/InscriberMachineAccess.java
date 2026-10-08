package thaumicenergistics_ce.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;

/**
 * 菜单对它背后机器的读取：两侧共同给解析的 level、槽位持有的核心、方块还在不在。
 * 客户端菜单没有机器，每次读取改为从菜单自己的槽位作答。
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
