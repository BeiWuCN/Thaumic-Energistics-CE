package thaumicenergistics_ce.init;

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
import thaumicenergistics_ce.item.ItemEssentiaExportBus;
import thaumicenergistics_ce.item.ItemEssentiaImportBus;
import thaumicenergistics_ce.item.ItemEssentiaLevelEmitter;
import thaumicenergistics_ce.item.ItemEssentiaStorageBus;
import thaumicenergistics_ce.item.ItemEssentiaTerminal;
import thaumicenergistics_ce.item.ItemFocusAEWrench;
import thaumicenergistics_ce.item.ItemGolemWirelessBackpack;
import thaumicenergistics_ce.item.ItemKnowledgeCore;
import thaumicenergistics_ce.item.ItemMachineBlock;
import thaumicenergistics_ce.item.ItemVisInterface;
import thaumicenergistics_ce.item.ItemWirelessConnector;
import thaumicenergistics_ce.item.ItemWirelessEssentiaTerminal;

/**
 * Item registration, including the block items for {@link ModBlocks}.
 * <ul>
 *   <li>Block items live here because NeoForge's {@code registerSimpleBlockItem} helpers belong to the item
 *       registry while the block holders come from {@link ModBlocks}, so this class reads both.
 * </ul>
 */
public final class ModItems {
    public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(ThEIds.MODID);

