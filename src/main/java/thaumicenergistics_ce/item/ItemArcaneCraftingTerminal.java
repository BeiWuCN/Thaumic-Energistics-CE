package thaumicenergistics_ce.item;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

public class ItemArcaneCraftingTerminal extends Item implements IPartItem<PartArcaneCraftingTerminal> {

    public ItemArcaneCraftingTerminal(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PartHelper.usePartItem(context);
    }

    @Override
    public Class<PartArcaneCraftingTerminal> getPartClass() {
        return PartArcaneCraftingTerminal.class;
    }

    @Override
    public PartArcaneCraftingTerminal createPart() {
        return new PartArcaneCraftingTerminal(this);
    }
}
