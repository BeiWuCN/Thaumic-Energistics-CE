package com.beiwu.thaumicenergistics_ce.menu;

import com.beiwu.thaumicenergistics_ce.config.ThEConfig;

import appeng.menu.me.common.MEStorageMenu;
public final class EssentiaVisibility {
    private EssentiaVisibility() {}

    public static boolean allowed(MEStorageMenu menu) {
        // 奥术终端的源质行不再看访问卡：可见性与交互性解耦后，
        // 没卡也能看到网络里有什么，只是右键存不进去（那道闸在 essentiaAccessGranted）。
        if (!ThEConfig.RESTRICT_ESSENTIA_VISIBILITY.get()) {
            return true;
        }
        return menu instanceof MenuEssentiaTerminalBase;
    }
}
