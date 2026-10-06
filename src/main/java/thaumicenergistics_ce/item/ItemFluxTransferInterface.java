package thaumicenergistics_ce.item;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import thaumicenergistics_ce.part.PartFluxTransferInterface;

/** The Flux Transfer Interface as an item, for placing it on a cable. */
public class ItemFluxTransferInterface extends Item implements IPartItem<PartFluxTransferInterface> {

    public ItemFluxTransferInterface(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PartHelper.usePartItem(context);
    }

    @Override
    public Class<PartFluxTransferInterface> getPartClass() {
        return PartFluxTransferInterface.class;
    }

    @Override
    public PartFluxTransferInterface createPart() {
        return new PartFluxTransferInterface(this);
    }
}
