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
 * Thaumic Energistics：把 [Thaumaturge] 的源质接到 [Applied Energistics 2] 的 [ME 网络]。
 * 目标：Minecraft 1.21.1、NeoForge 21.1.250、Thaumaturge、AE2 19.2.x。奥术自动合成走
 * [Knowledge Inscriber]：它把一份原料网格当作 [AE2] [样板] 存进知识核心，再公布这些配方，
 * 用环境 vis 运行。
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
        // [tooltip] 的服务端那一半走 [AE2] 部件注册表，[Jade] 只看得到方块实体。
        FluxTransferStatusProvider.register();
        // [ME 接口] 的访问卡挂游戏总线，不挂网格的可 tick 对象：[AE2] 在卡片进出时不发通知。
        // 见 [EssentiaInterfaceRegistry]。
        EssentiaInterfaceRegistry.register();
    }

    /**
     * [AE2] 19 里 {@code @PartModels} 只是个标记，模型还得自己注册。渲染器查不到位置的部件，
     * 一放下去就崩。
     */
    private static void registerPartModels() {
        List<ResourceLocation> models = new ArrayList<>();
        models.addAll(PartEssentiaTerminal.MODEL_LOCATIONS);
        models.addAll(PartFluxTransferInterface.MODEL_LOCATIONS);
        models.addAll(PartEssentiaLevelEmitter.MODEL_LOCATIONS);
        models.addAll(PartArcaneCraftingTerminal.MODEL_LOCATIONS);
        // [P2P] 部件用 [AE2] 自己的 [P2P] 模型集绘制，含状态模型。
        models.addAll(PartVisInterface.MODEL_LOCATIONS);
        PartModels.registerModels(models);
    }

    /**
     * 没有 {@code IN_WORLD_GRID_NODE_HOST}，机器自成一张孤立网格，[ME 终端] 看不见它。
     */
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // 只实现接口不够，[能力] 不注册就不生效。
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

        // [AE2] 只把自己家的方块实体桥接到 FE，[Jade] 的能量条读这个 [能力] 返回的值。
        // 不列出就两边都看不到箱子的储备。
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.GACHA_BOX.get(),
                (blockEntity, context) -> blockEntity.getEnergyStorage(context));

        // STORAGE [能力] 与 [Thaumaturge] 罐子暴露的同一个，管道和邻居照同一个处理。
        event.registerBlockEntity(
                EssentiaCapabilities.STORAGE,
                ModBlockEntities.ALCHEMY_PROVIDER.get(),
                (blockEntity, context) -> (IEssentiaStorage)
                        blockEntity);

        // 对邻居来说是容器：挨着的罐子会被填满，挨着的蒸馏器会被抽空。
        event.registerBlockEntity(
                EssentiaCapabilities.STORAGE,
                ModBlockEntities.ALCHEMY_PROVIDER_CONNECTION.get(),
                (blockEntity, context) -> (IEssentiaStorage)
                        blockEntity);

        // STORAGE + TRANSPORT 源质才推得进来；吸取为通配，见类注释。
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

        // 箱子是消费者：屏幕后面的管道朝它伸臂，跟着它报的吸取量走，罐子里的 cognitio 就是这被拖进来的。
        event.registerBlockEntity(
                EssentiaCapabilities.TRANSPORT,
                ModBlockEntities.GACHA_BOX.get(),
                (blockEntity, context) -> blockEntity.essentiaTransport(context));

        // 注魔祭坛抽源质时扫的就是 Aspect CONTAINER 这个 [能力]。
        event.registerBlockEntity(
                AspectCapabilities.CONTAINER,
                ModBlockEntities.INFUSION_PROVIDER.get(),
                (blockEntity, context) -> (IAspectSource)
                        blockEntity);
    }

    /**
     * TECE 自己的部件走 [AE2] 自己的事件暴露：部件不是方块实体，查找走线缆总线。
     * 监听器要交给总线，只能写成静态。
     */
    public static void registerPartCapabilities(RegisterPartCapabilitiesEvent event) {
        TcAura.registerVisSource(event, PartVisInterface.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 跳过会静默破坏内存卡绑定；要在物品注册之后跑。
            GridLinkables.register(
                    ModItems.GOLEM_WIFI_BACKPACK.get(), ItemGolemWirelessBackpack.LINKABLE_HANDLER);
            GridLinkables.register(
                    ModItems.WIRELESS_ESSENTIA_TERMINAL.get(),
                    WirelessTerminalItem.LINKABLE_HANDLER);
            // 访问点的链接槽按物品查这张表，未注册的终端放不进去，静默破坏同上一行。
            GridLinkables.register(
                    ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                    WirelessTerminalItem.LINKABLE_HANDLER);
            registerUpgrades();
            ThELog.LOG.info("ThaumicEnergistics common setup complete");
            // 不注册，每次终端合成都以 PAYMENT_UNAVAILABLE 失败。见 [TerminalWorkbenchVis]。
            thaumicenergistics_ce.arcane.TerminalWorkbenchVis.register();
        });
    }

    /**
     * 告诉 [AE2] 本 mod 的机器和存储元件收哪些升级卡。不注册，槽位会显示 [AE2] 的 "available upgrades" 表头，
     * 下面空着，那张表来自 [AE2] 自己的注册表。
     */
    private static void registerUpgrades() {
        // [AE2] 的 [BasicCellInventory] 三种卡都读；源质要素不带 NBT，没有模糊卡。
        // 名称键是 [AE2] 自己的第四个参数，把五个等级并成一行 [tooltip]。
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
        // 每个槽位一张卡：这个数字就是机器自己的槽位数，两边不会对不上。
        Upgrades.add(
                AEItems.SPEED_CARD,
                ModItems.ARCANE_ASSEMBLER.get(),
                BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT);
        // 每个 [ME 接口] 一张访问卡，方块形态和线缆部件都要。少了这两条，
        // [AE2] 的升级槽拒收我们的卡。同一个名称键让方块和部件共用一行。
        Upgrades.add(ModItems.ESSENTIA_ACCESS_CARD.get(), AEBlocks.INTERFACE, 1, INTERFACE_UPGRADE_NAME);
        Upgrades.add(ModItems.ESSENTIA_ACCESS_CARD.get(), AEParts.INTERFACE, 1, INTERFACE_UPGRADE_NAME);
        // 同一张卡也进无线奥术终端自己的两个槽。少了这一行，[AE2] 槽位过滤器拒收，未注册的组合报告说放不下。
        Upgrades.add(
                ModItems.ESSENTIA_ACCESS_CARD.get(),
                ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                1,
                ARCANE_TERMINAL_UPGRADE_NAME);
        // vis 连接卡进同样两个槽。少了它自己那一行，[AE2] 槽位过滤器拒收；
        // 有它，合成从灵气取无类型 vis，不动网络能量。
        Upgrades.add(
                ModItems.VIS_CONNECTION_CARD.get(),
                ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                1,
                ARCANE_TERMINAL_UPGRADE_NAME);
        // 同一张卡也进装在电缆上的终端，它只有一个升级槽。
        // 卡放进去后，合成取电缆周围灵气的 vis，不动网络能量。
        Upgrades.add(
                ModItems.VIS_CONNECTION_CARD.get(),
                ModItems.ARCANE_CRAFTING_TERMINAL.get(),
                1,
                ARCANE_TERMINAL_PART_UPGRADE_NAME);
    }

    /** 卡片 [tooltip] 对整本源质存储元件家族的称呼，所有尺寸共用。 */
    private static final String CELL_UPGRADE_NAME = "item.thaumicenergistics_ce.essentia_cell";

    /** 访问卡 [tooltip] 对接口的称呼，方块和部件共用一个名字。 */
    private static final String INTERFACE_UPGRADE_NAME = "block.ae2.interface";

    /** 访问卡 [tooltip] 对无线奥术终端的称呼，用物品自己的名称键。 */
    private static final String ARCANE_TERMINAL_UPGRADE_NAME =
            "item.thaumicenergistics_ce.wireless_arcane_crafting_terminal";

    /** 装在电缆上的终端是另一个物品，[tooltip] 行要自己的名称键。 */
    private static final String ARCANE_TERMINAL_PART_UPGRADE_NAME =
            "item.thaumicenergistics_ce.arcane_crafting_terminal";

    /**
     * 把源质键类型加进 [AE2] 注册表。不能放在 mod 构造函数里：{@code AEKeyType} 是注册表对象，
     * [AE2] 建好注册表之前注册会抛异常。
     */
    private void registerKeyTypes(RegisterEvent event) {
        if (event.getRegistryKey() != AEKeyType.REGISTRY_KEY) {
            return;
        }
        AEKeyTypes.register(AEssentiaKeyType.INSTANCE);
        ThELog.LOG.info("Registered the essentia key type with AE2");
    }
}
