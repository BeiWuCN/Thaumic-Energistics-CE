package thaumicenergistics_ce.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Thaumic Energistics 方块实体的基类；提供 {@link MenuProvider} 约定，
 * 使方块打开菜单时不必重复类型转换，并集中处理菜单标题。
 */
public abstract class ThEBaseBlockEntity extends BlockEntity implements MenuProvider {

    protected ThEBaseBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public abstract AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player);
}
