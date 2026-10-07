package thaumicenergistics_ce;

import appeng.api.AECapabilities;
import appeng.api.features.GridLinkables;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.parts.PartModels;
import appeng.api.parts.RegisterPartCapabilitiesEvent;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.items.tools.powered.WirelessTerminalItem;
import com.leclowndu93150.thaumaturge.api.aspect.AspectCapabilities;
import com.leclowndu93150.thaumaturge.api.aspect.IAspectSource;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.focus.AEWrenchActions;
import thaumicenergistics_ce.focus.FocusElements;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.init.ModBlocks;
import thaumicenergistics_ce.init.ModCreativeTab;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.integration.jade.FluxTransferStatusProvider;
import thaumicenergistics_ce.item.ItemGolemWirelessBackpack;
import thaumicenergistics_ce.init.ModNetwork;
import thaumicenergistics_ce.init.capability.ThEItemCapabilities;
import thaumicenergistics_ce.interfaceaccess.EssentiaInterfaceRegistry;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import thaumicenergistics_ce.part.PartEssentiaLevelEmitter;
import thaumicenergistics_ce.part.PartEssentiaTerminal;
import thaumicenergistics_ce.part.PartFluxTransferInterface;
import thaumicenergistics_ce.part.PartVisInterface;
import thaumicenergistics_ce.util.ThELog;

/**
 * Thaumic Energistics——把 [Thaumaturge] 的源质与 [Applied Energistics 2] 的 [ME 网络] 对接起来。
 * 目标：Minecraft 1.21.1、NeoForge 21.1.250、Thaumaturge、AE2 19.2.x。对于奥术自动合成，
 * [Knowledge Inscriber] 把一份原料网格作为 [AE2] [样板] 存进知识核心；那台
 * 机器公布这些配方，并以环境 vis 为代价运行它们。
 */
@Mod(ThEIds.MODID)
public final class ThaumicEnergistics {
    public ThaumicEnergistics(IEventBus modBus, ModContainer container) {
        ThELog.LOG.info("ThaumicEnergistics loading");

        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenuTypes.register(modBus);
        ModCreativeTab.register(modBus);
        ModNetwork.register(modBus);
        FocusElements.register(modBus);

        modBus.addListener(this::registerCapabilities);
        modBus.addListener(ThEItemCapabilities::install);
        modBus.addListener(ThaumicEnergistics::registerPartCapabilities);
        modBus.addListener(this::registerKeyTypes);
        modBus.addListener(this::commonSetup);

        registerPartModels();
        // [tooltip] 的服务端那一半走 [AE2] 的部件注册表；[Jade] 只能看到方块实体。
        FluxTransferStatusProvider.register();
        // [ME 接口] 的访问卡走游戏总线而不是网格的可 tick 对象，因为 [AE2]
        // 在卡片进出时不报告任何东西——见 [EssentiaInterfaceRegistry]。
        EssentiaInterfaceRegistry.register();
    }

    /**
     * 注册每一个部件模型：在 [AE2] 19 中 {@code @PartModels} 只是个标记，而一个
     * 渲染器找不到的位置，就是部件被放置那一刻的崩溃。
     */
    private static void registerPartModels() {
        List<ResourceLocation> models = new ArrayList<>();
        models.addAll(PartEssentiaTerminal.MODEL_LOCATIONS);
        models.addAll(PartFluxTransferInterface.MODEL_LOCATIONS);
        models.addAll(PartEssentiaLevelEmitter.MODEL_LOCATIONS);
        models.addAll(PartArcaneCraftingTerminal.MODEL_LOCATIONS);
        // [P2P] 部件用 [AE2] 自己的 [P2P] 模型集绘制自身，含状态模型。
        models.addAll(PartVisInterface.MODEL_LOCATIONS);
        PartModels.registerModels(models);
    }

