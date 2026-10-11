package thaumicenergistics_ce.item;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import thaumicenergistics_ce.part.PartVisInterface;

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
}
