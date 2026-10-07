package thaumicenergistics_ce.item;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 作为物品的奥术合成终端，用于把它放到线缆上；直接实现 {@link IPartItem}
 * 而不是继承 AE2 的 {@code PartItem}，因为前者才是 AE2 真正检查的东西，
 * 也让放置路径与工厂待在一处。
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
