package thaumicenergistics_ce.init;

import appeng.api.upgrades.Upgrades;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.item.ItemArcaneCraftingTerminal;
import thaumicenergistics_ce.item.ItemArcanePattern;
import thaumicenergistics_ce.item.ItemEssentiaCell;
import thaumicenergistics_ce.item.ItemEssentiaLevelEmitter;
import thaumicenergistics_ce.item.ItemEssentiaTerminal;
import thaumicenergistics_ce.item.ItemFluxTransferInterface;
import thaumicenergistics_ce.item.ItemFocusAEWrench;
import thaumicenergistics_ce.item.ItemGolemWirelessBackpack;
import thaumicenergistics_ce.item.ItemKnowledgeCore;
import thaumicenergistics_ce.item.ItemVisInterface;
import thaumicenergistics_ce.item.ItemWirelessArcaneCraftingTerminal;
import thaumicenergistics_ce.item.ItemWirelessConnector;
import thaumicenergistics_ce.item.ItemWirelessEssentiaTerminal;

/**
 * 物品注册，含 {@link ModBlocks} 的方块物品。
 * 方块物品放这里：NeoForge 的 {@code registerSimpleBlockItem} 属于物品注册表，
 * 持有者却来自 {@link ModBlocks}，这个类两边都要读。
 */
public final class ModItems {
    public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(ThEIds.MODID);

