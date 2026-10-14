package thaumicenergistics_ce.registry;

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

public final class TECEItems {
    public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(ThEIds.MODID);

    public static final DeferredItem<ItemKnowledgeCore> KNOWLEDGE_CORE = REGISTRY.registerItem(
            "knowledge_core", ItemKnowledgeCore::new, () -> new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    public static final DeferredItem<Item> ARCANE_PATTERN =
            REGISTRY.registerItem("arcane_pattern", ItemArcanePattern::build, () -> new Item.Properties());

    public static final DeferredItem<BlockItem> ARCANE_ASSEMBLER = REGISTRY.registerItem(
            "arcane_assembler",
            p -> new BlockItem(TECEBlocks.ARCANE_ASSEMBLER.get(), p),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> KNOWLEDGE_INSCRIBER = REGISTRY.registerItem(
            "knowledge_inscriber",
            p -> new BlockItem(TECEBlocks.KNOWLEDGE_INSCRIBER.get(), p),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> ESSENTIA_CELL_WORKBENCH =
            REGISTRY.registerSimpleBlockItem(TECEBlocks.ESSENTIA_CELL_WORKBENCH);

    public static final DeferredItem<BlockItem> ESSENTIA_VIBRATION_CHAMBER = REGISTRY.registerItem(
            "essentia_vibration_chamber",
            p -> new BlockItem(TECEBlocks.ESSENTIA_VIBRATION_CHAMBER.get(), p),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> ALCHEMY_PROVIDER = REGISTRY.registerItem(
            "alchemy_provider",
            p -> new BlockItem(TECEBlocks.ALCHEMY_PROVIDER.get(), p),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> INFUSION_PROVIDER = REGISTRY.registerItem(
            "infusion_provider",
            p -> new BlockItem(TECEBlocks.INFUSION_PROVIDER.get(), p),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> DISTILLATION_ENCODER = REGISTRY.registerItem(
            "distillation_encoder",
            p -> new BlockItem(TECEBlocks.DISTILLATION_ENCODER.get(), p),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> OCCULT_MONITOR = REGISTRY.registerItem(
            "occult_monitor",
            p -> new BlockItem(TECEBlocks.OCCULT_MONITOR.get(), p),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> ALCHEMY_PROVIDER_CONNECTION = REGISTRY.registerItem(
            "alchemy_provider_connection",
            p -> new BlockItem(TECEBlocks.ALCHEMY_PROVIDER_CONNECTION.get(), p),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> GACHA_BOX = REGISTRY.registerSimpleBlockItem(TECEBlocks.GACHA_BOX);

    public static final DeferredItem<BlockItem> GACHA_BOX_AGGREGATOR =
            REGISTRY.registerSimpleBlockItem(TECEBlocks.GACHA_BOX_AGGREGATOR);

    public static final DeferredItem<ItemWirelessConnector> WIRELESS_CONNECTOR = REGISTRY.registerItem(
            "wireless_connector", ItemWirelessConnector::new, () -> new Item.Properties());

    public static final DeferredItem<BlockItem> ALKUSURE86_FUMO =
            REGISTRY.registerSimpleBlockItem(TECEBlocks.ALKUSURE86_FUMO);

    public static final DeferredItem<BlockItem> BEIWU_CN_FUMO =
            REGISTRY.registerSimpleBlockItem(TECEBlocks.BEIWU_CN_FUMO);

    public static final DeferredItem<BlockItem> XIAO_LENG_FUMO =
            REGISTRY.registerSimpleBlockItem(TECEBlocks.XIAO_LENG_FUMO);

    public static final DeferredItem<ItemVisInterface> VIS_INTERFACE =
            REGISTRY.registerItem("vis_interface", ItemVisInterface::new, () -> new Item.Properties());

    public static final DeferredItem<ItemFocusAEWrench> FOCUS_AEWRENCH = REGISTRY.registerItem(
            "focus_aewrench",
            p -> new ItemFocusAEWrench(p), () -> new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    public static final DeferredItem<ItemGolemWirelessBackpack> GOLEM_WIFI_BACKPACK = REGISTRY.registerItem(
            "golem_wifi_backpack",
            ItemGolemWirelessBackpack::new, () -> new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    public static final DeferredItem<ItemArcaneCraftingTerminal> ARCANE_CRAFTING_TERMINAL =
            REGISTRY.registerItem(
                    "arcane_crafting_terminal", ItemArcaneCraftingTerminal::new, () -> new Item.Properties());


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

    public static final DeferredItem<Item> ESSENTIA_ACCESS_CARD = REGISTRY.registerItem(
            "essentia_access_card", Upgrades::createUpgradeCardItem, () -> new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> VIS_CONNECTION_CARD = REGISTRY.registerItem(
            "vis_connection_card", thaumicenergistics_ce.item.ItemVisConnectionCard::new, () -> new Item.Properties().stacksTo(64));

    private TECEItems() {}

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
