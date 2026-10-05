package thaumicenergistics_ce;

import appeng.api.AECapabilities;
import appeng.api.features.GridLinkables;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.parts.PartModels;
import appeng.api.parts.RegisterPartCapabilitiesEvent;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.WirelessTerminalItem;
import com.leclowndu93150.thaumaturge.api.aspect.AspectCapabilities;
import com.leclowndu93150.thaumaturge.api.aspect.IAspectSource;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.focus.FocusElements;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.init.ModBlocks;
import thaumicenergistics_ce.init.ModCreativeTab;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.item.ItemGolemWirelessBackpack;
import thaumicenergistics_ce.network.ModNetwork;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import thaumicenergistics_ce.part.PartEssentiaExportBus;
import thaumicenergistics_ce.part.PartEssentiaImportBus;
import thaumicenergistics_ce.part.PartEssentiaLevelEmitter;
import thaumicenergistics_ce.part.PartEssentiaStorageBus;
import thaumicenergistics_ce.part.PartEssentiaTerminal;
import thaumicenergistics_ce.part.PartVisInterface;
import thaumicenergistics_ce.selftest.AssemblerCraftSelfTest;
import thaumicenergistics_ce.selftest.CellPartitionSelfTest;
import thaumicenergistics_ce.selftest.EncoderSelfTest;
import thaumicenergistics_ce.selftest.EssentiaSelfTest;
import thaumicenergistics_ce.selftest.GearSelfTest;
import thaumicenergistics_ce.selftest.InscriberSelfTest;
import thaumicenergistics_ce.selftest.MenuSelfTest;
import thaumicenergistics_ce.selftest.NetworkSelfTest;
import thaumicenergistics_ce.selftest.ResearchSelfTest;
import thaumicenergistics_ce.selftest.SyncSelfTest;
import thaumicenergistics_ce.selftest.VisRelaySelfTest;
import thaumicenergistics_ce.util.ThELog;

