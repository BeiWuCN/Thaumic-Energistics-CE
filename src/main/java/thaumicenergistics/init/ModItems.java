package thaumicenergistics.init;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics.ThEIds;
import thaumicenergistics.item.ItemArcaneCraftingTerminal;
import thaumicenergistics.item.ItemArcanePattern;
import thaumicenergistics.item.ItemEssentiaCell;
import thaumicenergistics.item.ItemEssentiaExportBus;
import thaumicenergistics.item.ItemEssentiaImportBus;
import thaumicenergistics.item.ItemEssentiaLevelEmitter;
import thaumicenergistics.item.ItemEssentiaStorageBus;
import thaumicenergistics.item.ItemEssentiaTerminal;
import thaumicenergistics.item.ItemFocusAEWrench;
import thaumicenergistics.item.ItemGolemWirelessBackpack;
import thaumicenergistics.item.ItemKnowledgeCore;
import thaumicenergistics.item.ItemMachineBlock;
import thaumicenergistics.item.ItemVisInterface;
import thaumicenergistics.item.ItemWirelessConnector;
import thaumicenergistics.item.ItemWirelessEssentiaTerminal;

/**
 * Item registration, including the block items for {@link ModBlocks}.
 *
 * <p>Block items live here because NeoForge's {@code registerSimpleBlockItem} helpers belong to the
 * item registry while the block holders come from {@link ModBlocks}, so this class reads both.
 */
public final class ModItems {
    public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(ThEIds.MODID);

    /** Holds the arcane recipes the Knowledge Inscriber writes and the Arcane Assembler reads. */
    public static final DeferredItem<ItemKnowledgeCore> KNOWLEDGE_CORE = REGISTRY.registerItem(
            "knowledge_core", ItemKnowledgeCore::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    /**
     * The item form of one arcane pattern, which exists so AE2's crafting CPU can save and reload its tasks.
     *
     * <p>Built through AE2's own {@code encodedPatternItemBuilder} rather than as a plain item, because the
     * CPU persists a task as a single {@code AEItemKey} tag and rebuilds it with
     * {@code PatternDetailsHelper.decodePattern}, which only answers for an {@code EncodedPatternItem}. A
     * plain item would register, render and tooltip perfectly and still fail that one check - and the failure
     * is silent: the CPU drops the task while keeping the job, so the plan hangs forever. See
     * {@link ItemArcanePattern} and docs/RECIPES-AND-BUSES.md §23.
     *
     * <p>The builder is called on the way up and its result is what gets registered, so the item is not a
     * {@code DeferredItem} of its own class - AE2 hands back the {@code EncodedPatternItem} it constructed
     * around our decoder, and that is the object that has to be in the registry.
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
                    "tooltip.thaumicenergistics.essentia_vibration_chamber.desc",
                    "tooltip.thaumicenergistics.essentia_vibration_chamber.hint"),
            new Item.Properties());

    public static final DeferredItem<BlockItem> ESSENTIA_PROVIDER = REGISTRY.registerItem(
            "essentia_provider",
            properties -> new ItemMachineBlock(
                    ModBlocks.ESSENTIA_PROVIDER.get(),
                    properties,
                    "tooltip.thaumicenergistics.essentia_provider.desc",
                    "tooltip.thaumicenergistics.essentia_provider.hint"),
            new Item.Properties());

    public static final DeferredItem<BlockItem> INFUSION_PROVIDER = REGISTRY.registerItem(
            "infusion_provider",
            properties -> new ItemMachineBlock(
                    ModBlocks.INFUSION_PROVIDER.get(),
                    properties,
                    "tooltip.thaumicenergistics.infusion_provider.desc",
                    "tooltip.thaumicenergistics.infusion_provider.hint"),
            new Item.Properties());

    public static final DeferredItem<BlockItem> DISTILLATION_ENCODER = REGISTRY.registerItem(
            "distillation_encoder",
            properties -> new ItemMachineBlock(
                    ModBlocks.DISTILLATION_ENCODER.get(),
                    properties,
                    "tooltip.thaumicenergistics.distillation_encoder.desc",
                    "tooltip.thaumicenergistics.distillation_encoder.hint"),
            new Item.Properties());

    public static final DeferredItem<BlockItem> INFUSION_MONITOR = REGISTRY.registerItem(
            "infusion_monitor",
            properties -> new ItemMachineBlock(
                    ModBlocks.INFUSION_MONITOR.get(),
                    properties,
                    "tooltip.thaumicenergistics.infusion_monitor.desc",
                    "tooltip.thaumicenergistics.infusion_monitor.hint"),
            new Item.Properties());

    public static final DeferredItem<BlockItem> ESSENTIA_PROVIDER_CONNECTION = REGISTRY.registerItem(
            "essentia_provider_connection",
            properties -> new ItemMachineBlock(
                    ModBlocks.ESSENTIA_PROVIDER_CONNECTION.get(),
                    properties,
                    "tooltip.thaumicenergistics.essentia_provider_connection.desc",
                    "tooltip.thaumicenergistics.essentia_provider_connection.hint"),
            new Item.Properties());

    /** Makes and breaks the links between an Essentia Provider and its receivers. */
    public static final DeferredItem<ItemWirelessConnector> WIRELESS_CONNECTOR = REGISTRY.registerItem(
            "wireless_connector", ItemWirelessConnector::new, new Item.Properties());

    /**
     * The decorative figure.
     *
     * <p>A plain block item, registered explicitly rather than through the simple helper: the helper names
     * the item after the block, and this block's registry name is the only name it has - there is no
     * separate in-world name to give it.
     */
    public static final DeferredItem<BlockItem> ALKUSURE86_FUMO =
            REGISTRY.registerSimpleBlockItem(ModBlocks.ALKUSURE86_FUMO);

    /** The Vis Interface, as a cable part: lets machines draw vis and charge it to the network. */
    public static final DeferredItem<ItemVisInterface> VIS_INTERFACE =
            REGISTRY.registerItem("vis_interface", ItemVisInterface::new, new Item.Properties());

    /**
     * The AE2 wrench, worn as a wand focus.
     *
     * <p>Not a {@code PartItem}: it is not a cable part, it is a focus, and Thaumaturge's foci are ordinary
     * items whose behaviour lives in the package component on the stack.
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
    //
    // Sizes are bytes, as AE2's own components are: the capacity in essentia is eight times the number
    // in the name, so a 1k component holds 8192. See ItemEssentiaCell.

    /**
     * The housing half of a storage cell.
     *
     * <p>A storage cell is two things: a component, which carries the capacity and is made at an arcane
     * workbench, and this casing, which is ordinary crafting. Building cells straight from components -
     * which this mod did first - makes a component look like a finished cell and skips the step that gives
     * a player something to make before they have any vis.
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
    //
    // Plain items: the capacity they describe lives in the cell they are built into, and a component on
    // its own is only an ingredient. The tiers are the byte figures AE2 uses for its own components.

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
    //
    // Plain items, as in the reference build: they are ingredients for the terminal and the buses, and
    // have no behaviour of their own. AE2's annihilation and formation cores are the pattern they follow.

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
     * The Essentia Terminal, reached from anywhere rather than from a cable.
     *
     * <p>The power capacity is a supplier so the item reads it on demand rather than storing it - the same
     * shape AE2's own wireless terminals use.
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
