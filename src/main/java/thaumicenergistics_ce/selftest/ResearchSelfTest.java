package thaumicenergistics_ce.selftest;

import com.leclowndu93150.thaumaturge.api.research.IResearchCategory;
import com.leclowndu93150.thaumaturge.api.research.IResearchEntry;
import com.leclowndu93150.thaumaturge.api.research.IResearchStage;
import com.leclowndu93150.thaumaturge.api.research.ResearchParent;
import com.leclowndu93150.thaumaturge.api.research.ResearchRequirement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Headless check of the Thaumonomicon research this addon contributes.
 *
 * <ul><li>Off unless {@code THAUMICENERGISTICS_RESEARCH_SELFTEST=true}; runs on
 * {@code ServerStartedEvent}, which supplies the registry and recipe manager.</li>
 * <li>Research data fails silently - an unknown parent, a bad icon, a missing recipe id or
 * language key all pass without throwing - so ask the game, not a compiler.</li></ul>
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
            // Thaumaturge absent or registries renamed: report, do not throw out of the handler.
            ThaumicEnergistics.LOG.error("[research] self-test could not read Thaumaturge's research "
                    + "registries: {}", e.getMessage());
            return;
        }

        IResearchCategory category = categories.get(CATEGORY_KEY)
                .map(Holder.Reference::value)
                .orElse(null);
        if (category == null) {
            // List what the registry does hold: "none" and "seven, none of them ours" differ.
            var present = new ArrayList<String>();
            for (Holder.Reference<IResearchCategory> holder : categories.listElements().toList()) {
                present.add(holder.key().location().toString());
            }
            Collections.sort(present);
            ThaumicEnergistics.LOG.error("[research] FAIL no research category {} - the tab will not "
                    + "appear at all. The registry holds {} categor{}: {}",
                    CATEGORY_KEY.location(),
                    present.size(),
                    present.size() == 1 ? "y" : "ies",
                    present);
            return;
        }

        checkCategory(category, categories, failures);

        // All entry ids that exist, so a reference can be told from a typo.
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

        checkFirstPageReachable(category, byId, entries, failures);

        RecipeManager recipes = server.getRecipeManager();
        for (Map.Entry<ResourceLocation, IResearchEntry> e : byId.entrySet()) {
            ours++;
            checkEntry(e.getKey(), e.getValue(), allEntryIds, recipes, registries, failures);
        }

        report(ours, failures);
    }

    /**
     * The tab's gate and at least one of its entries have to agree, or it opens onto dead nodes: the gate
     * and each entry's parents are set independently, and only one parentless entry must be startable.
     */
    private static void checkFirstPageReachable(
            IResearchCategory category,
            Map<ResourceLocation, IResearchEntry> ours,
            HolderLookup.RegistryLookup<IResearchEntry> allEntries,
            List<String> failures) {

        ResourceLocation gate = category.requiredResearch().orElse(null);
        if (gate == null) {
            // An ungated tab is visible from the start and cannot disagree with anything.
            return;
        }

        Set<ResourceLocation> doneAtGate = new HashSet<>();
        collectAncestors(gate, allEntries, doneAtGate);

        boolean anyStartable = false;
        List<String> refused = new ArrayList<>();
        for (Map.Entry<ResourceLocation, IResearchEntry> e : ours.entrySet()) {
            IResearchEntry entry = e.getValue();
            boolean hangsOffOurs = entry.parents().stream().anyMatch(parent -> ours.containsKey(parent.id()));
            if (hangsOffOurs) {
                continue;
            }
            List<String> unmet = new ArrayList<>();
            for (ResearchParent parent : entry.parents()) {
                if (!doneAtGate.contains(parent.id())) {
                    unmet.add(parent.id().toString());
                }
            }
            if (unmet.isEmpty()) {
                anyStartable = true;
            } else {
                refused.add(e.getKey() + " needs " + unmet);
            }
        }

        if (!anyStartable) {
            failures.add("the category waits for " + gate + ", but no entry of ours is startable once that is"
                    + " done - the tab would open onto nothing the player can research. Entries with no parent"
                    + " of ours and what they are still waiting for: " + refused);
        }
    }

    private static void collectAncestors(
            ResourceLocation id,
            HolderLookup.RegistryLookup<IResearchEntry> allEntries,
            Set<ResourceLocation> into) {

        if (!into.add(id)) {
            return;
        }
        allEntries.get(ResourceKey.create(IResearchEntry.REGISTRY_KEY, id))
                .map(Holder.Reference::value)
                .ifPresent(entry -> {
                    for (ResearchParent parent : entry.parents()) {
                        collectAncestors(parent.id(), allEntries, into);
                    }
                });
    }

    private static void checkCategory(
            IResearchCategory category,
            HolderLookup.RegistryLookup<IResearchCategory> categories,
            List<String> failures) {
        checkTexture(category.icon(), "category icon", failures);
        checkTexture(category.background(), "category background", failures);
        category.overlayBackground()
                .ifPresent(bg -> checkTexture(bg, "category overlay background", failures));

        // The two sizes blitLegacyBackground is called with, for the tab and any overlay it names.
        checkTextureSize(category.background(), 1024, "category background", failures);
        category.overlayBackground()
                .ifPresent(bg -> checkTextureSize(bg, 512, "category overlay background", failures));

        // Two categories sharing an index overlap in the tab strip; compared by identity.
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

        // The label key is "research_category." + namespace + "." + path - it is not free-form.
        // Any other shape resolves to nothing and the tab strip prints the raw key.
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

        // A parent that is neither an entry nor a progress marker makes the node unreachable.
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

            // The recipe list is what puts a recipe page in the book; a wrong id is a blank page.
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

            // Knowledge awarded into a category that does not exist is silently lost.
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

    /** Confirms a texture is on the classpath: a typo here is an invisible sprite, never an error. */
    private static void checkTexture(ResourceLocation id, String what, List<String> failures) {
        String path = "assets/" + id.getNamespace() + "/" + id.getPath();
        if (ResearchSelfTest.class.getClassLoader().getResource(path) == null) {
            failures.add(what + " names texture " + id + " (looked for " + path + "), which does not exist");
        }
    }

    /**
     * The category background must be the size the browser tiles it at: {@code blitLegacyBackground} derives
     * its UV window from that figure without clamping, so another size samples off the edge of the image.
     */
    private static void checkTextureSize(ResourceLocation id, int expected, String what, List<String> failures) {
        String path = "assets/" + id.getNamespace() + "/" + id.getPath();
        try (var in = ResearchSelfTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                return;
            }
            // Read by hand: this runs on the server, where the image loader is client-only.
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
        } catch (IOException e) {
            failures.add(what + " " + id + " could not be read: " + e);
        }
    }

    /** Confirms a translation key resolves; Thaumaturge prints research text by the literal key. */
    private static void checkLang(String key, List<String> failures) {
        if (key == null) {
            return;
        }
        try {
            if (!Language.getInstance().has(key)) {
                failures.add("no translation for key " + key + " - the book would show the raw key");
            }
        } catch (RuntimeException | LinkageError e) {
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
