package thaumicenergistics_ce.arcane;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * An item that can be paired with a placed arcane crafting terminal, so that opening it shows the
 * placed terminal's own workbench rather than a second one. The part calls this rather than naming
 * the item, so a future paired item needs no change there.
 */
public interface ArcaneTerminalLink {

    void pairWith(ItemStack terminal, Level level, BlockPos pos, Direction side);
}