/**
 * Thaumic Energistics - bridges Thaumaturge essentia with Applied Energistics 2 ME networks.
 * <ul>
 *   <li>Target: Minecraft 1.21.1, NeoForge 21.1.250, Thaumaturge, AE2 19.2.x.</li>
 *   <li>Arcane autocrafting: the Knowledge Inscriber stores an ingredient grid as an AE2 pattern in a
 *       knowledge core; that machine advertises those recipes and runs them for ambient vis.</li>
 * </ul>
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
        modBus.addListener(this::registerPartCapabilities);
        modBus.addListener(this::registerKeyTypes);
        modBus.addListener(this::commonSetup);

        registerPartModels();
        registerSelfTests();
    }

    /**
     * Registers every part model. The {@code @PartModels} annotation is only a marker in AE2 19, and a
     * location the renderer cannot find is a crash the moment the part is placed, so this walks them.
     */
    private static void registerPartModels() {
        List<ResourceLocation> models = new ArrayList<>();
        models.addAll(PartEssentiaTerminal.MODEL_LOCATIONS);
        models.addAll(PartEssentiaImportBus.MODEL_LOCATIONS);
        models.addAll(PartEssentiaExportBus.MODEL_LOCATIONS);
        models.addAll(PartEssentiaStorageBus.MODEL_LOCATIONS);
        models.addAll(PartEssentiaLevelEmitter.MODEL_LOCATIONS);
        models.addAll(PartArcaneCraftingTerminal.MODEL_LOCATIONS);
        // The P2P part draws itself with AE2's own P2P set, status models included.
        models.addAll(PartVisInterface.MODEL_LOCATIONS);
        PartModels.registerModels(models);
    }

    /**
     * Wires the diagnostic self-tests to the game bus; each self-guards on its own switch.
     * {@code build.gradle} must pass the {@code THAUMICENERGISTICS_*} vars to the game JVM, else none run.
     */
    private static void registerSelfTests() {
        NeoForge.EVENT_BUS.addListener(ThaumicEnergistics::recipeSelfTest);
        NeoForge.EVENT_BUS.addListener(EssentiaSelfTest::run);
        // Waits for the tick that can see Thaumaturge's aspect index, which is published a hop late.
        NeoForge.EVENT_BUS.addListener(EssentiaSelfTest::onServerTick);
        NeoForge.EVENT_BUS.addListener(GearSelfTest::run);
        NeoForge.EVENT_BUS.addListener(MenuSelfTest::run);
        NeoForge.EVENT_BUS.addListener(ResearchSelfTest::run);
        NeoForge.EVENT_BUS.addListener(InscriberSelfTest::run);
        // The Distillation Encoder: the mod's other container whose slots a save can rearrange.
        NeoForge.EVENT_BUS.addListener(EncoderSelfTest::run);
        // Same wait as the essentia battery: the encoder reads the same aspect index.
        NeoForge.EVENT_BUS.addListener(EncoderSelfTest::onServerTick);
        // Runs on login against block entities never added to a level; builds nothing. It answers the
        // server-starting event too, so a headless gate sees these checks without a player.
        NeoForge.EVENT_BUS.addListener(AssemblerCraftSelfTest::run);
        NeoForge.EVENT_BUS.addListener(AssemblerCraftSelfTest::onServerStarted);
        // The cell workbench's partition: a mark has to reach the cell item, survive a save, and then
        // filter it. Nothing a client writes to the grid is ever sent - see PartitionWellPayload.
        NeoForge.EVENT_BUS.addListener(CellPartitionSelfTest::onServerStarted);
        // Read-only check that an assembler can reach a relay block; a lone interface cannot.
        NeoForge.EVENT_BUS.addListener(VisRelaySelfTest::run);
        // The payload codecs: a field dropped while the records moved packages compiles and only shows
        // up as a client drawing something the server never sent.
        NeoForge.EVENT_BUS.addListener(NetworkSelfTest::run);
        // Every synced number, in and out: the save tag, the packet, the menu reading table. A figure
        // that goes out and comes back changed shows up nowhere else until a player reads it.
        NeoForge.EVENT_BUS.addListener(SyncSelfTest::run);
    }

    /**
     * Feeds every arcane recipe's own grid back through the machine's matcher; off by default because
     * it walks all 283 recipes. Each grid must resolve to its own recipe, so a mismatch names a side.
     */
    @SubscribeEvent
    public static void recipeSelfTest(ServerStartedEvent event) {
        // Env var, not -D: a -D on the Gradle command line never reaches the game's JVM.
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_RECIPE_SELFTEST"))) {
            return;
        }
        var level = event.getServer().overworld();
        if (level == null) {
            return;
        }
        int total = 0;
        int failed = 0;
        for (RecipeHolder<?> holder : event.getServer().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            if (output.isEmpty()) {
                continue;
            }
            ThEArcanePattern pattern = ThEArcanePattern.fromRecipe(arcane, output);
            if (pattern == null) {
                continue;
            }
            total++;
            // The grid as the workbench holds it: the recipe's own cells in its own order.
            List<ItemStack> grid = new ArrayList<>(ThEArcanePattern.MAX_GRID);
            for (int i = 0; i < ThEArcanePattern.MAX_GRID; i++) {
                grid.add(ItemStack.EMPTY);
            }
            List<ItemStack> cells = pattern.grid();
            for (int i = 0; i < cells.size() && i < ThEArcanePattern.MAX_GRID; i++) {
                grid.set(i, cells.get(i));
            }
            ThEArcanePattern resolved = ThEArcanePattern.resolveGrid(level, grid);
            boolean ok = resolved != null
                    && ItemStack.isSameItemSameComponents(resolved.result(), output);
            if (ok) {
                continue;
            }
            failed++;
            boolean fromGrid = ThEArcanePattern.satisfiesGrid(arcane, grid, level);
            ThELog.LOG.info("[selftest] FAIL {} gridMatches={} grid={}", holder.id(), fromGrid, pattern.grid());
        }
        ThELog.LOG.info("[selftest] {} arcane recipes, {} resolved to themselves, {} failed",
                total, total - failed, failed);

        // JEI round trip: a broken transfer is indistinguishable from a broken matcher.
        int transfers = 0;
        int broken = 0;
        for (RecipeHolder<?> holder : event.getServer().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            if (output.isEmpty()) {
                continue;
            }
            List<ItemStack> template =
                    thaumicenergistics_ce.integration.jei.ArcaneRecipeTypes.templateFor(holder);
            if (template.isEmpty()) {
                continue;
            }
            transfers++;
            List<ItemStack> grid = new ArrayList<>(ThEArcanePattern.MAX_GRID);
            for (int i = 0; i < ThEArcanePattern.MAX_GRID; i++) {
                grid.add(i < template.size() ? template.get(i) : ItemStack.EMPTY);
            }
            ThEArcanePattern resolved = ThEArcanePattern.resolveGrid(level, grid);
            if (resolved != null && ItemStack.isSameItemSameComponents(resolved.result(), output)) {
                continue;
            }
            broken++;
            if (broken <= 10) {
                ThELog.LOG.info(
                        "[selftest] TRANSFER {} -> {} template={}",
                        holder.id(),
                        resolved == null ? "no match" : resolved.result().getItem().toString(),
                        template);
            }
        }
        ThELog.LOG.info("[selftest] {} JEI templates, {} read back, {} broken",
                transfers, transfers - broken, broken);

        // Loading a stored pattern writes only its own cells; the remaining grid cells stay empty.
        int loads = 0;
        int unreadable = 0;
        for (RecipeHolder<?> holder : event.getServer().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            if (output.isEmpty()) {
                continue;
            }
            ThEArcanePattern pattern = ThEArcanePattern.fromRecipe(arcane, output);
            if (pattern == null) {
                continue;
            }
            loads++;
            // Mirrors MenuKnowledgeInscriber.loadStoredPattern: its cells first, rest empty.
            List<ItemStack> grid = new ArrayList<>(ThEArcanePattern.MAX_GRID);
            List<ItemStack> cells = pattern.grid();
            for (int i = 0; i < ThEArcanePattern.MAX_GRID; i++) {
                grid.add(i < cells.size() ? cells.get(i) : ItemStack.EMPTY);
            }
            ThEArcanePattern resolved = ThEArcanePattern.resolveGrid(level, grid);
            if (resolved != null && ItemStack.isSameItemSameComponents(resolved.result(), output)) {
                continue;
            }
            unreadable++;
            if (unreadable <= 10) {
                ThELog.LOG.info(
                        "[selftest] LOAD {} -> {} cells={}",
                        holder.id(),
                        resolved == null ? "no match" : resolved.result().getItem().toString(),
                        cells);
            }
        }
        ThELog.LOG.info("[selftest] {} stored patterns, {} read back, {} unreadable",
                loads, loads - unreadable, unreadable);
    }

    /**
     * Exposes the mod's grid machines to AE2's network; without {@code IN_WORLD_GRID_NODE_HOST} a
     * machine forms its own isolated grid and the ME terminal never learns about it.
     */
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Implementing the interface is not enough; an unregistered capability leaves it inert.
        for (BlockEntityType<?> type : List.of(
                ModBlockEntities.ARCANE_ASSEMBLER.get(),
                ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(),
                ModBlockEntities.ESSENTIA_PROVIDER.get(),
                ModBlockEntities.INFUSION_PROVIDER.get(),
                ModBlockEntities.INFUSION_MONITOR.get())) {
            event.registerBlockEntity(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST,
                    type,
                    (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);
        }

        // Same STORAGE capability Thaumaturge's jars expose; pipes and neighbours treat it as one.
        event.registerBlockEntity(
                EssentiaCapabilities.STORAGE,
                ModBlockEntities.ESSENTIA_PROVIDER.get(),
                (blockEntity, context) -> (IEssentiaStorage)
                        blockEntity);

        // A container to its neighbours: a jar beside it fills, an alembic beside it empties.
        event.registerBlockEntity(
                EssentiaCapabilities.STORAGE,
                ModBlockEntities.ESSENTIA_PROVIDER_CONNECTION.get(),
                (blockEntity, context) -> (IEssentiaStorage)
                        blockEntity);

        // STORAGE + TRANSPORT so essentia can be pushed in; wildcard suction - see the class note.
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

        // Aspect CONTAINER is the capability an Infusion Altar scans for to draw essentia.
        event.registerBlockEntity(
                AspectCapabilities.CONTAINER,
                ModBlockEntities.INFUSION_PROVIDER.get(),
                (blockEntity, context) -> (IAspectSource)
                        blockEntity);
    }

    /**
     * Exposes the Vis Interface part to Thaumaturge's relay network. Parts are not block entities, so
     * they need AE2's own event: the lookup is answered through the cable bus the part sits on.
     */
    private void registerPartCapabilities(RegisterPartCapabilitiesEvent event) {
        TcAura.registerVisSource(event, PartVisInterface.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Skipping this silently breaks memory card binding; must run after item registration.
            GridLinkables.register(
                    ModItems.GOLEM_WIFI_BACKPACK.get(), ItemGolemWirelessBackpack.LINKABLE_HANDLER);
            GridLinkables.register(
                    ModItems.WIRELESS_ESSENTIA_TERMINAL.get(),
                    WirelessTerminalItem.LINKABLE_HANDLER);
            registerUpgrades();
            ThELog.LOG.info("ThaumicEnergistics common setup complete");
            // Else every terminal craft fails with PAYMENT_UNAVAILABLE - see TerminalWorkbenchVis.
            thaumicenergistics_ce.arcane.TerminalWorkbenchVis.register();
        });
    }

    /**
     * Tells AE2 which upgrade cards this mod's machines and cells take; without it their slots show AE2's
     * "available upgrades" header with nothing under it, since that list comes from AE2's own registry.
     */
    private static void registerUpgrades() {
        // AE2's BasicCellInventory reads all three cards; an essentia aspect carries no NBT, so no fuzzy one.
        // The name key is AE2's own fourth argument and collapses the five tiers into one tooltip line.
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
        // One card per slot: the number is the machine's own slot count, so the two cannot disagree.
        Upgrades.add(
                AEItems.SPEED_CARD,
                ModItems.ARCANE_ASSEMBLER.get(),
                BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT);
        // The count is the bus's own slot count (PartEssentiaImportBus#getUpgradeSlots); capacity is
        // deliberately absent because MenuEssentiaBus keeps 18 fixed config slots and never reads the card.
        for (var bus : List.of(
                ModItems.ESSENTIA_IMPORT_BUS.get(),
                ModItems.ESSENTIA_EXPORT_BUS.get())) {
            Upgrades.add(AEItems.SPEED_CARD, bus, BUS_UPGRADE_SLOTS);
            Upgrades.add(AEItems.REDSTONE_CARD, bus, 1);
        }
    }

    /** The four upgrade slots every essentia bus has; the same number {@code Upgrades.add} should report. */
    private static final int BUS_UPGRADE_SLOTS = 4;

    /** What a card's tooltip calls the whole essentia cell family, at every size. */
    private static final String CELL_UPGRADE_NAME = "item.thaumicenergistics_ce.essentia_cell";

    /**
     * Adds the essentia key type to AE2's registry. Not from the mod constructor: an {@code AEKeyType}
     * is a registry object, so registering before AE2 builds its registry throws.
     */
    private void registerKeyTypes(RegisterEvent event) {
        if (event.getRegistryKey() != AEKeyType.REGISTRY_KEY) {
            return;
        }
        AEKeyTypes.register(AEssentiaKeyType.INSTANCE);
        ThELog.LOG.info("Registered the essentia key type with AE2");
    }
}
