package thaumicenergistics.item;

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
import thaumicenergistics.part.PartEssentiaExportBus;

/**
 * The Essentia Export Bus as an item, for placing it on a cable.
 *
 * <p>AE2's {@link IPartItem} contract, as with the other parts here: placing, wrenching and the model all
 * come through it.
 */
public class ItemEssentiaExportBus extends Item implements IPartItem<PartEssentiaExportBus> {

    public ItemEssentiaExportBus(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PartHelper.usePartItem(context);
    }

    @Override
    public Class<PartEssentiaExportBus> getPartClass() {
        return PartEssentiaExportBus.class;
    }

    @Override
    public PartEssentiaExportBus createPart() {
        return new PartEssentiaExportBus(this);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.thaumicenergistics.essentia_export_bus.desc"));
        tooltip.add(Component.translatable("tooltip.thaumicenergistics.essentia_export_bus.hint"));
    }
}
