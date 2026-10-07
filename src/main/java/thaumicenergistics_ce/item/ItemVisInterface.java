package thaumicenergistics_ce.item;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import thaumicenergistics_ce.part.PartVisInterface;

/**
 * Vis 接口作为物品的形态，可以装到线缆上。
 * 直接实现 {@link IPartItem} 是 AE2 检查的，放置逻辑与部件工厂都留在这一个类里。
 */
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
