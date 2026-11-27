package com.beiwu.thaumicenergistics_ce.item;

import appeng.api.inventories.InternalInventory;

/**
 * 无线奥术合成终端要的那一块状态：工作台九格、六格水晶、法杖槽。
 *
 * <p>两处实现：不走 AE2WTLib 时是 {@link WirelessArcaneCraftingTerminalMenuHost}，
 * 装了 AE2WTLib 时是 {@code integration.ae2wtlib.AE2WTArcaneCraftingTerminalMenuHost}。
 * 菜单只认这个接口，不关心宿主挂在哪个基类上，也不因此把可选 API 拖进普通代码。</p>
 */
public interface WirelessArcaneCraftingHost {

    /** 工作台九宫格。 */
    InternalInventory craftingGrid();

    /** 六个水晶槽，按 Thaumaturge 元要素顺序排。 */
    InternalInventory crystalInventory();

    /** 法杖槽（0 号）。 */
    InternalInventory wandInventory();
}
