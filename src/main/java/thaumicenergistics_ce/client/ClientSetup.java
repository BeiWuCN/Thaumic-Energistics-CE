package thaumicenergistics_ce.client;

import appeng.api.client.AEKeyRendering;
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
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.client.gui.ScreenArcaneAssembler;
import thaumicenergistics_ce.client.gui.ScreenArcaneCraftingTerminal;
import thaumicenergistics_ce.client.gui.ScreenDistillationEncoder;
import thaumicenergistics_ce.client.gui.ScreenEssentiaCellWorkbench;
import thaumicenergistics_ce.client.gui.ScreenEssentiaTerminal;
import thaumicenergistics_ce.client.gui.ScreenEssentiaVibrationChamber;
import thaumicenergistics_ce.client.gui.ScreenKnowledgeInscriber;
import thaumicenergistics_ce.client.jade.FluxTransferTooltip;
import thaumicenergistics_ce.client.render.ArcaneAssemblerRenderer;
import thaumicenergistics_ce.client.render.EssentiaKeyRenderHandler;
import thaumicenergistics_ce.client.render.GachaBoxRenderer;
import thaumicenergistics_ce.client.render.bubble.OccultMonitorBubbleRenderer;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.init.ModMenuTypes;
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

/**
 * 仅客户端的装配：整个类只在 {@link Dist#CLIENT} 侧注册。
 * 专用服务端不会加载界面类及其带来的客户端专有类型。
 */
@EventBusSubscriber(modid = ThEIds.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    /**
     * 客户端的 AE2 注册，挂在 {@code FMLClientSetupEvent} 上。
     * 它和界面都要赶在任何绘制之前就位。
     */
    @SubscribeEvent
    public static void registerKeyRendering(FMLClientSetupEvent event) {
        // 界面和客户端缓存只在本侧有接收者，协议包在这里认识它们。
        // 专用服务端上什么都不装。
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
        // key 类型从这里索要本侧的注册表，不指名任何客户端类。
        // 它要在第一个 key 被解析之前就位。
        ClientRegistries.install(new ClientRegistrySource() {
            @Override
            public @Nullable RegistryAccess registries() {
                var minecraft = Minecraft.getInstance();
                if (minecraft == null) {
                    return null;
                }
                // 读字段，别调 getter：这个 Minecraft 没有 [getLevel]，调用会抛异常。
                var level = minecraft.level;
                if (level != null) {
                    return level.registryAccess();
                }
                // 世界尚未创建，连接上的注册表就已经是服务端的。
                var connection = minecraft.getConnection();
                return connection == null ? null : connection.registryAccess();
            }
        });
        event.enqueueWork(() -> {
            AEKeyRendering.register(
                    AEssentiaKeyType.INSTANCE, AEssentiaKey.class, new EssentiaKeyRenderHandler());
            // tooltip 的绘制一半；服务端一半在 mod 构造函数里注册。
            FluxTransferTooltip.register();
        });
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.OCCULT_MONITOR.get(), OccultMonitorBubbleRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.ARCANE_ASSEMBLER.get(),
                ArcaneAssemblerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GACHA_BOX.get(), GachaBoxRenderer::new);
    }

    /**
     * 箱子的核心由 Thaumaturge 的模型类烘焙，外面套我们的一层。
     * 是否套上不取决于两个 mod 的注册顺序。
     */
    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GachaBoxRenderer.BRAIN_LAYER, BrainModel::createLayer);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.ARCANE_ASSEMBLER.get(), ScreenArcaneAssembler::new);
        event.register(ModMenuTypes.KNOWLEDGE_INSCRIBER.get(), ScreenKnowledgeInscriber::new);
        // 存储元件工作台是 AE2 可升级界面，美术和槽位都来自一个 style。
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
        // lambda 写明参数类型：register 在菜单和界面上是泛型的。
        event.register(
                ModMenuTypes.ESSENTIA_TERMINAL.get(),
                (MenuEssentiaTerminal menu, Inventory inventory, Component title) ->
                        new ScreenEssentiaTerminal(
                                menu,
                                inventory,
                                title,
                                StyleManager.loadStyleDoc("/screens/terminals/terminal.json")));
        // 路径里不带命名空间：[StyleManager] 只解析它自己的命名空间。
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
        // 无线终端画同一个工作台，在那套 style 之上多叠一个标题。
        event.register(
                ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                (MenuArcaneCraftingTerminal menu, Inventory inventory, Component title) ->
                        new ScreenArcaneCraftingTerminal(
                                menu,
                                inventory,
                                title,
                                StyleManager.loadStyleDoc("/screens/wireless_arcane_crafting_terminal.json")));
        event.register(
                ModMenuTypes.ESSENTIA_LEVEL_EMITTER.get(),
                (MenuEssentiaLevelEmitter menu, Inventory inventory, Component title) ->
                        new UpgradeableScreen<>(
                                menu, inventory, title, StyleManager.loadStyleDoc("/screens/level_emitter.json")));
    }
}
