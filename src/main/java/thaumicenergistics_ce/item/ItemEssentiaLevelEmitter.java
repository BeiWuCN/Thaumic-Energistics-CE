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
import thaumicenergistics_ce.part.PartEssentiaLevelEmitter;

/**
 * The Essentia Level Emitter as an item, for placing it on a cable.
 */
public class ItemEssentiaLevelEmitter extends Item implements IPartItem<PartEssentiaLevelEmitter> {

    public ItemEssentiaLevelEmitter(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PartHelper.usePartItem(context);
    }

    @Override
    public Class<PartEssentiaLevelEmitter> getPartClass() {
        return PartEssentiaLevelEmitter.class;
    }

    @Override
    public PartEssentiaLevelEmitter createPart() {
        return new PartEssentiaLevelEmitter(this);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.thaumicenergistics_ce.essentia_level_emitter.desc"));
    }
}
