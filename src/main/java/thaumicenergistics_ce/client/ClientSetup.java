package thaumicenergistics_ce.client;

import appeng.api.client.AEKeyRendering;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.StyleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.client.gui.ScreenArcaneAssembler;
import thaumicenergistics_ce.client.gui.ScreenArcaneCraftingTerminal;
import thaumicenergistics_ce.client.gui.ScreenDistillationEncoder;
import thaumicenergistics_ce.client.gui.ScreenEssentiaCellWorkbench;
import thaumicenergistics_ce.client.gui.ScreenEssentiaExportBus;
import thaumicenergistics_ce.client.gui.ScreenEssentiaImportBus;
import thaumicenergistics_ce.client.gui.ScreenEssentiaStorageBus;
import thaumicenergistics_ce.client.gui.ScreenEssentiaTerminal;
import thaumicenergistics_ce.client.gui.ScreenEssentiaVibrationChamber;
import thaumicenergistics_ce.client.gui.ScreenKnowledgeInscriber;
import thaumicenergistics_ce.client.render.ArcaneAssemblerRenderer;
import thaumicenergistics_ce.client.render.EssentiaKeyRenderHandler;
import thaumicenergistics_ce.client.render.MonitorBubbleRenderer;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.integration.ae2.ClientRegistries;
import thaumicenergistics_ce.integration.ae2.ClientRegistrySource;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench;
import thaumicenergistics_ce.menu.MenuEssentiaExportBus;
import thaumicenergistics_ce.menu.MenuEssentiaImportBus;
import thaumicenergistics_ce.menu.MenuEssentiaLevelEmitter;
import thaumicenergistics_ce.menu.MenuEssentiaStorageBus;
import thaumicenergistics_ce.menu.MenuEssentiaTerminal;
import thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import thaumicenergistics_ce.network.ClientSinks;
import thaumicenergistics_ce.network.ClientboundReceiver;
import thaumicenergistics_ce.network.GolemBackpackPayload;

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
        // The screens and the client cache are the only receivers this side has, so this is where the
        // protocol package learns about them; on a dedicated server nothing is installed.
        ClientSinks.install(new ClientboundReceiver() {
            @Override
            public void acceptArcaneCraftCost(ArcaneCraftCostPayload payload) {
                ScreenArcaneCraftingTerminal.acceptCost(payload);
            }

            @Override
            public void acceptGolemBackpack(GolemBackpackPayload payload) {
                GolemBackpackClientData.accept(payload);
            }
        });
        // The key type asks for this side's registries through here rather than naming a client class,
        // so it must be in place before the first key is resolved.
        ClientRegistries.install(new ClientRegistrySource() {
            @Override
            public @Nullable RegistryAccess registries() {
                var minecraft = Minecraft.getInstance();
                if (minecraft == null) {
                    return null;
                }
                // Field, not a getter: this Minecraft has no getLevel, and asking for one throws.
                var level = minecraft.level;
                if (level != null) {
                    return level.registryAccess();
                }
                // Before a level exists, the connection's registries are already the server's.
                var connection = minecraft.getConnection();
                return connection == null ? null : connection.registryAccess();
            }
        });
        event.enqueueWork(() -> {
            AEKeyRendering.register(
                    AEssentiaKeyType.INSTANCE, AEssentiaKey.class, new EssentiaKeyRenderHandler());
        });
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.INFUSION_MONITOR.get(), MonitorBubbleRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.ARCANE_ASSEMBLER.get(),
                ArcaneAssemblerRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.ARCANE_ASSEMBLER.get(), ScreenArcaneAssembler::new);
        event.register(ModMenuTypes.KNOWLEDGE_INSCRIBER.get(), ScreenKnowledgeInscriber::new);
        // The cell workbench is an AE2 upgradeable screen, so its art and its slots come from a style.
        event.register(
                ModMenuTypes.ESSENTIA_CELL_WORKBENCH.get(),
                (MenuEssentiaCellWorkbench menu, Inventory inventory, Component title) ->
                        new ScreenEssentiaCellWorkbench(
                                menu,
                                inventory,
                                title,
                                StyleManager.loadStyleDoc("/screens/essentia_cell_workbench.json")));
        event.register(ModMenuTypes.DISTILLATION_ENCODER.get(), ScreenDistillationEncoder::new);
        event.register(ModMenuTypes.ESSENTIA_VIBRATION_CHAMBER.get(), ScreenEssentiaVibrationChamber::new);
        // The lambda names its parameter types because register is generic over menu and screen.
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
        event.register(
                ModMenuTypes.WIRELESS_ESSENTIA_TERMINAL.get(),
                (MenuEssentiaTerminal menu, Inventory inventory, Component title) ->
                        new ScreenEssentiaTerminal(
                                menu,
                                inventory,
                                title,
                                StyleManager.loadStyleDoc("/screens/terminals/wireless_terminal.json")));
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