    /** Holds the arcane recipes the Knowledge Inscriber writes and the Arcane Assembler reads. */
    public static final DeferredItem<ItemKnowledgeCore> KNOWLEDGE_CORE = REGISTRY.registerItem(
            "knowledge_core", ItemKnowledgeCore::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    /**
     * Not a plain item: AE2's CPU saves a task as one {@code AEItemKey} tag and rebuilds it with
     * {@code PatternDetailsHelper.decodePattern}, which answers only for {@code EncodedPatternItem}.
     */
    public static final DeferredItem<Item> ARCANE_PATTERN =
            REGISTRY.register("arcane_pattern", () -> ItemArcanePattern.build());

    public static final DeferredItem<BlockItem> ARCANE_ASSEMBLER =
            REGISTRY.registerSimpleBlockItem(ModBlocks.ARCANE_ASSEMBLER);

    public static final DeferredItem<BlockItem> KNOWLEDGE_INSCRIBER =
            REGISTRY.registerSimpleBlockItem(ModBlocks.KNOWLEDGE_INSCRIBER);

    public static final DeferredItem<BlockItem> ESSENTIA_CELL_WORKBENCH =
            REGISTRY.registerSimpleBlockItem(ModBlocks.ESSENTIA_CELL_WORKBENCH);

    public static final DeferredItem<BlockItem> ESSENTIA_VIBRATION_CHAMBER = REGISTRY.registerItem(
            "essentia_vibration_chamber",
            properties -> new ItemMachineBlock(
                    ModBlocks.ESSENTIA_VIBRATION_CHAMBER.get(),
                    properties,
                    "tooltip.thaumicenergistics_ce.essentia_vibration_chamber.desc",
                    "tooltip.thaumicenergistics_ce.essentia_vibration_chamber.hint"),
            new Item.Properties());

    public static final DeferredItem<BlockItem> ESSENTIA_PROVIDER = REGISTRY.registerItem(
            "essentia_provider",
            properties -> new ItemMachineBlock(
                    ModBlocks.ESSENTIA_PROVIDER.get(),
                    properties,
                    "tooltip.thaumicenergistics_ce.essentia_provider.desc",
                    "tooltip.thaumicenergistics_ce.essentia_provider.hint"),
            new Item.Properties());

    public static final DeferredItem<BlockItem> INFUSION_PROVIDER = REGISTRY.registerItem(
            "infusion_provider",
            properties -> new ItemMachineBlock(
                    ModBlocks.INFUSION_PROVIDER.get(),
                    properties,
                    "tooltip.thaumicenergistics_ce.infusion_provider.desc",
                    "tooltip.thaumicenergistics_ce.infusion_provider.hint"),
            new Item.Properties());

    public static final DeferredItem<BlockItem> DISTILLATION_ENCODER = REGISTRY.registerItem(
            "distillation_encoder",
            properties -> new ItemMachineBlock(
                    ModBlocks.DISTILLATION_ENCODER.get(),
                    properties,
                    "tooltip.thaumicenergistics_ce.distillation_encoder.desc",
                    "tooltip.thaumicenergistics_ce.distillation_encoder.hint"),
            new Item.Properties());

    public static final DeferredItem<BlockItem> INFUSION_MONITOR = REGISTRY.registerItem(
            "infusion_monitor",
            properties -> new ItemMachineBlock(
                    ModBlocks.INFUSION_MONITOR.get(),
                    properties,
                    "tooltip.thaumicenergistics_ce.infusion_monitor.desc",
                    "tooltip.thaumicenergistics_ce.infusion_monitor.hint"),
            new Item.Properties());

    public static final DeferredItem<BlockItem> ESSENTIA_PROVIDER_CONNECTION = REGISTRY.registerItem(
            "essentia_provider_connection",
            properties -> new ItemMachineBlock(
                    ModBlocks.ESSENTIA_PROVIDER_CONNECTION.get(),
                    properties,
                    "tooltip.thaumicenergistics_ce.essentia_provider_connection.desc",
                    "tooltip.thaumicenergistics_ce.essentia_provider_connection.hint"),
            new Item.Properties());

    /** Makes and breaks the links between an Essentia Provider and its receivers. */
    public static final DeferredItem<ItemWirelessConnector> WIRELESS_CONNECTOR = REGISTRY.registerItem(
            "wireless_connector", ItemWirelessConnector::new, new Item.Properties());

    /** The decorative figure; a plain block item, as the block's registry name is its only name. */
    public static final DeferredItem<BlockItem> ALKUSURE86_FUMO =
            REGISTRY.registerSimpleBlockItem(ModBlocks.ALKUSURE86_FUMO);

    /** The Vis Interface, as a cable part: lets machines draw vis and charge it to the network. */
    public static final DeferredItem<ItemVisInterface> VIS_INTERFACE =
            REGISTRY.registerItem("vis_interface", ItemVisInterface::new, new Item.Properties());

    /**
     * The AE2 wrench, worn as a wand focus. Not a {@code PartItem}: it is a focus, and Thaumaturge's foci
     * are ordinary items whose behaviour lives in the package component on the stack.
     */
    public static final DeferredItem<ItemFocusAEWrench> FOCUS_AEWRENCH = REGISTRY.registerItem(
            "focus_aewrench",
            p -> new ItemFocusAEWrench(p),
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    /** The golem's wireless link to an ME network. */
    public static final DeferredItem<ItemGolemWirelessBackpack> GOLEM_WIFI_BACKPACK = REGISTRY.registerItem(
            "golem_wifi_backpack",
            ItemGolemWirelessBackpack::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    /** The Arcane Crafting Terminal, as a cable part. */
    public static final DeferredItem<ItemArcaneCraftingTerminal> ARCANE_CRAFTING_TERMINAL =
            REGISTRY.registerItem(
                    "arcane_crafting_terminal", ItemArcaneCraftingTerminal::new, new Item.Properties());

    // ---- Essentia storage components --------------------------------------
    // Sizes are bytes, as AE2's components are: capacity in essentia is eight times the name, so a 1k
    // component holds 8192.

    /**
     * The housing half of a storage cell: made from ordinary crafting, unlike the component that carries the
     * capacity and is made at an arcane workbench.
     */
    public static final DeferredItem<Item> STORAGE_CASING = REGISTRY.registerItem(
            "storage_casing", Item::new, new Item.Properties().stacksTo(64));

    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_1K = REGISTRY.registerItem(
            "essentia_cell_1k", ItemEssentiaCell::create1k, new Item.Properties().rarity(Rarity.COMMON));

    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_4K = REGISTRY.registerItem(
            "essentia_cell_4k", ItemEssentiaCell::create4k, new Item.Properties().rarity(Rarity.COMMON));

    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_16K = REGISTRY.registerItem(
            "essentia_cell_16k", ItemEssentiaCell::create16k, new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_64K = REGISTRY.registerItem(
            "essentia_cell_64k", ItemEssentiaCell::create64k, new Item.Properties().rarity(Rarity.RARE));

    /** Holds as much as a component can be asked to hold. See ItemEssentiaCell.createCreative. */
    public static final DeferredItem<ItemEssentiaCell> ESSENTIA_CELL_CREATIVE = REGISTRY.registerItem(
            "essentia_cell_creative",
            ItemEssentiaCell::createCreative,
            new Item.Properties().rarity(Rarity.EPIC));

    // ---- The arcane half of a cell ----------------------------------------
    // Plain items: the capacity lives in the cell they are built into, so a component alone is only an
    // ingredient. Tiers are the byte figures AE2 uses for its own components.

    public static final DeferredItem<Item> STORAGE_COMPONENT_1K = REGISTRY.registerItem(
            "storage_component_1k", Item::new, new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> STORAGE_COMPONENT_4K = REGISTRY.registerItem(
            "storage_component_4k", Item::new, new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> STORAGE_COMPONENT_16K = REGISTRY.registerItem(
            "storage_component_16k", Item::new, new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> STORAGE_COMPONENT_64K = REGISTRY.registerItem(
            "storage_component_64k", Item::new, new Item.Properties().stacksTo(64));

    /** The Essentia Terminal, placed on a cable. */
    public static final DeferredItem<ItemEssentiaTerminal> ESSENTIA_TERMINAL = REGISTRY.registerItem(
            "essentia_terminal",
            ItemEssentiaTerminal::new,
            new Item.Properties().stacksTo(64).rarity(Rarity.RARE));

    // ---- Essentia machine cores -------------------------------------------
    // Plain items with no behaviour: ingredients for the terminal and the buses, the shape AE2 uses for
    // its annihilation and formation cores.
    /** Pulls essentia one way. Ingredient of the import bus and the terminal. */
    public static final DeferredItem<Item> DIFFUSION_CORE = REGISTRY.registerItem(
            "diffusion_core", Item::new, new Item.Properties().stacksTo(64));

    /** Pulls essentia the other way. Ingredient of the export bus and the terminal. */
    public static final DeferredItem<Item> COALESCENCE_CORE = REGISTRY.registerItem(
            "coalescence_core", Item::new, new Item.Properties().stacksTo(64));

    /** Pulls essentia out of the container it faces and into the ME network. */
    public static final DeferredItem<ItemEssentiaImportBus> ESSENTIA_IMPORT_BUS = REGISTRY.registerItem(
            "essentia_import_bus",
            ItemEssentiaImportBus::new,
            new Item.Properties().stacksTo(64).rarity(Rarity.RARE));

    /** Pushes essentia from the ME network into the container it faces. */
    public static final DeferredItem<ItemEssentiaExportBus> ESSENTIA_EXPORT_BUS = REGISTRY.registerItem(
            "essentia_export_bus",
            ItemEssentiaExportBus::new,
            new Item.Properties().stacksTo(64).rarity(Rarity.RARE));

    /** Makes the essentia container it faces part of the ME network. */
    public static final DeferredItem<ItemEssentiaStorageBus> ESSENTIA_STORAGE_BUS = REGISTRY.registerItem(
            "essentia_storage_bus",
            ItemEssentiaStorageBus::new,
            new Item.Properties().stacksTo(64).rarity(Rarity.RARE));

    /** Redstone that follows how much of one aspect the network holds. */
    public static final DeferredItem<ItemEssentiaLevelEmitter> ESSENTIA_LEVEL_EMITTER = REGISTRY.registerItem(
            "essentia_level_emitter",
            ItemEssentiaLevelEmitter::new,
            new Item.Properties().stacksTo(64).rarity(Rarity.RARE));

    /**
     * The Essentia Terminal, reached from anywhere rather than from a cable. The power capacity is a
     * supplier so the item reads it on demand, the shape AE2's own wireless terminals use.
     */
    public static final DeferredItem<ItemWirelessEssentiaTerminal> WIRELESS_ESSENTIA_TERMINAL =
            REGISTRY.registerItem(
                    "wireless_essentia_terminal",
                    properties -> new ItemWirelessEssentiaTerminal(
                            () -> ItemWirelessEssentiaTerminal.POWER_CAPACITY, properties),
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    private ModItems() {}

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
