package thaumicenergistics_ce.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;

/**
 * A knowledge core: the portable list of arcane recipes an Arcane Assembler can perform.
 *
 * <p>The item is a plain {@link Item}; everything it holds lives in its custom data, written by
 * {@link HandlerKnowledgeCore}. This class only reports that contents in the tooltip.
 */
public class ItemKnowledgeCore extends Item {

    public ItemKnowledgeCore(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        Level level = context.level();
        if (level == null) {
            return;
        }
        HandlerKnowledgeCore core = HandlerKnowledgeCore.of(stack, level.registryAccess());
        if (core == null) {
            return;
        }
        lines.add(Component.literal(core.describeCapacity().getString()).withStyle(ChatFormatting.AQUA));
        lines.addAll(core.describePatterns());
        lines.addAll(core.describeUnreadable());
    }

    /** Convenience for callers that only have a stack. */
    public static @Nullable HandlerKnowledgeCore handler(ItemStack stack, Level level) {
        return HandlerKnowledgeCore.of(stack, level.registryAccess());
    }
}
