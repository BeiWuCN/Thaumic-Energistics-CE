package thaumicenergistics_ce.menu;

import thaumicenergistics_ce.ThEConfig;

import appeng.menu.me.common.MEStorageMenu;
public final class EssentiaVisibility {
    private EssentiaVisibility() {}

    public static boolean allowed(MEStorageMenu menu) {
        if (menu instanceof MenuArcaneCraftingTerminal arcane) {
            return arcane.hasEssentiaAccessCard();
        }
        if (!ThEConfig.RESTRICT_ESSENTIA_VISIBILITY.get()) {
            return true;
        }
        return menu instanceof MenuEssentiaTerminalBase;
    }
}
