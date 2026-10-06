package thaumicenergistics_ce.item;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import thaumicenergistics_ce.part.PartEssentiaStorageBus;

/** The Essentia Storage Bus as an item, for placing it on a cable. */
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
}