    /**
     * 把 mod 的网格机器暴露给 [AE2] 的网络；没有 {@code IN_WORLD_GRID_NODE_HOST} 时，
     * 机器会形成自己的孤立网格，[ME 终端] 永远不会知道它存在。
     */
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // 只实现接口是不够的；未注册的 [能力] 始终不起作用。
        for (BlockEntityType<?> type : List.of(
                ModBlockEntities.ARCANE_ASSEMBLER.get(),
                ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(),
                ModBlockEntities.ALCHEMY_PROVIDER.get(),
                ModBlockEntities.INFUSION_PROVIDER.get(),
                ModBlockEntities.OCCULT_MONITOR.get(),
                ModBlockEntities.GACHA_BOX.get())) {
            event.registerBlockEntity(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST,
                    type,
                    (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);
        }

        // [AE2] 只把自己拥有的方块实体桥接到 FE，而 [Jade] 画的能量条取自
        // 这个 [能力] 给出的值，因此在被列出之前，箱子的储备对这两者都不可见。
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.GACHA_BOX.get(),
                (blockEntity, context) -> blockEntity.getEnergyStorage(context));

        // 与 [Thaumaturge] 的罐子暴露的 STORAGE [能力] 相同；管道与相邻方块都把它当作同一个。
        event.registerBlockEntity(
                EssentiaCapabilities.STORAGE,
                ModBlockEntities.ALCHEMY_PROVIDER.get(),
                (blockEntity, context) -> (IEssentiaStorage)
                        blockEntity);

        // 对相邻方块它是个容器：旁边的罐子会被填满，旁边的蒸馏器会被抽空。
        event.registerBlockEntity(
                EssentiaCapabilities.STORAGE,
                ModBlockEntities.ALCHEMY_PROVIDER_CONNECTION.get(),
                (blockEntity, context) -> (IEssentiaStorage)
                        blockEntity);

        // STORAGE + TRANSPORT，这样源质才能被压入；通配吸取——见类注释。
        event.registerBlockEntity(
                EssentiaCapabilities.STORAGE,
                ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(),
                (blockEntity, context) -> (IEssentiaStorage)
                        blockEntity);
        event.registerBlockEntity(
                EssentiaCapabilities.TRANSPORT,
                ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(),
                (blockEntity, context) -> (IEssentiaTransport)
                        blockEntity);

        // 箱子是个消费者：屏幕后面的管道会朝它伸出一条臂并跟随
        // 箱子报告的吸取，正是这个把罐子里的 cognitio 顺着管道拖进去。
        event.registerBlockEntity(
                EssentiaCapabilities.TRANSPORT,
                ModBlockEntities.GACHA_BOX.get(),
                (blockEntity, context) -> blockEntity.essentiaTransport(context));

