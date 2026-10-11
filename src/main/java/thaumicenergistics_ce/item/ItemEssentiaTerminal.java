package thaumicenergistics_ce.item;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import thaumicenergistics_ce.part.PartEssentiaTerminal;

public class ItemEssentiaTerminal extends Item implements IPartItem<PartEssentiaTerminal> {

    public ItemEssentiaTerminal(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PartHelper.usePartItem(context);
    }

    @Override
    public Class<PartEssentiaTerminal> getPartClass() {
        return PartEssentiaTerminal.class;
    }

    @Override
    public PartEssentiaTerminal createPart() {
        return new PartEssentiaTerminal(this);
    }
}
