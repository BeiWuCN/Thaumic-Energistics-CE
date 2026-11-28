package com.beiwu.thaumicenergistics_ce.menu.slot;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.GenericStack;
import appeng.menu.slot.FakeSlot;
import net.minecraft.world.item.ItemStack;
import com.beiwu.thaumicenergistics_ce.util.EssentiaContainers;

/**
 * 能把一只装满源质的安瓿/罐直接点进配置槽的假槽位。
 * <p>AE2 自己只认「物品即键」（一桶水标成水），源质没有对应物品，容器点进来会被判成物品键而拒收；
 * 这里先把容器拆成它装的那味源质再交给 AE2，剩下的由 {@code ConfigMenuInventory} 解包。
 * <p>空手点一下照 AE2 原本的行为清空。
 */
public class EssentiaFilterSlot extends FakeSlot {

    public EssentiaFilterSlot(InternalInventory inv, int invSlot) {
        super(inv, invSlot);
    }

    @Override
    public void set(ItemStack stack) {
        GenericStack contained = EssentiaContainers.contained(stack);
        if (contained != null) {
            stack = GenericStack.wrapInItemStack(contained.what(), 1);
        }
        super.set(stack);
    }
}
