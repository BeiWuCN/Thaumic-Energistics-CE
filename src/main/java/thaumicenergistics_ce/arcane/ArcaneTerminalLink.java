package thaumicenergistics_ce.arcane;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 可与已放置的奥术合成终端配对的物品，打开它显示的是那个终端的工作台。
 * 部件调用这个接口，不写死物品名，以后新增可配对的物品不用改那边。
 */
public interface ArcaneTerminalLink {

    void pairWith(ItemStack terminal, Level level, BlockPos pos, Direction side);
}
