package thaumicenergistics;

import appeng.api.AECapabilities;
import appeng.api.parts.PartModels;
import appeng.api.stacks.AEKeyTypes;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thaumicenergistics.arcane.ThEArcanePattern;
import thaumicenergistics.essentia.EssentiaSelfTest;
import thaumicenergistics.focus.FocusElements;
import thaumicenergistics.focus.GearSelfTest;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.init.ModBlocks;
import thaumicenergistics.init.ModCreativeTab;
import thaumicenergistics.init.ModItems;
import thaumicenergistics.init.ModMenuTypes;
import thaumicenergistics.integration.ae2.AEssentiaKeyType;
import thaumicenergistics.item.ItemGolemWirelessBackpack;
import thaumicenergistics.network.ModNetwork;
import thaumicenergistics.part.PartArcaneCraftingTerminal;
import thaumicenergistics.part.PartVisInterface;
import thaumicenergistics.part.PartEssentiaExportBus;
import thaumicenergistics.part.PartEssentiaImportBus;
import thaumicenergistics.part.PartEssentiaLevelEmitter;
import thaumicenergistics.part.PartEssentiaStorageBus;
import thaumicenergistics.part.PartEssentiaTerminal;
import thaumicenergistics.research.ResearchSelfTest;

/**
 * Thaumic Energistics - bridges Thaumaturge essentia with Applied Energistics 2 ME networks.
 *
 * <p>Target: Minecraft 1.21.1, NeoForge 21.1.250, Thaumaturge, AE2 19.2.x.
 *
 * <p>Phase 1 implements the arcane autocrafting path. The Knowledge Inscriber turns a grid of ingredients
 * into an AE2 pattern for the arcane recipe they stand for, and stores that recipe in a knowledge core.
 * The Arcane Assembler advertises the core's recipes to the ME network and performs them on demand, paying
 * in ambient vis.
 */
@Mod(ThEIds.MODID)
public final class ThaumicEnergistics {
    public static final Logger LOG = LoggerFactory.getLogger("ThaumicEnergistics");

    public ThaumicEnergistics(IEventBus modBus, ModContainer container) {
        LOG.info("ThaumicEnergistics loading");

        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenuTypes.register(modBus);
        ModCreativeTab.register(modBus);
        ModNetwork.register(modBus);
        FocusElements.register(modBus);

        modBus.addListener(this::registerCapabilities);
        modBus.addListener(this::registerKeyTypes);
        modBus.addListener(this::commonSetup);

        // AE2's part models are not discovered by scanning for the PartModels annotation - that annotation
        // is only a marker. Each location has to be handed over, or the game dies in the renderer with
        // "Trying to use an unregistered part model" the first time a chunk containing the part is drawn.
        // It surfaces as a crash while tesselating and names no code of ours, which is what made it worth
        // a note.
        PartModels.registerModels(
                PartEssentiaTerminal.MODEL_BASE,
                PartEssentiaTerminal.MODEL_OFF,
                PartEssentiaTerminal.MODEL_ON,
                PartEssentiaTerminal.MODEL_HAS_CHANNEL);
        PartModels.registerModels(
                PartEssentiaImportBus.MODEL_BASE,
                PartEssentiaImportBus.MODEL_OFF,
                PartEssentiaImportBus.MODEL_ON,
                PartEssentiaImportBus.MODEL_HAS_CHANNEL);
        PartModels.registerModels(
                PartEssentiaExportBus.MODEL_BASE,
                PartEssentiaExportBus.MODEL_OFF,
                PartEssentiaExportBus.MODEL_ON,
                PartEssentiaExportBus.MODEL_HAS_CHANNEL);
        PartModels.registerModels(
                PartEssentiaStorageBus.MODEL_BASE,
                PartEssentiaStorageBus.MODEL_OFF,
                PartEssentiaStorageBus.MODEL_ON,
                PartEssentiaStorageBus.MODEL_HAS_CHANNEL);
        PartModels.registerModels(
                PartEssentiaLevelEmitter.MODEL_BASE_OFF,
                PartEssentiaLevelEmitter.MODEL_BASE_ON,
                PartEssentiaLevelEmitter.MODEL_STATUS_OFF,
                PartEssentiaLevelEmitter.MODEL_STATUS_ON,
                PartEssentiaLevelEmitter.MODEL_STATUS_HAS_CHANNEL);
        // The Vis Interface is a P2P tunnel part, so its models come from AE2's own P2P model set rather
        // than from a list of four like the terminals - but they still have to be handed over, or the
        // renderer dies the first time a chunk containing one is drawn.
        PartModels.registerModels(PartVisInterface.getModelLocations());
        PartModels.registerModels(
                PartArcaneCraftingTerminal.MODEL_BASE,
                PartArcaneCraftingTerminal.MODEL_OFF,
                PartArcaneCraftingTerminal.MODEL_ON,
                PartArcaneCraftingTerminal.MODEL_HAS_CHANNEL);
        // Diagnostics: each listener checks its own environment variable first and returns unless it is set,
        // so registering all of them costs nothing. README.md lists the switches.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ThaumicEnergistics::recipeSelfTest);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(EssentiaSelfTest::run);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(GearSelfTest::run);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                thaumicenergistics.menu.MenuSelfTest::run);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ResearchSelfTest::run);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                thaumicenergistics.blockentity.InscriberSelfTest::run);
        // And the Distillation Encoder's own slots, which are the other container in this mod that a save can
        // rearrange: see EncoderSelfTest.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                thaumicenergistics.blockentity.EncoderSelfTest::run);
        // One listener: the checks run on login and work on block entities that are never added to a level, so
        // nothing is built in the world and nothing in it is touched. See AssemblerCraftSelfTest.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                thaumicenergistics.menu.AssemblerCraftSelfTest::run);
        // And the relay chain, which is read-only: it walks the player's own base and reports whether an
        // assembler can reach a relay block, since an interface part on its own cannot be reached.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                thaumicenergistics.blockentity.VisRelaySelfTest::run);
    }

