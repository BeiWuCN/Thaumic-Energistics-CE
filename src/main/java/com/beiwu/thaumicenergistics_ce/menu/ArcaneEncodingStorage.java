package com.beiwu.thaumicenergistics_ce.menu;

import appeng.api.inventories.InternalInventory;

/**
 * 奥术编码终端菜单要的那一小块状态：九格编码网格、装样板的那个知识核心、以及当前选中的样板。
 *
 * <p>两处实现：线缆上的 {@code PartArcaneEncodingTerminal} 存在部件里，
 * 无线版存在物品组件里。菜单只认这个接口，不关心宿主是方块部件还是手里的物品。
 */
public interface ArcaneEncodingStorage {

    /** 九格编码网格。 */
    InternalInventory encodingGrid();

    /** 知识核心槽（只有 0 号槽）。 */
    InternalInventory encodingCore();

    /** 选中的样板下标，-1 为没选中。 */
    int selectedEncodingPattern();

    void setSelectedEncodingPattern(int index);
}