        // Aspect CONTAINER 是注魔祭坛为抽取源质而扫描的那个 [能力]。
        event.registerBlockEntity(
                AspectCapabilities.CONTAINER,
                ModBlockEntities.INFUSION_PROVIDER.get(),
                (blockEntity, context) -> (IAspectSource)
                        blockEntity);
    }

    /**
     * 用 [AE2] 自己的事件暴露 TECE 自己的部件，因为部件不是方块实体，
     * 查找要经过线缆总线。写成静态是因为它作为监听器交给了总线。
     */
    public static void registerPartCapabilities(RegisterPartCapabilitiesEvent event) {
        TcAura.registerVisSource(event, PartVisInterface.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 跳过这一步会静默地破坏内存卡绑定；必须在物品注册之后运行。
            GridLinkables.register(
                    ModItems.GOLEM_WIFI_BACKPACK.get(), ItemGolemWirelessBackpack.LINKABLE_HANDLER);
            GridLinkables.register(
                    ModItems.WIRELESS_ESSENTIA_TERMINAL.get(),
                    WirelessTerminalItem.LINKABLE_HANDLER);
            // 访问点的链接槽按物品查询这个注册表，所以未注册的终端会在
            // 玩家放进去之前就被拒绝——与上一行同样的静默破坏。
            GridLinkables.register(
                    ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                    WirelessTerminalItem.LINKABLE_HANDLER);
            registerUpgrades();
            ThELog.LOG.info("ThaumicEnergistics common setup complete");
            // 否则每次终端合成都会以 PAYMENT_UNAVAILABLE 失败——见 [TerminalWorkbenchVis]。
            thaumicenergistics_ce.arcane.TerminalWorkbenchVis.register();
        });
    }

    /**
     * 告诉 [AE2] 这个 mod 的机器与存储元件接受哪些升级卡；没有它，它们的槽位会显示 [AE2] 的
     * "available upgrades" 表头而下面空无一物，因为那张列表来自 [AE2] 自己的注册表。
     */
    private static void registerUpgrades() {
        // [AE2] 的 [BasicCellInventory] 会读取全部三种卡；源质要素不携带 NBT，所以没有模糊卡。
        // 名称键是 [AE2] 自己的第四个参数，它把五个等级收拢成一行 [tooltip]。
        for (var cell : List.of(
                ModItems.ESSENTIA_CELL_1K.get(),
                ModItems.ESSENTIA_CELL_4K.get(),
                ModItems.ESSENTIA_CELL_16K.get(),
                ModItems.ESSENTIA_CELL_64K.get(),
                ModItems.ESSENTIA_CELL_CREATIVE.get())) {
            Upgrades.add(AEItems.INVERTER_CARD, cell, 1, CELL_UPGRADE_NAME);
            Upgrades.add(AEItems.EQUAL_DISTRIBUTION_CARD, cell, 1, CELL_UPGRADE_NAME);
            Upgrades.add(AEItems.VOID_CARD, cell, 1, CELL_UPGRADE_NAME);
        }
        // 每个槽位一张卡：这个数字就是机器自身的槽位数量，因此两者不可能不一致。
        Upgrades.add(
                AEItems.SPEED_CARD,
                ModItems.ARCANE_ASSEMBLER.get(),
                BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT);
        // 每个 [ME 接口] 一张访问卡，方块形态与线缆部件都一样：没有这两条，[AE2] 的
        // 升级槽会拒绝我们的卡。同一个名称键让方块与部件共用一行。
        Upgrades.add(ModItems.ESSENTIA_ACCESS_CARD.get(), AEBlocks.INTERFACE, 1, INTERFACE_UPGRADE_NAME);
        Upgrades.add(ModItems.ESSENTIA_ACCESS_CARD.get(), AEParts.INTERFACE, 1, INTERFACE_UPGRADE_NAME);
        // 同一张卡放进无线奥术终端自己的两个槽位；没有这一行，[AE2] 的槽位
        // 过滤器会拒绝它，因为未注册的组合报告说一个都放不下。
        Upgrades.add(
                ModItems.ESSENTIA_ACCESS_CARD.get(),
                ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                1,
                ARCANE_TERMINAL_UPGRADE_NAME);
        // vis 连接卡放进同样这两个槽位：没有它自己的一行，[AE2] 的槽位过滤器会
        // 拒绝它；有了它，合成改为从灵气取用无类型 vis，而不是用网络的能量。
        Upgrades.add(
                ModItems.VIS_CONNECTION_CARD.get(),
                ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                1,
                ARCANE_TERMINAL_UPGRADE_NAME);
        // 同一张卡用在装在电缆上的终端：它也有一个升级槽，卡放进去之后
        // 合成改为从电缆周围的灵气取 vis，而不是用网络的能量。
        Upgrades.add(
                ModItems.VIS_CONNECTION_CARD.get(),
                ModItems.ARCANE_CRAFTING_TERMINAL.get(),
                1,
                ARCANE_TERMINAL_PART_UPGRADE_NAME);
    }

    /** 卡片 [tooltip] 对整本源质存储元件家族的称呼，适用于所有尺寸。 */
    private static final String CELL_UPGRADE_NAME = "item.thaumicenergistics_ce.essentia_cell";

    /** 访问卡 [tooltip] 对接口的称呼：方块与部件共用同一个名称。 */
    private static final String INTERFACE_UPGRADE_NAME = "block.ae2.interface";

    /** 访问卡 [tooltip] 对无线奥术终端的称呼：物品自己的名称键。 */
    private static final String ARCANE_TERMINAL_UPGRADE_NAME =
            "item.thaumicenergistics_ce.wireless_arcane_crafting_terminal";

    /** 装在电缆上的同一终端是另一个物品，所以它的 [tooltip] 行需要自己的名称键。 */
    private static final String ARCANE_TERMINAL_PART_UPGRADE_NAME =
            "item.thaumicenergistics_ce.arcane_crafting_terminal";

    /**
     * 把源质键类型加到 [AE2] 的注册表。不能在 mod 构造函数里做：{@code AEKeyType}
     * 是注册表对象，在 [AE2] 建好自己的注册表之前注册会抛异常。
     */
    private void registerKeyTypes(RegisterEvent event) {
        if (event.getRegistryKey() != AEKeyType.REGISTRY_KEY) {
            return;
        }
        AEKeyTypes.register(AEssentiaKeyType.INSTANCE);
        ThELog.LOG.info("Registered the essentia key type with AE2");
    }
}
