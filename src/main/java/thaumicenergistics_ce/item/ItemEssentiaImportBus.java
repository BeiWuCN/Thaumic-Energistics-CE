package thaumicenergistics_ce.item;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import thaumicenergistics_ce.part.PartEssentiaImportBus;

/**
 * The Essentia Import Bus as an item, for placing it on a cable.
 * {@link IPartItem} is AE2's contract for a part: placing, wrenching, the model and the
 * cable's click handling all come through it.
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
}
