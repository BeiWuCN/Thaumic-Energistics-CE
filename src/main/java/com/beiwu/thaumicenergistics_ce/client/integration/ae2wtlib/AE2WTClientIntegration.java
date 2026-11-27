package com.beiwu.thaumicenergistics_ce.client.integration.ae2wtlib;

import appeng.client.gui.style.StyleManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import com.beiwu.thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import com.beiwu.thaumicenergistics_ce.menu.MenuEssentiaTerminal;
import com.beiwu.thaumicenergistics_ce.registry.TECEMenuTypes;

/**
 * AE2WTLib 客户端屏幕注册的纯门面入口：仅暴露 {@link #enabled()} 与转发方法，
 * 实际引用 {@code ae2wtlib_api} 与 WT 屏幕类的逻辑隔离在私有静态内部类 {@link Impl}，
 * 防止缺 {@code ae2wtlib} 时调用 {@link #enabled()} 或验证 {@code ClientSetup} 触发类加载错误。
 */
public final class AE2WTClientIntegration {

    private AE2WTClientIntegration() {}

    /** AE2WTLib 是否在场。 */
    public static boolean enabled() {
        return ModList.get().isLoaded("ae2wtlib");
    }

    /** 装了 AE2WTLib 时为两个无线终端注册 WT 屏幕。调用方须先确认 {@link #enabled()}。 */
    public static void registerScreens(RegisterMenuScreensEvent event) {
        if (!enabled()) {
            return;
        }
        Impl.registerScreens(event);
    }

    /** 承载对 {@code AE2WT*Screen} 与 {@code IUniversalTerminalCapable} 引用的实现类。 */
    private static final class Impl {

        private Impl() {}

        static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(
                    TECEMenuTypes.WIRELESS_ESSENTIA_TERMINAL.get(),
                    (MenuEssentiaTerminal menu, Inventory inventory, Component title) ->
                            new AE2WTEssentiaTerminalScreen(
                                    menu,
                                    inventory,
                                    title,
                                    StyleManager.loadStyleDoc("/screens/wireless_essentia_terminal_wt.json")));
            event.register(
                    TECEMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                    (MenuArcaneCraftingTerminal menu, Inventory inventory, Component title) ->
                            new AE2WTArcaneCraftingTerminalScreen(
                                    menu,
                                    inventory,
                                    title,
                                    StyleManager.loadStyleDoc("/screens/wireless_arcane_crafting_terminal_wt.json")));
        }
    }
}
