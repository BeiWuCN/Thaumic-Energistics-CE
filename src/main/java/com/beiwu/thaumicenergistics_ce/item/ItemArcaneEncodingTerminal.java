package com.beiwu.thaumicenergistics_ce.item;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import com.beiwu.thaumicenergistics_ce.part.PartArcaneEncodingTerminal;

/**
 * 作为物品的 ME 奥术编码终端，放到线缆上使用。
 * 与奥术合成终端同理：直接实现 {@link IPartItem}，因为 AE2 检查的是这个接口。
 */
public class ItemArcaneEncodingTerminal extends Item implements IPartItem<PartArcaneEncodingTerminal> {

    public ItemArcaneEncodingTerminal(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return PartHelper.usePartItem(context);
    }

    @Override
    public Class<PartArcaneEncodingTerminal> getPartClass() {
        return PartArcaneEncodingTerminal.class;
    }

    @Override
    public PartArcaneEncodingTerminal createPart() {
        return new PartArcaneEncodingTerminal(this);
    }
}
