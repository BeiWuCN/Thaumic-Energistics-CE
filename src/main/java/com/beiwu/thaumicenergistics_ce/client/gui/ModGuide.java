package com.beiwu.thaumicenergistics_ce.client.gui;

import guideme.GuidesCommon;
import guideme.PageAnchor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import com.beiwu.thaumicenergistics_ce.ThEIds;

/**
 * 把本 mod 的 GUI 帮助按钮接到 ME 指南上。AEBaseScreen 自带一个 help 按钮，
 * 但它的 getHelpTopic 固定用 ae2 命名空间，指向不了本 mod 的指南；屏幕覆盖这两个方法即可。
 */
public final class ModGuide {
    private ModGuide() {}

    public static final ResourceLocation GUIDE_ID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "guide");

    /** 页面取自 2.7.5.5 的指南结构；本地只保留这两处 GUI 帮助入口。 */
    public static final String PAGE_ARCANE_TERMINAL =
            "thaumicenergistics_ce:arcane-crafting.md";
    public static final String PAGE_CELL_WORKBENCH =
            "thaumicenergistics_ce:essentia-storage.md";

    public static void open(Player player, String page) {
        GuidesCommon.openGuide(player, GUIDE_ID, PageAnchor.page(ResourceLocation.parse(page)));
    }
}