    /**
     * Feeds every arcane recipe's own grid back through the machine's matcher, and reports any that fail
     * to come back.
     *
     * <p>Off unless {@code -Dthaumicenergistics.recipeSelfTest=true} is passed, because it walks all 283
     * arcane recipes and is not something a player should pay for. It stays in the mod because it is the
     * only check here that needs no player: a grid built from a recipe's own ingredients has to resolve to
     * that recipe, and when the Knowledge Inscriber reported "Invalid" for grids that plainly matched, the
     * answer came from this and not from a screenshot.
     *
     * <p>It also reports what the recipe itself says about the same grid. The two answers together say
     * which side is wrong: the mod's matcher, or the grid it was handed.
     */
    @net.neoforged.bus.api.SubscribeEvent
    public static void recipeSelfTest(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        // An environment variable rather than a system property: this has to reach the game's own JVM,
        // and a -D on the Gradle command line only reaches Gradle's.
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
            // The grid as the workbench would hold it: the recipe's own cells, where the recipe puts them.
            List<ItemStack> grid = new java.util.ArrayList<>(ThEArcanePattern.MAX_GRID);
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
            LOG.info("[selftest] FAIL {} gridMatches={} grid={}", holder.id(), fromGrid, pattern.grid());
        }
        LOG.info("[selftest] {} arcane recipes, {} resolved to themselves, {} failed",
                total, total - failed, failed);

