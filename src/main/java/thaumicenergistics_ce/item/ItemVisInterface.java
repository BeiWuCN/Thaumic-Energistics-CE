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
import thaumicenergistics_ce.part.PartVisInterface;

/**
 * The Vis Interface as an item, for fitting it to a cable.
 *
 * <p>Same shape as this mod's other part items: implementing {@link IPartItem} directly is what AE2 checks
 * for, and doing it here keeps placement, tooltip and factory together.
 */
public class ItemVisInterface extends Item implements IPartItem<PartVisInterface> {

    public ItemVisInterface(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PartHelper.usePartItem(context);
    }

    @Override
    public Class<PartVisInterface> getPartClass() {
        return PartVisInterface.class;
    }

    @Override
    public PartVisInterface createPart() {
        return new PartVisInterface(this);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.thaumicenergistics_ce.vis_interface.desc"));
        tooltip.add(Component.translatable("tooltip.thaumicenergistics_ce.vis_interface.hint"));
    }
}
