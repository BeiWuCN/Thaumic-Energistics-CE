package thaumicenergistics_ce.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;

public class ItemKnowledgeCore extends Item {

    public ItemKnowledgeCore(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> lines,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        HolderLookup.Provider registries = context.registries();
        if (registries == null) {
            return;
        }
        HandlerKnowledgeCore core = HandlerKnowledgeCore.of(stack, registries);
        if (core == null) {
            return;
        }
        lines.accept(core.describeCapacity().copy().withStyle(ChatFormatting.AQUA));
        core.describePatterns().forEach(lines);
        core.describeUnreadable().forEach(lines);
    }

    public static @Nullable HandlerKnowledgeCore handler(ItemStack stack, Level level) {
        return HandlerKnowledgeCore.of(stack, level.registryAccess());
    }
}