        // The other half: the round trip a JEI transfer performs. It fills the grid from the recipe's own
        // cells, and the machine then has to read that grid back. A transfer that writes a grid the
        // machine cannot resolve looks exactly like a matcher that does not work, and this says which it
        // is without anyone having to press a button.
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
                    thaumicenergistics.integration.jei.ArcaneRecipeTypes.templateFor(holder);
            if (template.isEmpty()) {
                continue;
            }
            transfers++;
            List<ItemStack> grid = new java.util.ArrayList<>(ThEArcanePattern.MAX_GRID);
            for (int i = 0; i < ThEArcanePattern.MAX_GRID; i++) {
                grid.add(i < template.size() ? template.get(i) : ItemStack.EMPTY);
            }
            ThEArcanePattern resolved = ThEArcanePattern.resolveGrid(level, grid);
            if (resolved != null && ItemStack.isSameItemSameComponents(resolved.result(), output)) {
                continue;
            }
            broken++;
            if (broken <= 10) {
                LOG.info(
                        "[selftest] TRANSFER {} -> {} template={}",
                        holder.id(),
                        resolved == null ? "no match" : resolved.result().getItem().toString(),
                        template);
            }
        }
        LOG.info("[selftest] {} JEI templates, {} read back, {} broken",
                transfers, transfers - broken, broken);

        // The third path, and the one that has no other check on it: reading a stored pattern back onto
        // the grid, which is how the player reaches a recipe again - to look at it, or to delete it.
        //
        // It is not the same as the two above. A stored pattern's grid is written straight into the cells,
        // starting at the first one, because that is where the pattern's own cells are; a shapeless
        // recipe's grid is only as long as its ingredient list, while the grid it is written into is
        // always nine cells. A load that writes the ingredients and leaves the rest of the grid alone
        // produces a grid nothing resolves to, and the player is told the recipe they are looking at is
        // not a recipe at all.
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
            // Exactly what MenuKnowledgeInscriber.loadStoredPattern writes: the pattern's cells from the
            // first, and empty for the rest of the grid.
            List<ItemStack> grid = new java.util.ArrayList<>(ThEArcanePattern.MAX_GRID);
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
                LOG.info(
                        "[selftest] LOAD {} -> {} cells={}",
                        holder.id(),
                        resolved == null ? "no match" : resolved.result().getItem().toString(),
                        cells);
            }
        }
        LOG.info("[selftest] {} stored patterns, {} read back, {} unreadable",
                loads, loads - unreadable, unreadable);
    }

    /**
     * Exposes the assembler to AE2's grid.
     *
     * <p>{@code IN_WORLD_GRID_NODE_HOST} is what lets an ordinary AE2 cable find the assembler and pull
     * it onto the network. Without it the assembler forms its own isolated grid and the ME terminal
     * never learns about the recipes in its knowledge core.
     */
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ARCANE_ASSEMBLER.get(),
                (blockEntity, context) -> (appeng.api.networking.IInWorldGridNodeHost) blockEntity);

        // The machines that extend AE2's AENetworkedBlockEntity still have to be offered as grid node hosts
        // by hand: implementing the interface is not what makes the capability exist. A block entity whose
        // interface is never registered looks exactly like a machine with no cable attached - the node is
        // built, nothing connects, and the block is silently inert.
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(),
                (blockEntity, context) -> (appeng.api.networking.IInWorldGridNodeHost) blockEntity);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ESSENTIA_PROVIDER.get(),
                (blockEntity, context) -> (appeng.api.networking.IInWorldGridNodeHost) blockEntity);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.INFUSION_PROVIDER.get(),
                (blockEntity, context) -> (appeng.api.networking.IInWorldGridNodeHost) blockEntity);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.INFUSION_MONITOR.get(),
                (blockEntity, context) -> (appeng.api.networking.IInWorldGridNodeHost) blockEntity);

        // The provider is also an essentia container, which is how the network puts essentia into it. Same
        // capability Thaumaturge's own jars expose, so a pipe or a neighbouring machine treats it as one.
        event.registerBlockEntity(
                com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities.STORAGE,
                ModBlockEntities.ESSENTIA_PROVIDER.get(),
                (blockEntity, context) -> (com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage)
                        blockEntity);

        // The receiver carries essentia too, and it is a container as far as its neighbours are concerned:
        // that is how a jar beside it gets filled and how an alembic beside it gets emptied.
        event.registerBlockEntity(
                com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities.STORAGE,
                ModBlockEntities.ESSENTIA_PROVIDER_CONNECTION.get(),
                (blockEntity, context) -> (com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage)
                        blockEntity);

        // The vibration chamber is a fuel container as far as its neighbours are concerned: the storage a
        // jar exposes, and the transport a pipe speaks, so essentia can be pushed into it rather than only
        // pulled out of whatever it is standing next to. Its suction is wildcard - see the class note.
        event.registerBlockEntity(
                com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities.STORAGE,
                ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(),
                (blockEntity, context) -> (com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage)
                        blockEntity);
        event.registerBlockEntity(
                com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities.TRANSPORT,
                ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(),
                (blockEntity, context) -> (com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport)
                        blockEntity);

        // The infusion provider is offered as an aspect container instead - the capability an Infusion Altar
        // scans for when it looks for something to draw essentia from. This is what makes the altar find it
        // at all: without the registration the interface is implemented and never asked for.
        event.registerBlockEntity(
                com.leclowndu93150.thaumaturge.api.aspect.AspectCapabilities.CONTAINER,
                ModBlockEntities.INFUSION_PROVIDER.get(),
                (blockEntity, context) -> (com.leclowndu93150.thaumaturge.api.aspect.IAspectSource)
                        blockEntity);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // AE2's memory card asks this registry which items it may link. Registration is by item and the
            // lookup is by identity, so it has to happen once the item exists - not from a field initialiser,
            // which would run while the item registry is still being filled.
            //
            // AE2 does this for its own two wireless terminals in InitGridLinkables; an addon that extends
            // WirelessTerminalItem and skips it gets a terminal that looks and behaves correctly in every
            // respect except that a memory card will not bind it, and nothing says why. Both of this mod's
            // linkable items are registered here for that reason.
            appeng.api.features.GridLinkables.register(
                    ModItems.GOLEM_WIFI_BACKPACK.get(), ItemGolemWirelessBackpack.LINKABLE_HANDLER);
            appeng.api.features.GridLinkables.register(
                    ModItems.WIRELESS_ESSENTIA_TERMINAL.get(),
                    appeng.items.tools.powered.WirelessTerminalItem.LINKABLE_HANDLER);
            LOG.info("ThaumicEnergistics common setup complete");
            // The arcane crafting terminal has no workbench block, so Thaumaturge has nowhere to take a
            // craft's untyped vis cost from unless an addon offers it. Without this registration every
            // arcane craft in the terminal is refused with PAYMENT_UNAVAILABLE - see TerminalWorkbenchVis
            // for why it is registered here rather than from the event Thaumaturge documents.
            thaumicenergistics.arcane.TerminalWorkbenchVis.register();
        });
    }

    /**
     * Adds the essentia key type to AE2's key type registry.
     *
     * <p>Not from this mod's constructor, which is where it first went and where it fails: an
     * {@code AEKeyType} is a registry object, and AE2 builds the registry it belongs to during its own
     * construction. Registering earlier throws {@code AE2 isn't initialized yet} and takes the whole mod
     * down with it - which is how this was found.
     *
     * <p>The registry key is compared before registering because this event fires once per registry, for
     * every registry in the game.
     */
    private void registerKeyTypes(net.neoforged.neoforge.registries.RegisterEvent event) {
        if (event.getRegistryKey() != appeng.api.stacks.AEKeyType.REGISTRY_KEY) {
            return;
        }
        AEKeyTypes.register(AEssentiaKeyType.INSTANCE);
        LOG.info("Registered the essentia key type with AE2");
    }
}
