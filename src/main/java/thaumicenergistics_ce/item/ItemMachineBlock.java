package thaumicenergistics_ce.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * A machine's block item, carrying the two tooltip lines the rest of this mod's machines have.
 * <ul>
 * <li>Exists because {@code registerSimpleBlockItem} makes a plain {@link BlockItem}, with no hook for one.
 * <li>Description and placement hint are separate lines: what the block is for, and where to put it.
 * <li>Keys are passed in, so one item covers every machine; either may be {@code null} for a plain tooltip.
 * </ul>
 */
public class ItemMachineBlock extends BlockItem {

    private final @Nullable String descriptionKey;
    private final @Nullable String hintKey;

    public ItemMachineBlock(
            Block block, Properties properties, @Nullable String descriptionKey, @Nullable String hintKey) {
        super(block, properties);
        this.descriptionKey = descriptionKey;
        this.hintKey = hintKey;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (descriptionKey != null) {
            tooltip.add(Component.translatable(descriptionKey));
        }
        if (hintKey != null) {
            tooltip.add(Component.translatable(hintKey));
        }
    }
}
