package com.beiwu.thaumicenergistics_ce.client.gui;

import com.beiwu.thaumicenergistics_ce.ThEIds;
import guideme.GuidesCommon;
import guideme.PageAnchor;
import net.minecraft.world.entity.player.Player;

final class TECEGuide {
    private TECEGuide() {}

    static PageAnchor page(String path) {
        return PageAnchor.page(ThEIds.id(path + ".md"));
    }

    static void open(Player player, String path) {
        GuidesCommon.openGuide(player, ThEIds.id("guide"), page(path));
    }
}
