package thaumicenergistics_ce.item;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import thaumicenergistics_ce.part.PartEssentiaExportBus;

/**
 * The Essentia Export Bus as an item, for placing it on a cable.
 * {@link IPartItem} is AE2's contract for a part: placing, wrenching and the model
 * all come through it.
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
}
