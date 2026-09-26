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
import thaumicenergistics_ce.part.PartEssentiaImportBus;

/**
 * The Essentia Import Bus as an item, for placing it on a cable.
 *
 * <p>Like the terminal, this is AE2's {@link IPartItem} contract and nothing more: placing, wrenching,
 * the model and the cable's click handling all come through it.
 */
public class ItemEssentiaImportBus extends Item implements IPartItem<PartEssentiaImportBus> {

    public ItemEssentiaImportBus(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PartHelper.usePartItem(context);
    }

    @Override
    public Class<PartEssentiaImportBus> getPartClass() {
        return PartEssentiaImportBus.class;
    }

    @Override
    public PartEssentiaImportBus createPart() {
        return new PartEssentiaImportBus(this);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.thaumicenergistics_ce.essentia_import_bus.desc"));
        tooltip.add(Component.translatable("tooltip.thaumicenergistics_ce.essentia_import_bus.hint"));
    }
}
