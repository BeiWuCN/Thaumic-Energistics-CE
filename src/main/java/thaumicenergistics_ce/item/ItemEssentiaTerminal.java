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
import thaumicenergistics_ce.part.PartEssentiaTerminal;

/**
 * The Essentia Terminal as an item, for placing it on a cable.
 * Implementing {@link IPartItem} is what makes AE2 treat this as a part: placing, wrenching,
 * the cable's click handling and the model are all driven through it.
 */
public class ItemEssentiaTerminal extends Item implements IPartItem<PartEssentiaTerminal> {

    public ItemEssentiaTerminal(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PartHelper.usePartItem(context);
    }

    @Override
    public Class<PartEssentiaTerminal> getPartClass() {
        return PartEssentiaTerminal.class;
    }

    @Override
    public PartEssentiaTerminal createPart() {
        return new PartEssentiaTerminal(this);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.thaumicenergistics_ce.essentia_terminal.desc"));
    }
}
