package com.beiwu.thaumicenergistics_ce.integration.ae2wtlib;

import appeng.api.util.KeyTypeSelection;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import java.util.function.BiConsumer;
import net.minecraft.world.entity.player.Player;

public final class AE2WTEssentiaTerminalMenuHost extends WTMenuHost {
    private final KeyTypeSelection essentiaOnly =
            new KeyTypeSelection(() -> {}, type -> type == AEssentiaKeyType.INSTANCE);

    public AE2WTEssentiaTerminalMenuHost(ItemWT item, Player player, ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
    }

    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return essentiaOnly;
    }
}