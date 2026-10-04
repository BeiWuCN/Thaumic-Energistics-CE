package thaumicenergistics_ce.selftest;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.StorageCell;
import appeng.me.cells.BasicCellInventory;
import com.leclowndu93150.thaumaturge.api.aspect.AspectCapabilities;
import com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.item.ItemEssentiaCell;

/**
 * Headless self-check of the essentia storage layer, off unless {@code THAUMICENERGISTICS_ESSENTIA_SELFTEST=true}.
 * <ul>
 * <li>Everything it checks fails silently at runtime rather than at compile time: key type registration, AE2's
 * cell logic for a non-item key type, the byte budget and the NBT round trip.
 * <li>Runs on {@code ServerStartedEvent}, which it needs for a level to resolve aspects against.
 * </ul>
 */
public final class EssentiaSelfTest {

    private EssentiaSelfTest() {}

    public static void run(ServerStartedEvent event) {
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_ESSENTIA_SELFTEST"))) {
            return;
        }
        Level level = event.getServer().overworld();
        if (level == null) {
            return;
        }
        List<String> failures = new ArrayList<>();

        checkKeyTypeRegistered(failures);
        checkMyRecipesAreLoaded(event, failures);
        checkRecipeTagsResolve(level, failures);
        checkCrystalIngredientsArePinned(failures);
        checkMachineCapabilities(failures);
        checkAspectIndexResolves(level, failures);
        AEssentiaKey aer = checkKeyIdentity(level, failures);
        AEssentiaKey ignis = checkKeysAreDistinct(level, failures);
        if (aer == null) {
            report(failures);
            return;
        }
        checkByteBudget(failures);
        checkKeyRoundTripsNbt(aer, failures);
        checkStoreAndExtract(aer, ignis, failures);
        report(failures);
    }

    /** Checked by name: a recipe JSON that fails to parse is simply absent, with no error anywhere. */
    private static void checkMyRecipesAreLoaded(ServerStartedEvent event, List<String> failures) {
        var manager = event.getServer().getRecipeManager();
        for (String path : new String[] {
            "storage_casing",
            "storage_component_1k",
            "storage_component_4k",
            "storage_component_16k",
            "storage_component_64k",
            "essentia_cell_1k",
            "essentia_cell_4k",
            "essentia_cell_16k",
            "essentia_cell_64k",
            "knowledge_core",
            "diffusion_core",
            "coalescence_core",
            "essentia_terminal",
            "essentia_import_bus",
            "essentia_export_bus",
            "essentia_storage_bus",
            "essentia_level_emitter",
            "essentia_cell_workbench",
            "essentia_vibration_chamber",
            "essentia_provider",
            "infusion_provider",
            "distillation_encoder",
            "infusion_monitor",
            "essentia_provider_connection",
            "wireless_connector",
            "alkusure86fumo",
            "arcane_crafting_terminal",
            "vis_interface",
            "wireless_essentia_terminal",
            "focus_aewrench",
            "golem_wifi_backpack",
        }) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("thaumicenergistics_ce", path);
            var holder = manager.byKey(id);
            if (holder.isEmpty()) {
                failures.add("recipe " + id + " is not loaded - check the JSON parses");
                continue;
            }
            // A recipe that loaded but produces nothing is worse than one that failed: nothing reports it.
            var result = holder.get().value().getResultItem(dummyProvider());
            if (result.isEmpty()) {
                failures.add("recipe " + id + " loaded but produces nothing");
            }
        }
    }

    /** A misspelt tag, or a convention like {@code c:ingots/iron} that nothing declares, loads fine, appears in
     * JEI, and matches no grid a player can build. */
    private static void checkRecipeTagsResolve(Level level, List<String> failures) {
        var items = level.registryAccess().registryOrThrow(Registries.ITEM);
        for (var entry : new String[][] {
            {"illuminated_panel", "thaumicenergistics_ce:illuminated_panel"},
            {"essentia_cell_glass", "thaumicenergistics_ce:essentia_cell_glass"},
        }) {
            ResourceLocation id = ResourceLocation.parse(entry[1]);
            var tag = ItemTags.create(id);
            var holders = items.getTag(tag);
            if (holders.isEmpty()) {
                failures.add("tag " + id + " (" + entry[0] + ") resolves to nothing - the recipe naming it "
                        + "would never match");
            }
        }
    }

    /** A bare {@code {"item": ...}} naming {@code thaumaturge:essentia_crystal} matches any crystal and draws as
     * an "unknown" one in JEI, silently. Read off the classpath, which keeps whether the aspect was pinned. */
    private static void checkCrystalIngredientsArePinned(List<String> failures) {
        var folder = EssentiaSelfTest.class.getClassLoader()
                .getResource("data/" + ThEIds.MODID + "/recipe");
        if (folder == null || !"file".equals(folder.getProtocol())) {
            // Packed into a jar: not enumerable this way. Skip rather than fail - it already runs in dev.
            return;
        }
        File[] files;
        try {
            files = new File(folder.toURI()).listFiles((d, n) -> n.endsWith(".json"));
        } catch (java.net.URISyntaxException e) {
            return;
        }
        if (files == null) {
            return;
        }

        var gson = new com.google.gson.Gson();
        for (File file : files) {
            String text;
            try {
                text = java.nio.file.Files.readString(file.toPath());
            } catch (IOException e) {
                failures.add("could not read recipe " + file.getName() + ": " + e);
                continue;
            }
            var root = gson.fromJson(text, com.google.gson.JsonElement.class);
            scanForBareCrystals(root, file.getName(), "", failures);
        }
    }

    /** Depth-first for any object that names the crystal item without also naming its aspect. */
    private static void scanForBareCrystals(
            com.google.gson.JsonElement node, String file, String where, List<String> failures) {
        if (node == null) {
            return;
        }
        if (node.isJsonArray()) {
            int i = 0;
            for (com.google.gson.JsonElement child : node.getAsJsonArray()) {
                scanForBareCrystals(child, file, where + "[" + i++ + "]", failures);
            }
            return;
        }
        if (!node.isJsonObject()) {
            return;
        }
        var obj = node.getAsJsonObject();
        boolean namesCrystal = (obj.has("item") && obj.get("item").isJsonPrimitive()
                        && obj.get("item").getAsString().equals("thaumaturge:essentia_crystal"))
                || (obj.has("items") && obj.get("items").isJsonPrimitive()
                        && obj.get("items").getAsString().equals("thaumaturge:essentia_crystal"));
        if (namesCrystal) {
            boolean aspectPinned = obj.has("components")
                    && obj.get("components").toString().contains("crystal_aspect");
            if (!aspectPinned) {
                failures.add(file + " " + where + " asks for an essentia crystal without naming its aspect"
                        + " - JEI will show it as an unknown crystal. Use a neoforge:components filter, as"
                        + " tools/pin_crystals.js writes.");
            }
        }
        for (var entry : obj.entrySet()) {
            scanForBareCrystals(entry.getValue(), file, where + "." + entry.getKey(), failures);
        }
    }

    /** The type has to be in AE2's registry, or every packet write of a key throws. */
    private static void checkKeyTypeRegistered(List<String> failures) {
        if (AEKeyType.fromRawId(AEssentiaKeyType.INSTANCE.getRawId()) != AEssentiaKeyType.INSTANCE) {
            failures.add("key type is not retrievable by its own raw id");
        }
        boolean present = false;
        for (AEKeyType type : AEKeyTypes.getAll()) {
            if (type == AEssentiaKeyType.INSTANCE) {
                present = true;
                break;
            }
        }
        if (!present) {
            failures.add("key type is not in AEKeyTypes.getAll()");
        }
    }

    /** Interning: two keys for one aspect must be the same object, because AE2 groups by reference. */
    private static AEssentiaKey checkKeyIdentity(Level level, List<String> failures) {
        Holder<IAspect> aer = AEssentiaKeyType.aspectOf(level, ResourceLocation.fromNamespaceAndPath("thaumaturge", "aer"));
        if (aer == null) {
            failures.add("aspect thaumaturge:aer did not resolve - is the aspect registry populated?");
            return null;
        }
        AEssentiaKey first = AEssentiaKey.of(aer);
        AEssentiaKey second = AEssentiaKey.of(ResourceLocation.fromNamespaceAndPath("thaumaturge", "aer"));
        if (first != second) {
            failures.add("keys for one aspect are different objects (interning is broken)");
        }
        if (first.getPrimaryKey() != second.getPrimaryKey()) {
            failures.add("getPrimaryKey differs for one aspect - AE2 would store it twice");
        }
        if (!first.equals(second)) {
            failures.add("keys for one aspect are not equal");
        }
        return first;
    }

    private static AEssentiaKey checkKeysAreDistinct(Level level, List<String> failures) {
        Holder<IAspect> ignis = AEssentiaKeyType.aspectOf(level, ResourceLocation.fromNamespaceAndPath("thaumaturge", "ignis"));
        if (ignis == null) {
            failures.add("aspect thaumaturge:ignis did not resolve");
            return null;
        }
        AEssentiaKey key = AEssentiaKey.of(ignis);
        if (key.equals(AEssentiaKey.of(ResourceLocation.fromNamespaceAndPath("thaumaturge", "aer")))) {
            failures.add("aer and ignis compare equal");
        }
        return key;
    }

    /** The documented capacity: 1k bytes at 8 essentia per byte. */
    private static void checkByteBudget(List<String> failures) {
        ItemStack cell = new ItemStack(ModItems.ESSENTIA_CELL_1K.get());
        StorageCell inventory = BasicCellInventory.createInventory(cell, null);
        if (inventory == null) {
            failures.add("AE2 did not build a cell inventory for the 1k essentia cell - "
                    + "is IBasicCellItem wired up?");
            return;
        }
        long bytes = ((BasicCellInventory) inventory).getTotalBytes();
        if (bytes != 1024L) {
            failures.add("1k cell reports " + bytes + " bytes, expected 1024");
        }
        long capacity = bytes * AEssentiaKeyType.INSTANCE.getAmountPerByte();
        if (capacity != 8192L) {
            failures.add("1k cell capacity is " + capacity + " essentia, expected 8192");
        }
    }

    /** A key has to survive the trip through the tag a cell is persisted with. */
    private static void checkKeyRoundTripsNbt(AEssentiaKey key, List<String> failures) {
        var provider = dummyProvider();
        var tag = key.toTag(provider);
        AEKey parsed = AEKey.fromTagGeneric(provider, tag);
        if (parsed == null) {
            failures.add("key did not deserialize from its own tag " + tag);
            return;
        }
        if (!parsed.equals(key)) {
            failures.add("key round-tripped to a different key: " + parsed + " from " + tag);
        }
        if (!(parsed instanceof AEssentiaKey)) {
            failures.add("key round-tripped to the wrong type: " + parsed.getClass() + " from " + tag);
        }
    }

    /** Insert, read back, extract - through AE2's own inventory, not a stand-in. */
    private static void checkStoreAndExtract(AEssentiaKey aer, AEssentiaKey ignis, List<String> failures) {
        if (ignis == null) {
            return;
        }
        ItemStack cell = new ItemStack(ModItems.ESSENTIA_CELL_1K.get());
        StorageCell inventory = BasicCellInventory.createInventory(cell, null);
        if (inventory == null) {
            return;
        }
        long inserted = inventory.insert(aer, 100L, Actionable.MODULATE, source());
        if (inserted != 100L) {
            failures.add("inserted " + inserted + " of 100 essentia");
            return;
        }
        inventory.insert(ignis, 8L, Actionable.MODULATE, source());

        var counter = new KeyCounter();
        inventory.getAvailableStacks(counter);
        long storedAer = counter.get(aer);
        long storedIgnis = counter.get(ignis);
        if (storedAer != 100L) {
            failures.add("read back " + storedAer + " aer, stored 100");
        }
        if (storedIgnis != 8L) {
            failures.add("read back " + storedIgnis + " ignis, stored 8");
        }

        long extracted = inventory.extract(aer, 40L, Actionable.MODULATE, source());
        if (extracted != 40L) {
            failures.add("extracted " + extracted + " aer, asked for 40");
        }

        // The budget is shared: two types cost two type-overheads.
        long types = ((BasicCellInventory) inventory).getStoredItemTypes();
        if (types != 2L) {
            failures.add("cell reports " + types + " stored types, expected 2");
        }

        // And the whole thing has to persist into the cell item and come back.
        ((StorageCell) inventory).persist();
        StorageCell reopened = BasicCellInventory.createInventory(cell, null);
        if (reopened == null) {
            failures.add("cell could not be reopened after persisting");
            return;
        }
        var reCounter = new KeyCounter();
        reopened.getAvailableStacks(reCounter);
        if (reCounter.get(aer) != 60L) {
            failures.add("after persisting, aer is " + reCounter.get(aer) + ", expected 60");
        }
        if (reCounter.get(ignis) != 8L) {
            failures.add("after persisting, ignis is " + reCounter.get(ignis) + ", expected 8");
        }
    }

    private static IActionSource source() {
        return IActionSource.empty();
    }

    /** A registry provider for the tag round trip, taken from the running server. */
    private static HolderLookup.Provider dummyProvider() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("self-test requires a running server");
        }
        return server.registryAccess();
    }

    /** A capability exists where NeoForge has an interface registered for it: an unregistered machine looks
     * switched off and no method on it is ever called, however many interfaces it implements. */
    private static void checkMachineCapabilities(List<String> failures) {
        var origin = new BlockPos(0, 0, 0);

        // Both grid machines must be reachable as grid node hosts, or no cable will ever connect.
        expectGridHost(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                new thaumicenergistics_ce.blockentity.BlockEntityEssentiaVibrationChamber(
                        origin,
                        thaumicenergistics_ce.init.ModBlocks.ESSENTIA_VIBRATION_CHAMBER
                                .get().defaultBlockState()),
                "essentia_vibration_chamber",
                failures);
        expectGridHost(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                new thaumicenergistics_ce.blockentity.BlockEntityEssentiaProvider(
                        origin,
                        thaumicenergistics_ce.init.ModBlocks.ESSENTIA_PROVIDER
                                .get().defaultBlockState()),
                "essentia_provider",
                failures);
        expectGridHost(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                new thaumicenergistics_ce.blockentity.BlockEntityInfusionProvider(
                        origin,
                        thaumicenergistics_ce.init.ModBlocks.INFUSION_PROVIDER
                                .get().defaultBlockState()),
                "infusion_provider",
                failures);

        // The provider is an essentia container, which is how the network puts essentia into it.
        expectGridHost(
                EssentiaCapabilities.STORAGE,
                new thaumicenergistics_ce.blockentity.BlockEntityEssentiaProvider(
                        origin,
                        thaumicenergistics_ce.init.ModBlocks.ESSENTIA_PROVIDER
                                .get().defaultBlockState()),
                "essentia_provider as an essentia container",
                failures);

        // And the infusion provider is an aspect source, which is what an Infusion Altar scans for.
        expectGridHost(
                AspectCapabilities.CONTAINER,
                new thaumicenergistics_ce.blockentity.BlockEntityInfusionProvider(
                        origin,
                        thaumicenergistics_ce.init.ModBlocks.INFUSION_PROVIDER
                                .get().defaultBlockState()),
                "infusion_provider as an aspect source for the infusion altar",
                failures);

        // The encoder has no capability of its own - a missing block entity or menu type is a dead block.
        if (thaumicenergistics_ce.init.ModBlockEntities.DISTILLATION_ENCODER.get() == null) {
            failures.add("the distillation encoder's block entity type is not registered");
        }
        if (thaumicenergistics_ce.init.ModMenuTypes.DISTILLATION_ENCODER.get() == null) {
            failures.add("the distillation encoder's menu type is not registered");
        }

        // Checks the Arcane Crafting Terminal's model files are on the classpath: an unregistered part model
        // crashes the renderer, and AE2's registry is package-private, so this is all that is askable here.
        if (thaumicenergistics_ce.init.ModItems.ARCANE_CRAFTING_TERMINAL.get() == null) {
            failures.add("the arcane crafting terminal's item is not registered");
        }
        if (thaumicenergistics_ce.init.ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get() == null) {
            failures.add("the arcane crafting terminal's menu type is not registered");
        }
        for (var model : new ResourceLocation[] {
                thaumicenergistics_ce.part.PartArcaneCraftingTerminal.MODEL_BASE,
                thaumicenergistics_ce.part.PartArcaneCraftingTerminal.MODEL_OFF,
                thaumicenergistics_ce.part.PartArcaneCraftingTerminal.MODEL_ON,
                thaumicenergistics_ce.part.PartArcaneCraftingTerminal.MODEL_HAS_CHANNEL}) {
            String path = "assets/" + model.getNamespace() + "/models/" + model.getPath() + ".json";
            if (EssentiaSelfTest.class.getClassLoader().getResource(path) == null) {
                failures.add("the arcane crafting terminal's model " + model + " has no file at " + path
                        + " - the part would render as nothing");
            }
        }

        // The infusion monitor's blockstate needs both properties its model selects on: a missing one does not
        // fail, the variants never match and the block renders with no model.
        expectGridHost(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                new thaumicenergistics_ce.blockentity.BlockEntityInfusionMonitor(
                        origin,
                        thaumicenergistics_ce.init.ModBlocks.INFUSION_MONITOR
                                .get().defaultBlockState()),
                "infusion_monitor as a grid node host",
                failures);
        var monitor = new thaumicenergistics_ce.blockentity.BlockEntityInfusionMonitor(
                origin, thaumicenergistics_ce.init.ModBlocks.INFUSION_MONITOR.get().defaultBlockState());
        if (!monitor.hasBook()) {
            // Expected: the slot starts empty. Asking must not throw, and the state must carry the BOOK property.
            var state = thaumicenergistics_ce.init.ModBlocks.INFUSION_MONITOR.get().defaultBlockState();
            if (!state.hasProperty(thaumicenergistics_ce.block.BlockInfusionMonitor.BOOK)) {
                failures.add("the infusion monitor's blockstate is missing its 'book' property - the "
                        + "blockstate file selects a book model on it and would never draw one");
            }
            if (!state.hasProperty(thaumicenergistics_ce.block.BlockInfusionMonitor.NETWORK)) {
                failures.add("the infusion monitor's blockstate is missing its 'network' property - the "
                        + "blockstate file lights the model on it and would never light up");
            }
        }
    }

    /** Asks a one-sided capability, recording a null answer as a failure. Capability and side are raw and
     * {@code null} on purpose: an erased call hands NeoForge's lambda a value it must cast to its context type. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void expectGridHost(
            BlockCapability<?, ?> capability,
            BlockEntity blockEntity,
            String what,
            List<String> failures) {
        Object offered = ((BlockCapability) capability)
                .getCapability(null, blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, null);
        if (offered == null) {
            failures.add("no " + capability.name() + " capability registered for " + what
                    + " - the interface is implemented but nothing will ever ask for it");
        }
    }

    /** The Distillation Encoder offers what Thaumaturge's aspect index reports, and that index is bound by
     * whichever side owns it: unbound, every lookup is empty. Several items are checked, since one would pass. */
    private static void checkAspectIndexResolves(Level level, List<String> failures) {
        var items = level.registryAccess().lookupOrThrow(Registries.ITEM);
        for (String id : new String[] {"minecraft:bone", "minecraft:stone", "minecraft:coal"}) {
            var key = ResourceKey.create(
                    Registries.ITEM,
                    ResourceLocation.parse(id));
            var holder = items.get(key);
            if (holder.isEmpty()) {
                // Not an item in this pack; nothing to learn from it.
                continue;
            }
            ItemStack stack = new ItemStack(holder.get().value());
            var composition = AspectIndexAccess.of(stack);
            if (composition == null || composition.isEmpty()) {
                failures.add("the aspect index reports no aspects for " + id
                        + " - the Distillation Encoder would offer nothing to distil");
            }
        }
    }

    private static void report(List<String> failures) {
        if (failures.isEmpty()) {
            ThaumicEnergistics.LOG.info("[essentia] self-test passed");
            return;
        }
        for (String failure : failures) {
            ThaumicEnergistics.LOG.error("[essentia] FAIL {}", failure);
        }
        ThaumicEnergistics.LOG.error("[essentia] self-test failed with {} problem(s)", failures.size());
    }
}
