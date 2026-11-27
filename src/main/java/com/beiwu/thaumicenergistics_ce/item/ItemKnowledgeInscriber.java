package com.beiwu.thaumicenergistics_ce.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * 知识记录仪的方块物品。这台机器已经被 ME 奥术编码终端取代，
 * 方块本身留着给老存档和配方包用，但 tooltip 里写清楚该换用什么。
 */
public class ItemKnowledgeInscriber extends BlockItem {

    /** 整行红色、斜体、加粗：在灰色说明里最扎眼，翻物品栏时一眼能看见。 */
    private static final Component DEPRECATED = Component
            .translatable("tooltip.thaumicenergistics_ce.knowledge_inscriber.deprecated")
            .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC, ChatFormatting.BOLD);

    public ItemKnowledgeInscriber(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(DEPRECATED);
    }
}
