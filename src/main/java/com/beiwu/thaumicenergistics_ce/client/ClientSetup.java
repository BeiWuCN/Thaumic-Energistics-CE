package com.beiwu.thaumicenergistics_ce.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.client.StorageCellModels;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.StyleManager;
import com.leclowndu93150.thaumaturge.client.model.entity.BrainModel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.client.gui.ScreenArcaneAssembler;
import com.beiwu.thaumicenergistics_ce.client.gui.ScreenArcaneCraftingTerminal;
import com.beiwu.thaumicenergistics_ce.client.gui.ScreenArcaneEncodingTerminal;
import com.beiwu.thaumicenergistics_ce.client.gui.ScreenDistillationEncoder;
import com.beiwu.thaumicenergistics_ce.client.gui.ScreenEssentiaCellWorkbench;
import com.beiwu.thaumicenergistics_ce.client.gui.ScreenEssentiaTerminal;
import com.beiwu.thaumicenergistics_ce.client.gui.ScreenEssentiaResonanceChamber;
import com.beiwu.thaumicenergistics_ce.client.gui.ScreenKnowledgeInscriber;
import com.beiwu.thaumicenergistics_ce.client.jade.FluxTransferTooltip;
import com.beiwu.thaumicenergistics_ce.client.render.ArcaneAssemblerRenderer;
import com.beiwu.thaumicenergistics_ce.client.render.EssentiaKeyRenderHandler;
import com.beiwu.thaumicenergistics_ce.client.render.GachaBoxRenderer;
import com.beiwu.thaumicenergistics_ce.client.render.bubble.OccultMonitorBubbleRenderer;
import com.beiwu.thaumicenergistics_ce.item.ItemEssentiaCell;
import com.beiwu.thaumicenergistics_ce.registry.TECEBlockEntities;
import com.beiwu.thaumicenergistics_ce.registry.TECEItems;
import com.beiwu.thaumicenergistics_ce.registry.TECEMenuTypes;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import com.beiwu.thaumicenergistics_ce.integration.ae2.ClientRegistries;
import com.beiwu.thaumicenergistics_ce.integration.ae2.ClientRegistrySource;
import com.beiwu.thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import com.beiwu.thaumicenergistics_ce.menu.MenuArcaneEncodingTerminal;
import com.beiwu.thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench;
import com.beiwu.thaumicenergistics_ce.menu.MenuEssentiaLevelEmitter;
import com.beiwu.thaumicenergistics_ce.menu.MenuEssentiaTerminal;
import com.beiwu.thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import com.beiwu.thaumicenergistics_ce.network.ClientSinks;
import com.beiwu.thaumicenergistics_ce.network.ClientboundReceiver;
import com.beiwu.thaumicenergistics_ce.network.GolemBackpackPayload;

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

            @Override public void acceptInscriberCost(com.beiwu.thaumicenergistics_ce.network.InscriberCostPayload payload) {
                if (net.minecraft.client.Minecraft.getInstance().screen instanceof ScreenKnowledgeInscriber screen) screen.acceptCost(payload);
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
            var standardCell = ThEIds.id("block/drive/cells/essentia_cell");
            var megaCell = ThEIds.id("block/drive/cells/mega_essentia_cell");
            for (var item : BuiltInRegistries.ITEM) {
                if (item instanceof ItemEssentiaCell cell
                        && BuiltInRegistries.ITEM.getKey(cell).getNamespace().equals(ThEIds.MODID)) {
                    StorageCellModels.registerModel(cell, cell.tier().endsWith("m") ? megaCell : standardCell);
                }
            }
        });
    }

    // 刻意不给奥术编码终端注册物品染色：AE2 的部件物品靠 StaticItemColor 把 tint 1/2/3
    // 映射成暗/中/亮三档，那是给灰度掩码用的；本 mod 的三层正面贴图是自带颜色的成品图，
    // 挂上 AEColor.TRANSPARENT（实际是 fluix 的紫灰）会把三层乘成近乎全黑，
    // 物品栏里看着就是"正面纹理没了"。和奥术合成终端一样留默认白色。

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
        event.register(TECEMenuTypes.ARCANE_ASSEMBLER.get(), ScreenArcaneAssembler::new);
        event.register(TECEMenuTypes.KNOWLEDGE_INSCRIBER.get(), ScreenKnowledgeInscriber::new);
        // 存储元件工作台是 AE2 可升级界面，美术和槽位都来自一个 style。
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
        // lambda 写明参数类型：register 在菜单和界面上是泛型的。
        event.register(
                TECEMenuTypes.ESSENTIA_TERMINAL.get(),
                (MenuEssentiaTerminal menu, Inventory inventory, Component title) ->
                        new ScreenEssentiaTerminal(
                                menu,
                                inventory,
                                title,
                                StyleManager.loadStyleDoc("/screens/terminals/terminal.json")));
        // 路径里不带命名空间：[StyleManager] 只解析它自己的命名空间。
        event.register(
                TECEMenuTypes.ARCANE_CRAFTING_TERMINAL.get(),
                (MenuArcaneCraftingTerminal menu, Inventory inventory, Component title) ->
                        new ScreenArcaneCraftingTerminal(
                                menu,
                                inventory,
                                title,
                                StyleManager.loadStyleDoc("/screens/arcane_crafting_terminal.json")));
        event.register(
                TECEMenuTypes.ARCANE_ENCODING_TERMINAL.get(),
                (MenuArcaneEncodingTerminal menu, Inventory inventory, Component title) ->
                        new ScreenArcaneEncodingTerminal(
                                menu,
                                inventory,
                                title,
                                StyleManager.loadStyleDoc("/screens/arcane_encoding_terminal.json")));
        // 无线编码终端：画面与面板版一致，样式只是把标题换成物品名。
        event.register(
                TECEMenuTypes.WIRELESS_ESSENTIA_TERMINAL.get(),
                (MenuEssentiaTerminal menu, Inventory inventory, Component title) ->
                        new ScreenEssentiaTerminal(
                                menu,
                                inventory,
                                title,
                                StyleManager.loadStyleDoc("/screens/terminals/wireless_terminal.json")));
        // 两个无线奥术终端的屏幕二选一，不能先注普通版再注 wtlib 版：
        // NeoForge 的 [RegisterMenuScreensEvent#register] 对同一 MenuType 重复注册是直接抛
        // IllegalStateException，不是后注覆盖先注。
        if (com.beiwu.thaumicenergistics_ce.client.integration.ae2wtlib.AE2WTClientIntegration
                .enabled()) {
            com.beiwu.thaumicenergistics_ce.client.integration.ae2wtlib.AE2WTClientIntegration
                    .registerScreens(event);
        } else {
            // 无线终端画同一个工作台，在那套 style 之上多叠一个标题。
            event.register(
                    TECEMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                    (MenuArcaneCraftingTerminal menu, Inventory inventory, Component title) ->
                            new ScreenArcaneCraftingTerminal(
                                    menu,
                                    inventory,
                                    title,
                                    StyleManager.loadStyleDoc("/screens/wireless_arcane_crafting_terminal.json")));
            event.register(
                    TECEMenuTypes.WIRELESS_ARCANE_ENCODING_TERMINAL.get(),
                    (MenuArcaneEncodingTerminal menu, Inventory inventory, Component title) ->
                            new ScreenArcaneEncodingTerminal(
                                    menu,
                                    inventory,
                                    title,
                                    StyleManager.loadStyleDoc("/screens/wireless_arcane_encoding_terminal.json")));
        }
        event.register(
                TECEMenuTypes.ESSENTIA_LEVEL_EMITTER.get(),
                (MenuEssentiaLevelEmitter menu, Inventory inventory, Component title) ->
                        new UpgradeableScreen<>(
                                menu, inventory, title, StyleManager.loadStyleDoc("/screens/level_emitter.json")));
    }
}
