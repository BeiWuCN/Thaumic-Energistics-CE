package thaumicenergistics_ce.research;

import com.leclowndu93150.thaumaturge.api.research.IResearchCategory;
import com.leclowndu93150.thaumaturge.api.research.IResearchEntry;
import com.leclowndu93150.thaumaturge.api.research.IResearchStage;
import com.leclowndu93150.thaumaturge.api.research.ResearchRequirement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.ThaumicEnergistics;

/**
 * A headless check of the Thaumonomicon research this addon contributes.
 *
 * <p>Off unless {@code THAUMICENERGISTICS_RESEARCH_SELFTEST=true} is set, for the same reason the other
 * self-tests are.
 *
 * <p>It exists because a research definition is pure data, and wrong data does not fail loudly. A parent
 * naming an entry that does not exist makes a node unreachable rather than erroring; an icon naming an
 * item that was never registered draws an empty box; a stage naming a recipe that does not exist shows a
 * blank page where the recipe should be; a missing language key prints the raw key at the player. None of
 * those throw, and none of them are visible to a compiler or to the generator script that wrote the files -
 * only to the game, which is why the game is asked.
 *
 * <p>Runs on {@code ServerStartedEvent} because it needs a loaded registry and recipe manager.
 */
public final class ResearchSelfTest {

    private static final ResourceKey<IResearchCategory> CATEGORY_KEY =
            ResourceKey.create(IResearchCategory.REGISTRY_KEY, ThEIds.id("thaumicenergistics_ce"));

    private ResearchSelfTest() {}

    public static void run(ServerStartedEvent event) {
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_RESEARCH_SELFTEST"))) {
            return;
        }
        MinecraftServer server = event.getServer();
        HolderLookup.Provider registries = server.registryAccess();

        List<String> failures = new ArrayList<>();
        HolderLookup.RegistryLookup<IResearchCategory> categories;
        HolderLookup.RegistryLookup<IResearchEntry> entries;
        try {
            categories = registries.lookupOrThrow(IResearchCategory.REGISTRY_KEY);
            entries = registries.lookupOrThrow(IResearchEntry.REGISTRY_KEY);
        } catch (IllegalStateException e) {
            // Thaumaturge not present, or its registries were renamed. Say so rather than throwing out of
            // the event handler, which would look like a crash in this mod.
            ThaumicEnergistics.LOG.error("[research] self-test could not read Thaumaturge's research "
                    + "registries: {}", e.getMessage());
            return;
        }

        IResearchCategory category = categories.get(CATEGORY_KEY)
                .map(Holder.Reference::value)
                .orElse(null);
        if (category == null) {
            // Report what the registry does hold, not just that ours is missing. Whether an addon's category
            // file is read at all is the question this diagnostic exists to answer, and "no categories" and
            // "seven categories, none of them ours" mean very different things.
            var present = new java.util.ArrayList<String>();
            for (Holder.Reference<IResearchCategory> holder : categories.listElements().toList()) {
                present.add(holder.key().location().toString());
            }
            java.util.Collections.sort(present);
            ThaumicEnergistics.LOG.error("[research] FAIL no research category {} - the tab will not "
                    + "appear at all. The registry holds {} categor{}: {}",
                    CATEGORY_KEY.location(),
                    present.size(),
                    present.size() == 1 ? "y" : "ies",
                    present);
            return;
        }

        checkCategory(category, categories, failures);

        // Every entry id that exists anywhere, so a reference to one can be told from a typo.
        Set<ResourceLocation> allEntryIds = new HashSet<>();
        for (Holder.Reference<IResearchEntry> holder : entries.listElements().toList()) {
            allEntryIds.add(holder.key().location());
        }

