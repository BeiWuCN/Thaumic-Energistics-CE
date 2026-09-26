package thaumicenergistics_ce.item;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * The Arcane Crafting Terminal as an item, for placing it on a cable.
 *
 * <p>Follows the same shape as this mod's other part items rather than extending AE2's {@code PartItem}:
 * implementing {@link IPartItem} directly is what AE2 actually checks for, and doing it here keeps the
 * placement path, the tooltip and the factory in one place, the way the Essentia Terminal does it.
 */
public class ItemArcaneCraftingTerminal extends Item implements IPartItem<PartArcaneCraftingTerminal> {

    public ItemArcaneCraftingTerminal(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PartHelper.usePartItem(context);
    }

    @Override
    public Class<PartArcaneCraftingTerminal> getPartClass() {
        return PartArcaneCraftingTerminal.class;
    }

    @Override
    public PartArcaneCraftingTerminal createPart() {
        return new PartArcaneCraftingTerminal(this);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.thaumicenergistics_ce.arcane_crafting_terminal.desc"));
        tooltip.add(Component.translatable("tooltip.thaumicenergistics_ce.arcane_crafting_terminal.hint"));
    }
}
