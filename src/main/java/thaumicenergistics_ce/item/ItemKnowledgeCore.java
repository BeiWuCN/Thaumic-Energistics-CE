package thaumicenergistics_ce.item;

import java.util.List;
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

/**
 * 知识核心：奥术组装机能执行的奥术配方的可携带清单。
 * 物品本身是普通的 {@link Item}，内容存在由 {@link HandlerKnowledgeCore} 写入的自定义数据里，
 * 这个类只在 tooltip 中报告它们。
 */
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
        // 26.1 里 TooltipContext 没了 level()：核心需要的本来就是注册表。
        HolderLookup.Provider registries = context.registries();
        if (registries == null) {
            return;
        }
        HandlerKnowledgeCore core = HandlerKnowledgeCore.of(stack, registries);
        if (core == null) {
            return;
        }
        lines.accept(Component.literal(core.describeCapacity().getString()).withStyle(ChatFormatting.AQUA));
        core.describePatterns().forEach(lines);
        core.describeUnreadable().forEach(lines);
    }

    public static @Nullable HandlerKnowledgeCore handler(ItemStack stack, Level level) {
        return HandlerKnowledgeCore.of(stack, level.registryAccess());
    }
}
