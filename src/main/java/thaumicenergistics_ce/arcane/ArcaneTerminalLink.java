package thaumicenergistics_ce.arcane;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 可与已放置的奥术合成终端配对的物品，打开它时显示的是那个已放置终端的
 * 工作台，而不是第二个工作台。部件调用这个接口而不是写死物品名，
 * 因此将来新增可配对物品时那里无需改动。
 */
public interface ArcaneTerminalLink {

    void pairWith(ItemStack terminal, Level level, BlockPos pos, Direction side);
}
