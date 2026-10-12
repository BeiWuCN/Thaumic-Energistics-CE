package thaumicenergistics_ce.client;

import appeng.client.api.AEKeyRendering;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.StyleManager;
import com.leclowndu93150.thaumaturge.client.model.entity.BrainModel;
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
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.client.gui.ScreenArcaneAssembler;
import thaumicenergistics_ce.client.gui.ScreenArcaneCraftingTerminal;
import thaumicenergistics_ce.client.gui.ScreenDistillationEncoder;
import thaumicenergistics_ce.client.gui.ScreenEssentiaCellWorkbench;
import thaumicenergistics_ce.client.gui.ScreenEssentiaTerminal;
import thaumicenergistics_ce.client.gui.ScreenEssentiaResonanceChamber;
import thaumicenergistics_ce.client.gui.ScreenKnowledgeInscriber;
import thaumicenergistics_ce.client.render.ArcaneAssemblerRenderer;
import thaumicenergistics_ce.client.render.EssentiaKeyRenderHandler;
import thaumicenergistics_ce.client.render.GachaBoxRenderer;
import thaumicenergistics_ce.client.render.bubble.OccultMonitorBubbleRenderer;
import thaumicenergistics_ce.registry.TECEBlockEntities;
import thaumicenergistics_ce.registry.TECEMenuTypes;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.integration.ae2.ClientRegistries;
import thaumicenergistics_ce.integration.ae2.ClientRegistrySource;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench;
import thaumicenergistics_ce.menu.MenuEssentiaLevelEmitter;
import thaumicenergistics_ce.menu.MenuEssentiaTerminal;
import thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import thaumicenergistics_ce.network.ClientSinks;
import thaumicenergistics_ce.network.ClientboundReceiver;
import thaumicenergistics_ce.network.GolemBackpackPayload;

@EventBusSubscriber(modid = ThEIds.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void registerKeyRendering(FMLClientSetupEvent event) {
        net.neoforged.fml.ModList.get().getModContainerById(ThEIds.MODID).ifPresent(container ->
                container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                        net.neoforged.neoforge.client.gui.ConfigurationScreen::new));
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
        ClientRegistries.install(new ClientRegistrySource() {
            @Override
            public @Nullable RegistryAccess registries() {
                var minecraft = Minecraft.getInstance();
                if (minecraft == null) {
                    return null;
                }
                var level = minecraft.level;
                if (level != null) {
                    return level.registryAccess();
                }
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
    public static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(GolemBackpackPayload.TYPE, (payload, context) -> ClientSinks.acceptGolemBackpack(payload));
        event.register(ArcaneCraftCostPayload.TYPE, (payload, context) -> ClientSinks.acceptArcaneCraftCost(payload));
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                TECEBlockEntities.OCCULT_MONITOR.get(), OccultMonitorBubbleRenderer::new);
        event.registerBlockEntityRenderer(
                TECEBlockEntities.ARCANE_ASSEMBLER.get(),
                ArcaneAssemblerRenderer::new);
        event.registerBlockEntityRenderer(TECEBlockEntities.GACHA_BOX.get(), GachaBoxRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GachaBoxRenderer.BRAIN_LAYER, BrainModel::createLayer);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(TECEMenuTypes.ARCANE_ASSEMBLER.get(), ScreenArcaneAssembler::new);
        event.register(TECEMenuTypes.KNOWLEDGE_INSCRIBER.get(), ScreenKnowledgeInscriber::new);
        event.register(
                TECEMenuTypes.ESSENTIA_CELL_WORKBENCH.get(),
                (MenuEssentiaCellWorkbench menu, Inventory inventory, Component title) ->
                        new ScreenEssentiaCellWorkbench(
                                menu,
                                inventory,
                                title,
                                StyleManager.loadStyleDoc("/screens/essentia_cell_workbench.json")));
        event.register(TECEMenuTypes.DISTILLATION_ENCODER.get(), ScreenDistillationEncoder::new);
        event.register(TECEMenuTypes.ESSENTIA_VIBRATION_CHAMBER.get(), ScreenEssentiaResonanceChamber::new);
        event.register(
                TECEMenuTypes.ESSENTIA_TERMINAL.get(),
                (MenuEssentiaTerminal menu, Inventory inventory, Component title) ->
                        new ScreenEssentiaTerminal(
                                menu,
                                inventory,
                                title,
                                StyleManager.loadStyleDoc("/screens/terminals/terminal.json")));
        event.register(
                TECEMenuTypes.ARCANE_CRAFTING_TERMINAL.get(),
                (MenuArcaneCraftingTerminal menu, Inventory inventory, Component title) ->
                        new ScreenArcaneCraftingTerminal(
                                menu,
                                inventory,
                                title,
                                StyleManager.loadStyleDoc("/screens/arcane_crafting_terminal.json")));
        event.register(
                TECEMenuTypes.WIRELESS_ESSENTIA_TERMINAL.get(),
                (MenuEssentiaTerminal menu, Inventory inventory, Component title) ->
                        new ScreenEssentiaTerminal(
                                menu,
                                inventory,
                                title,
                                StyleManager.loadStyleDoc("/screens/wireless_essentia_terminal.json")));
        event.register(
                TECEMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                (MenuArcaneCraftingTerminal menu, Inventory inventory, Component title) ->
                        new ScreenArcaneCraftingTerminal(
                                menu,
                                inventory,
                                title,
                                StyleManager.loadStyleDoc("/screens/wireless_arcane_crafting_terminal.json")));
        event.register(
                TECEMenuTypes.ESSENTIA_LEVEL_EMITTER.get(),
                (MenuEssentiaLevelEmitter menu, Inventory inventory, Component title) ->
                        new UpgradeableScreen<>(
                                menu, inventory, title, StyleManager.loadStyleDoc("/screens/level_emitter.json")));
    }
}
