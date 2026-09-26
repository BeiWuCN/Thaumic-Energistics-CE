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
import thaumicenergistics_ce.part.PartEssentiaStorageBus;

/**
 * The Essentia Storage Bus as an item, for placing it on a cable.
 */
public class ItemEssentiaStorageBus extends Item implements IPartItem<PartEssentiaStorageBus> {

    public ItemEssentiaStorageBus(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PartHelper.usePartItem(context);
    }

    @Override
    public Class<PartEssentiaStorageBus> getPartClass() {
        return PartEssentiaStorageBus.class;
    }

    @Override
    public PartEssentiaStorageBus createPart() {
        return new PartEssentiaStorageBus(this);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.thaumicenergistics_ce.essentia_storage_bus.desc"));
        tooltip.add(Component.translatable("tooltip.thaumicenergistics_ce.essentia_storage_bus.hint"));
    }
}