        int ours = 0;
        Map<ResourceLocation, IResearchEntry> byId = new HashMap<>();
        for (Holder.Reference<IResearchEntry> holder : entries.listElements().toList()) {
            ResourceLocation id = holder.key().location();
            if (id.getNamespace().equals(ThEIds.MODID)) {
                byId.put(id, holder.value());
            }
        }
        if (byId.isEmpty()) {
            failures.add("the category exists but no entries were loaded under it");
        }

        RecipeManager recipes = server.getRecipeManager();
        for (Map.Entry<ResourceLocation, IResearchEntry> e : byId.entrySet()) {
            ours++;
            checkEntry(e.getKey(), e.getValue(), allEntryIds, recipes, registries, failures);
        }

        report(ours, failures);
    }

    /** The tab itself: its position in the category bar, and the three textures it draws. */
    private static void checkCategory(
            IResearchCategory category,
            HolderLookup.RegistryLookup<IResearchCategory> categories,
            List<String> failures) {
        checkTexture(category.icon(), "category icon", failures);
        checkTexture(category.background(), "category background", failures);
        category.overlayBackground()
                .ifPresent(bg -> checkTexture(bg, "category overlay background", failures));

        // The two sizes blitLegacyBackground is called with, for this category and for any overlay it names.
        checkTextureSize(category.background(), 1024, "category background", failures);
        category.overlayBackground()
                .ifPresent(bg -> checkTextureSize(bg, 512, "category overlay background", failures));

        // Two categories sharing an index would draw on top of each other in the tab strip. Compared by
        // identity because this category came out of the same lookup, so it is the same instance.
        int duplicates = 0;
        for (Holder.Reference<IResearchCategory> holder : categories.listElements().toList()) {
            if (holder.value() != category && holder.value().index() == category.index()) {
                duplicates++;
            }
        }
        if (duplicates > 0) {
            failures.add("category index " + category.index() + " is already used by " + duplicates
                    + " other categor" + (duplicates == 1 ? "y" : "ies")
                    + " - the tabs will overlap");
        }

        if (category.formula().entries().isEmpty()) {
            failures.add("category has an empty aspect formula - a research note for it would cost nothing "
                    + "and always succeed");
        }

        // The tab's own label. This is not the entry-name key and it is not free-form: the browser builds it
        // as "research_category." + namespace + "." + path of the category's id, so for us that is
        // research_category.thaumicenergistics_ce.ThaumicEnergistics. A key in any other shape - the 1.12.2
        // style tc.research_category.THAUMICENERGISTICS, say - resolves to nothing and the tab strip shows
        // the raw key instead of a name.
        checkLang("research_category." + CATEGORY_KEY.location().getNamespace() + "."
                + CATEGORY_KEY.location().getPath(), failures);
    }

    private static void checkEntry(
            ResourceLocation id,
            IResearchEntry entry,
            Set<ResourceLocation> allEntryIds,
            RecipeManager recipes,
            HolderLookup.Provider registries,
            List<String> failures) {

        String where = id.toString();

        if (!entry.category().getKey().location().equals(CATEGORY_KEY.location())) {
            failures.add(where + " is in category " + entry.category().getKey().location()
                    + ", not " + CATEGORY_KEY.location());
        }

        if (entry.nameKey() == null || entry.nameKey().isBlank()) {
            failures.add(where + " has no name key - the book would show nothing");
        } else {
            checkLang(entry.nameKey(), failures);
        }

        if (entry.icons().isEmpty()) {
            failures.add(where + " has no icon - the node would be blank");
        }
        for (var icon : entry.icons()) {
            if (icon.texture()) {
                checkTexture(icon.id(), where + " icon", failures);
            } else {
                checkItem(icon.id(), where + " icon", registries, failures);
            }
        }

        // A parent that is not an entry and not one of Thaumaturge's progress markers makes the node
        // unreachable: the book only draws an entry once a parent has been completed.
        for (var parent : entry.parents()) {
            ResourceLocation parentId = parent.id();
            if (!allEntryIds.contains(parentId) && !isProgressMarker(parentId)) {
                failures.add(where + " has parent " + parentId + " which is neither a research entry nor an "
                        + "unlock marker - the node can never be reached");
            }
        }

        if (entry.stages().isEmpty()) {
            failures.add(where + " has no stages - the book would open an empty page");
        }
        for (int i = 0; i < entry.stages().size(); i++) {
            IResearchStage stage = entry.stages().get(i);
            String stageWhere = where + " stage " + i;

            if (stage.textKey() == null || stage.textKey().isBlank()) {
                failures.add(stageWhere + " has no text key");
            } else {
                checkLang(stage.textKey(), failures);
            }

            // The recipe list is what puts a recipe page in the book. A wrong id there is a blank page.
            for (ResourceLocation recipe : stage.recipes()) {
                if (recipes.byKey(recipe).isEmpty()) {
                    failures.add(stageWhere + " names recipe " + recipe + ", which does not exist");
                }
            }

            for (var requirement : stage.obtain()) {
                checkRequirement(stageWhere + " obtain", requirement, registries, failures);
            }
            for (var requirement : stage.craft()) {
                checkRequirement(stageWhere + " craft", requirement, registries, failures);
            }

            // Knowledge is awarded into a category; awarding into one that does not exist is silently lost.
            for (var reward : stage.knowledge()) {
                if (!reward.category().getKey().location().equals(CATEGORY_KEY.location())) {
                    failures.add(stageWhere + " awards knowledge into " + reward.category().getKey().location()
                            + " instead of " + CATEGORY_KEY.location());
                }
            }
            for (var reward : stage.requiredKnowledge()) {
                if (!reward.category().getKey().location().equals(CATEGORY_KEY.location())) {
                    failures.add(stageWhere + " requires knowledge from " + reward.category().getKey().location()
                            + ", which this addon never awards - the stage could never be completed");
                }
            }

            for (ResourceLocation required : stage.requiredResearch()) {
                if (!allEntryIds.contains(required) && !isProgressMarker(required)) {
                    failures.add(stageWhere + " requires research " + required + ", which does not exist");
                }
            }
        }

        for (var addendum : entry.addenda()) {
            String addWhere = where + " addendum";
            if (addendum.textKey() == null || addendum.textKey().isBlank()) {
                failures.add(addWhere + " has no text key");
            } else {
                checkLang(addendum.textKey(), failures);
            }
            for (ResourceLocation recipe : addendum.recipes()) {
                if (recipes.byKey(recipe).isEmpty()) {
                    failures.add(addWhere + " names recipe " + recipe + ", which does not exist");
                }
            }
            for (ResourceLocation required : addendum.requiredResearch()) {
                if (!allEntryIds.contains(required) && !isProgressMarker(required)) {
                    failures.add(addWhere + " requires research " + required + ", which does not exist");
                }
            }
        }
    }

    private static void checkRequirement(
            String where,
            ResearchRequirement requirement,
            HolderLookup.Provider registries,
            List<String> failures) {
        if (requirement.amount() <= 0) {
            failures.add(where + " asks for a non-positive amount (" + requirement.amount()
                    + ") - the stage would be trivially satisfied");
        }
        HolderSet<Item> items = requirement.items();
        if (items.size() == 0) {
            failures.add(where + " names no items, so nothing can ever satisfy it");
            return;
        }
        for (Holder<Item> holder : items) {
            if (holder.unwrapKey().isEmpty()) {
                failures.add(where + " has an item that is not in the item registry");
            }
        }
    }

    /** {@code thaumaturge:scanned/...} and {@code thaumaturge:unlock_*} are progress flags, not entries. */
    private static boolean isProgressMarker(ResourceLocation id) {
        if (!id.getNamespace().equals("thaumaturge")) {
            return false;
        }
        String path = id.getPath();
        return path.startsWith("unlock_") || path.startsWith("scanned/");
    }

    private static void checkItem(
            ResourceLocation id, String what, HolderLookup.Provider registries, List<String> failures) {
        var items = registries.lookupOrThrow(Registries.ITEM);
        if (items.get(ResourceKey.create(Registries.ITEM, id)).isEmpty()) {
            failures.add(what + " names item " + id + ", which is not registered - it would draw nothing");
        }
    }

    /**
     * Confirms a texture is actually on the classpath.
     *
     * <p>A research icon is a plain {@code ResourceLocation} resolved to a file, with no registry to catch
     * a mistake, so a typo here is an invisible sprite rather than an error.
     */
    private static void checkTexture(ResourceLocation id, String what, List<String> failures) {
        String path = "assets/" + id.getNamespace() + "/" + id.getPath();
        if (ResearchSelfTest.class.getClassLoader().getResource(path) == null) {
            failures.add(what + " names texture " + id + " (looked for " + path + "), which does not exist");
        }
    }

    /**
     * The category background has to be the size the browser tiles it at.
     *
     * <p>{@code ThaumonomiconBrowserScreen#blitLegacyBackground} blits the background as a {@code size x
     * size} texture - 1024 for the background, 512 for the overlay - with a UV window worked out from that
     * figure and nothing clamping it to the image. A file of any other size is sampled as though it were
     * that size, so the window runs off the edge of the texture and the panel is drawn with whatever the
     * sampler returns out there. Thaumaturge's own backgrounds are 1024x1024 and its overlay 512x512.
     *
     * <p>Read from the PNG's own header rather than by loading it: this runs on the server, and the image
     * loader is client-only.
     */
    private static void checkTextureSize(ResourceLocation id, int expected, String what, List<String> failures) {
        String path = "assets/" + id.getNamespace() + "/" + id.getPath();
        try (var in = ResearchSelfTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                return; // checkTexture already reported it
            }
            byte[] header = in.readNBytes(24);
            if (header.length < 24
                    || header[0] != (byte) 0x89 || header[1] != 'P' || header[2] != 'N' || header[3] != 'G') {
                failures.add(what + " " + id + " is not a PNG");
                return;
            }
            int width = ((header[16] & 0xff) << 24) | ((header[17] & 0xff) << 16)
                    | ((header[18] & 0xff) << 8) | (header[19] & 0xff);
            int height = ((header[20] & 0xff) << 24) | ((header[21] & 0xff) << 16)
                    | ((header[22] & 0xff) << 8) | (header[23] & 0xff);
            if (width != expected || height != expected) {
                failures.add(what + " " + id + " is " + width + "x" + height + ", but the browser blits it as "
                        + expected + "x" + expected + " - the panel would sample off the edge of the image. "
                        + "tools/resize_texture.js <file> " + expected);
            }
        } catch (java.io.IOException e) {
            failures.add(what + " " + id + " could not be read: " + e);
        }
    }

    /**
     * Confirms a translation key resolves.
     *
     * <p>Thaumaturge prints a research entry's title and stage text by the literal key in the data file, so
     * a key with no translation shows the player "tc.research_name.SOMETHING" in the middle of the book.
     */
    private static void checkLang(String key, List<String> failures) {
        if (key == null) {
            return;
        }
        try {
            if (!net.minecraft.locale.Language.getInstance().has(key)) {
                failures.add("no translation for key " + key + " - the book would show the raw key");
            }
        } catch (RuntimeException | LinkageError e) {
            // A client-only language class would make this an unreliable check; skip rather than fail the
            // whole run for it.
        }
    }

    private static void report(int entries, List<String> failures) {
        if (failures.isEmpty()) {
            ThaumicEnergistics.LOG.info("[research] self-test passed ({} entries)", entries);
            return;
        }
        for (String failure : failures) {
            ThaumicEnergistics.LOG.error("[research] FAIL {}", failure);
        }
        ThaumicEnergistics.LOG.error("[research] self-test failed with {} problem(s) across {} entries",
                failures.size(), entries);
    }
}
