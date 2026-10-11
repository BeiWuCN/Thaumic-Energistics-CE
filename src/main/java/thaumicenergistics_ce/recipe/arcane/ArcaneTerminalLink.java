package thaumicenergistics_ce.recipe.arcane;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public interface ArcaneTerminalLink {

    void pairWith(ItemStack terminal, Level level, BlockPos pos, Direction side);
}
