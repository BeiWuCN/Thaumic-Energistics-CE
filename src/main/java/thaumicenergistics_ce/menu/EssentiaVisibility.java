package thaumicenergistics_ce.menu;

import thaumicenergistics_ce.config.ThEConfig;

import appeng.menu.me.common.MEStorageMenu;
public final class EssentiaVisibility {
    private EssentiaVisibility() {}

    public static boolean allowed(MEStorageMenu menu) {
        if (!ThEConfig.RESTRICT_ESSENTIA_VISIBILITY.get()) {
            return true;
        }
        if (menu instanceof MenuArcaneCraftingTerminal arcane) {
            return arcane.hasEssentiaAccessCard();
        }
        return menu instanceof MenuEssentiaTerminalBase;
    }
}