    public static final DeferredItem<ItemKnowledgeCore> KNOWLEDGE_CORE = REGISTRY.registerItem(
            "knowledge_core", ItemKnowledgeCore::new, () -> new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    /**
     * 要继承 {@code EncodedPatternItem}：AE2 的 CPU 把任务存成 {@code AEItemKey} 标签，
     * 用 {@code PatternDetailsHelper.decodePattern} 重建，它能识别的只有 {@code EncodedPatternItem}。
     */
    public static final DeferredItem<Item> ARCANE_PATTERN =
            REGISTRY.registerItem("arcane_pattern", ItemArcanePattern::build, () -> new Item.Properties());

    public static final DeferredItem<BlockItem> ARCANE_ASSEMBLER = REGISTRY.registerItem(
            "arcane_assembler",
            p -> new BlockItem(ModBlocks.ARCANE_ASSEMBLER.get(), p),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> KNOWLEDGE_INSCRIBER = REGISTRY.registerItem(
            "knowledge_inscriber",
            p -> new BlockItem(ModBlocks.KNOWLEDGE_INSCRIBER.get(), p),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> ESSENTIA_CELL_WORKBENCH =
            REGISTRY.registerSimpleBlockItem(ModBlocks.ESSENTIA_CELL_WORKBENCH);

    public static final DeferredItem<BlockItem> ESSENTIA_VIBRATION_CHAMBER = REGISTRY.registerItem(
            "essentia_vibration_chamber",
            p -> new BlockItem(ModBlocks.ESSENTIA_VIBRATION_CHAMBER.get(), p),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> ALCHEMY_PROVIDER = REGISTRY.registerItem(
            "alchemy_provider",
            p -> new BlockItem(ModBlocks.ALCHEMY_PROVIDER.get(), p),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> INFUSION_PROVIDER = REGISTRY.registerItem(
            "infusion_provider",
            p -> new BlockItem(ModBlocks.INFUSION_PROVIDER.get(), p),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> DISTILLATION_ENCODER = REGISTRY.registerItem(
            "distillation_encoder",
            p -> new BlockItem(ModBlocks.DISTILLATION_ENCODER.get(), p),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> OCCULT_MONITOR = REGISTRY.registerItem(
            "occult_monitor",
            p -> new BlockItem(ModBlocks.OCCULT_MONITOR.get(), p),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> ALCHEMY_PROVIDER_CONNECTION = REGISTRY.registerItem(
            "alchemy_provider_connection",
            p -> new BlockItem(ModBlocks.ALCHEMY_PROVIDER_CONNECTION.get(), p),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> GACHA_BOX = REGISTRY.registerSimpleBlockItem(ModBlocks.GACHA_BOX);

    public static final DeferredItem<BlockItem> GACHA_BOX_AGGREGATOR =
            REGISTRY.registerSimpleBlockItem(ModBlocks.GACHA_BOX_AGGREGATOR);

    public static final DeferredItem<ItemWirelessConnector> WIRELESS_CONNECTOR = REGISTRY.registerItem(
            "wireless_connector", ItemWirelessConnector::new, () -> new Item.Properties());

    public static final DeferredItem<BlockItem> ALKUSURE86_FUMO =
            REGISTRY.registerSimpleBlockItem(ModBlocks.ALKUSURE86_FUMO);

    public static final DeferredItem<ItemVisInterface> VIS_INTERFACE =
            REGISTRY.registerItem("vis_interface", ItemVisInterface::new, () -> new Item.Properties());

    /**
     * 作为法杖核心佩戴的 AE2 扳手。
     * 不做成 {@code PartItem}：Thaumaturge 的核心是普通物品，行为放在物品堆的包组件里。
     */
    public static final DeferredItem<ItemFocusAEWrench> FOCUS_AEWRENCH = REGISTRY.registerItem(
            "focus_aewrench",
            p -> new ItemFocusAEWrench(p), () -> new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    public static final DeferredItem<ItemGolemWirelessBackpack> GOLEM_WIFI_BACKPACK = REGISTRY.registerItem(
            "golem_wifi_backpack",
            ItemGolemWirelessBackpack::new, () -> new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    public static final DeferredItem<ItemArcaneCraftingTerminal> ARCANE_CRAFTING_TERMINAL =
            REGISTRY.registerItem(
                    "arcane_crafting_terminal", ItemArcaneCraftingTerminal::new, () -> new Item.Properties());

    // 容量按字节计，与 AE2 一致：以源质算是名称数字的八倍，1k 存 8192。

    public static final DeferredItem<Item> STORAGE_CASING = REGISTRY.registerItem(
            "storage_casing", Item::new, () -> new Item.Properties().stacksTo(64));

    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_1K = REGISTRY.registerItem(
            "essentia_cell_1k", ItemEssentiaCell::create1k, () -> new Item.Properties().rarity(Rarity.COMMON));

    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_4K = REGISTRY.registerItem(
            "essentia_cell_4k", ItemEssentiaCell::create4k, () -> new Item.Properties().rarity(Rarity.COMMON));

    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_16K = REGISTRY.registerItem(
            "essentia_cell_16k", ItemEssentiaCell::create16k, () -> new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_64K = REGISTRY.registerItem(
            "essentia_cell_64k", ItemEssentiaCell::create64k, () -> new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_CREATIVE = REGISTRY.registerItem(
            "essentia_cell_creative",
            ItemEssentiaCell::createCreative, () -> new Item.Properties().rarity(Rarity.EPIC));

    // 普通物品：容量在存储元件里，组件只是原料；档位沿用 AE2 的字节数。

    public static final DeferredItem<Item> STORAGE_COMPONENT_1K = REGISTRY.registerItem(
            "storage_component_1k", Item::new, () -> new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> STORAGE_COMPONENT_4K = REGISTRY.registerItem(
            "storage_component_4k", Item::new, () -> new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> STORAGE_COMPONENT_16K = REGISTRY.registerItem(
            "storage_component_16k", Item::new, () -> new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> STORAGE_COMPONENT_64K = REGISTRY.registerItem(
            "storage_component_64k", Item::new, () -> new Item.Properties().stacksTo(64));

    public static final DeferredItem<ItemEssentiaTerminal> ESSENTIA_TERMINAL = REGISTRY.registerItem(
            "essentia_terminal",
            ItemEssentiaTerminal::new, () -> new Item.Properties().stacksTo(64).rarity(Rarity.RARE));

    // 没有行为的普通物品：终端和总线用的原料，与 AE2 的湮灭核心、成型核心同一种形式。
    public static final DeferredItem<Item> DIFFUSION_CORE = REGISTRY.registerItem(
            "diffusion_core", Item::new, () -> new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> COALESCENCE_CORE = REGISTRY.registerItem(
            "coalescence_core", Item::new, () -> new Item.Properties().stacksTo(64));

    public static final DeferredItem<ItemFluxTransferInterface> FLUX_TRANSFER_INTERFACE =
            REGISTRY.registerItem(
                    "flux_transfer_interface",
                    ItemFluxTransferInterface::new, () -> new Item.Properties().stacksTo(64).rarity(Rarity.RARE));

    public static final DeferredItem<ItemEssentiaLevelEmitter> ESSENTIA_LEVEL_EMITTER = REGISTRY.registerItem(
            "essentia_level_emitter",
            ItemEssentiaLevelEmitter::new, () -> new Item.Properties().stacksTo(64).rarity(Rarity.RARE));

    public static final DeferredItem<ItemWirelessEssentiaTerminal> WIRELESS_ESSENTIA_TERMINAL =
            REGISTRY.registerItem(
                    "wireless_essentia_terminal",
                    properties -> new ItemWirelessEssentiaTerminal(
                            () -> ItemWirelessEssentiaTerminal.POWER_CAPACITY, properties), () -> new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    public static final DeferredItem<ItemWirelessArcaneCraftingTerminal> WIRELESS_ARCANE_CRAFTING_TERMINAL =
            REGISTRY.registerItem(
                    "wireless_arcane_crafting_terminal",
                    properties -> new ItemWirelessArcaneCraftingTerminal(
                            () -> ItemWirelessArcaneCraftingTerminal.POWER_CAPACITY, properties), () -> new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    // 两张升级卡都会被读：访问卡开关源质手势，vis 卡把合成中未指定类型的 vis 转到灵气上。
    // AE2 的 [isUpgradeCardItem] 要求物品是自己的类，故用工厂创建。
    public static final DeferredItem<Item> ESSENTIA_ACCESS_CARD = REGISTRY.registerItem(
            "essentia_access_card", Upgrades::createUpgradeCardItem, () -> new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> VIS_CONNECTION_CARD = REGISTRY.registerItem(
            "vis_connection_card", Upgrades::createUpgradeCardItem, () -> new Item.Properties().stacksTo(64));

    private ModItems() {}

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
