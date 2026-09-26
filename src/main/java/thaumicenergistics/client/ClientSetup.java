package thaumicenergistics.client;

import appeng.api.client.AEKeyRendering;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.StyleManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import thaumicenergistics.ThEIds;
import thaumicenergistics.client.render.ArcaneAssemblerRenderer;
import thaumicenergistics.init.ModMenuTypes;
import thaumicenergistics.integration.ae2.AEssentiaKey;
import thaumicenergistics.integration.ae2.AEssentiaKeyType;
import thaumicenergistics.menu.MenuEssentiaExportBus;
import thaumicenergistics.menu.MenuEssentiaImportBus;
import thaumicenergistics.menu.MenuEssentiaLevelEmitter;
import thaumicenergistics.menu.MenuEssentiaStorageBus;
import thaumicenergistics.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics.menu.MenuEssentiaTerminal;

/**
 * Client-only wiring.
 *
 * <p>Kept behind {@link Dist#CLIENT} so the dedicated server never loads a screen class, which would
 * pull in client-only Minecraft types.
 */
@EventBusSubscriber(modid = ThEIds.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    /**
     * Tells AE2 how to draw an essentia key.
     *
     * <p>On {@code FMLClientSetupEvent} rather than with the screens, because it must be in place before
     * anything draws a key - a terminal row or a storage cell's tooltip - and that can happen the moment
     * a world is joined. Enqueued onto the client thread, as the event requires.
     */
    @SubscribeEvent
    public static void registerKeyRendering(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            AEKeyRendering.register(
                    AEssentiaKeyType.INSTANCE, AEssentiaKey.class, new EssentiaKeyRenderHandler());
        });
    }

    /**
     * The Infusion Monitor's risk bubble, drawn above the block.
     *
     * <p>A block entity renderer rather than an entity: see {@link MonitorBubbleRenderer} for why the
     * reference build's {@code TextDisplay} is not the shape this should have been.
     */
    @SubscribeEvent
    public static void registerRenderers(
            net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                thaumicenergistics.init.ModBlockEntities.INFUSION_MONITOR.get(), MonitorBubbleRenderer::new);
        // And the assembler's product, drawn inside the block while a craft runs - the molecular
        // assembler's own arrangement, and for the same reason: what a machine is doing should be
        // legible from outside it.
        event.registerBlockEntityRenderer(
                thaumicenergistics.init.ModBlockEntities.ARCANE_ASSEMBLER.get(),
                ArcaneAssemblerRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.ARCANE_ASSEMBLER.get(), ScreenArcaneAssembler::new);
        event.register(ModMenuTypes.KNOWLEDGE_INSCRIBER.get(), ScreenKnowledgeInscriber::new);
        // Draws its own art rather than using a screen style - see the class.
        event.register(ModMenuTypes.ESSENTIA_CELL_WORKBENCH.get(), ScreenEssentiaCellWorkbench::new);
        event.register(ModMenuTypes.DISTILLATION_ENCODER.get(), ScreenDistillationEncoder::new);
        // Draws its own window rather than blitting one: the machine's art is a widget, not a panel.
        event.register(ModMenuTypes.ESSENTIA_VIBRATION_CHAMBER.get(), ScreenEssentiaVibrationChamber::new);
        // AE2's own terminal layout, not a style of ours: the terminal looks like every other AE2
        // terminal, which is the point - a player already knows how to read one. The reference build
        // loads the same document for its essentia terminal.
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
        // The Arcane Crafting Terminal's style is resolved out of AE2's namespace on purpose - its
        // StyleManager only looks there - so the path carries no namespace of its own.
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
        // The bus screens are AE2's own upgradeable screens, and AE2's own style documents with them.
        // StyleManager resolves a style inside AE2's namespace only, so a document under this mod's assets
        // cannot be loaded at all - which is why these name AE2's files rather than copies of them.
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
