package thaumicenergistics.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * A machine's block item, with the two tooltip lines the rest of this mod's machines carry.
 *
 * <p>Exists because {@code registerSimpleBlockItem} makes a plain {@link BlockItem}, which has no hook for
 * a description - and a machine whose only explanation is its name is a machine a player has to guess at.
 * The description and the placement hint are separate lines rather than one paragraph: the first says what
 * the block is for, the second says where to put it, and they answer different questions.
 *
 * <p>Keys are supplied by the caller so the same class covers every machine, and both lines are optional -
 * a block with nothing useful to add passes {@code null} and gets a plain tooltip.
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
