package thaumicenergistics_ce.arcane;

import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 能够指出自身背后奥术合成终端的菜单宿主：可能是已放置的部件本身，也可能是
 * 记住了配对对象的无线物品。菜单询问这个接口而不是宿主的类，
 * 因此有线与无线终端会构建出相同的网格、法杖槽与晶体——
 * 玩家看到的状态就是那个已放置终端自己的状态。
 */
public interface ArcaneTerminalHost {

    @Nullable PartArcaneCraftingTerminal arcaneTerminal();
}
