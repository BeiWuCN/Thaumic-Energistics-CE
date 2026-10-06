package thaumicenergistics_ce.item;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * The Arcane Crafting Terminal as an item, for placing it on a cable; implements {@link IPartItem}
 * directly rather than extending AE2's {@code PartItem}, which is what AE2 actually checks for and
 * keeps the placement path and the factory in one place.
 */
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
