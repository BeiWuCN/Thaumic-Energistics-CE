package thaumicenergistics_ce.item;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import thaumicenergistics_ce.part.PartEssentiaTerminal;

/**
 * 源质终端作为物品的形态，用于把它装到线缆上。
 * 实现 {@link IPartItem} 才使 AE2 把它当作部件对待：放置、扳手拆卸、
 * 线缆的点击处理与模型都由它驱动。
 */
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
