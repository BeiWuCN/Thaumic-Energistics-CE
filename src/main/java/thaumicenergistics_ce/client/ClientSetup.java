package thaumicenergistics_ce.client;

import appeng.api.client.AEKeyRendering;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.StyleManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.client.render.ArcaneAssemblerRenderer;
import thaumicenergistics_ce.client.render.MonitorBubbleRenderer;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.menu.MenuEssentiaExportBus;
import thaumicenergistics_ce.menu.MenuEssentiaImportBus;
import thaumicenergistics_ce.menu.MenuEssentiaLevelEmitter;
import thaumicenergistics_ce.menu.MenuEssentiaStorageBus;
import thaumicenergistics_ce.menu.MenuEssentiaTerminal;

/**
 * Client-only wiring, kept behind {@link Dist#CLIENT} so the dedicated server never loads a screen
 * class and with it client-only Minecraft types.
 */
@EventBusSubscriber(modid = ThEIds.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    /**
     * Tells AE2 how to draw an essentia key, on {@code FMLClientSetupEvent} rather than with the
     * screens because it must be in place before anything draws a key. Enqueued onto the client thread.
     */
    @SubscribeEvent
    public static void registerKeyRendering(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            AEKeyRendering.register(
                    AEssentiaKeyType.INSTANCE, AEssentiaKey.class, new EssentiaKeyRenderHandler());
        });
    }

    /**
     * The Infusion Monitor's risk bubble, drawn above the block. A block entity renderer, because the
     * reference build's {@code TextDisplay} is not the shape this should have been.
     */
    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                thaumicenergistics_ce.init.ModBlockEntities.INFUSION_MONITOR.get(), MonitorBubbleRenderer::new);
        // And the assembler's product, drawn inside the block: what a machine is doing should be
        // legible from outside it.
        event.registerBlockEntityRenderer(
                thaumicenergistics_ce.init.ModBlockEntities.ARCANE_ASSEMBLER.get(),
                ArcaneAssemblerRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.ARCANE_ASSEMBLER.get(), ScreenArcaneAssembler::new);
        event.register(ModMenuTypes.KNOWLEDGE_INSCRIBER.get(), ScreenKnowledgeInscriber::new);
        // Draws its own art rather than using a screen style.
        event.register(ModMenuTypes.ESSENTIA_CELL_WORKBENCH.get(), ScreenEssentiaCellWorkbench::new);
        event.register(ModMenuTypes.DISTILLATION_ENCODER.get(), ScreenDistillationEncoder::new);
        // Draws its own window rather than blitting one: the machine's art is a widget, not a panel.
        event.register(ModMenuTypes.ESSENTIA_VIBRATION_CHAMBER.get(), ScreenEssentiaVibrationChamber::new);
        // AE2's own terminal layout, so the terminal looks like every other AE2 terminal and a player
        // already knows how to read one.
        //
        // The lambda names its parameter types because the event's register method is generic over both
        // the menu and the screen, and a bare lambda leaves Java nothing to infer them from.
        event.register(
                ModMenuTypes.ESSENTIA_TERMINAL.get(),
                (MenuEssentiaTerminal menu, Inventory inventory, Component title) ->
                        new ScreenEssentiaTerminal(
                                menu,
                                inventory,
                                title,
                                StyleManager.loadStyleDoc("/screens/terminals/terminal.json")));
        // AE2's StyleManager only resolves its own namespace, hence the path carries none.
        event.register(
                ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get(),
                (MenuArcaneCraftingTerminal menu, Inventory inventory, Component title) ->
                        new ScreenArcaneCraftingTerminal(
                                menu,
                                inventory,
                                title,
                                StyleManager.loadStyleDoc("/screens/arcane_crafting_terminal.json")));
        // The wireless one is the same screen: the two menus differ in how the network is reached, not in
        // what is drawn. AE2's wireless terminal layout is used so the power bar is where players expect.
        event.register(
                ModMenuTypes.WIRELESS_ESSENTIA_TERMINAL.get(),
                (MenuEssentiaTerminal menu, Inventory inventory, Component title) ->
                        new ScreenEssentiaTerminal(
                                menu,
                                inventory,
                                title,
                                StyleManager.loadStyleDoc("/screens/terminals/wireless_terminal.json")));
        // AE2's own upgradeable screens and style documents: a style under this mod's assets cannot
        // be loaded at all, as StyleManager searches AE2's namespace only.
        //
        // The import and export buses use named screen classes because JEI's ghost ingredient handler
        // registers against a screen class, and a generic screen has none to register against.
        event.register(
                ModMenuTypes.ESSENTIA_IMPORT_BUS.get(),
                (MenuEssentiaImportBus menu, Inventory inventory, Component title) ->
                        new ScreenEssentiaImportBus(
                                menu, inventory, title, StyleManager.loadStyleDoc("/screens/import_bus.json")));
        event.register(
                ModMenuTypes.ESSENTIA_EXPORT_BUS.get(),
                (MenuEssentiaExportBus menu, Inventory inventory, Component title) ->
                        new ScreenEssentiaExportBus(
                                menu, inventory, title, StyleManager.loadStyleDoc("/screens/export_bus.json")));
        event.register(
                ModMenuTypes.ESSENTIA_STORAGE_BUS.get(),
                (MenuEssentiaStorageBus menu, Inventory inventory, Component title) ->
                        new ScreenEssentiaStorageBus(
                                menu, inventory, title, StyleManager.loadStyleDoc("/screens/storage_bus.json")));
        event.register(
                ModMenuTypes.ESSENTIA_LEVEL_EMITTER.get(),
                (MenuEssentiaLevelEmitter menu, Inventory inventory, Component title) ->
                        new UpgradeableScreen<>(
                                menu, inventory, title, StyleManager.loadStyleDoc("/screens/level_emitter.json")));
    }
}
