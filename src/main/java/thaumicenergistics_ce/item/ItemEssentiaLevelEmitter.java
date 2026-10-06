package thaumicenergistics_ce.item;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import thaumicenergistics_ce.part.PartEssentiaLevelEmitter;

/** The Essentia Level Emitter as an item, for placing it on a cable. */
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
}
