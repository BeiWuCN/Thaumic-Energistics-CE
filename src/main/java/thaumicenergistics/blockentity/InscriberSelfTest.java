package thaumicenergistics.blockentity;

import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.Nullable;
import thaumicenergistics.arcane.ThEArcanePattern;
import thaumicenergistics.init.ModBlocks;
import thaumicenergistics.init.ModItems;
import thaumicenergistics.inventory.HandlerKnowledgeCore;

/**
 * Drives the Knowledge Inscriber's save/delete cycle and checks the button's state follows it.
 *
 * <p>This exists because of a bug no other check could see: the machine cached what the grid resolves to,
 * keyed on a signature of the grid, and deleting a stored recipe does not touch the grid - the grid is how the
 * player names the entry. The signature was unchanged, the cache was never invalidated, and the button went on
 * offering "delete" for a recipe that had just been removed.
 *
 * <p>So it walks the whole cycle and asserts the status after each step. Off unless
 * {@code THAUMICENERGISTICS_INSCRIBER_SELFTEST=true}.
 */
public final class InscriberSelfTest {

    /** One run per server, not one per login. */
    private static boolean hasRun;

    /**
     * The key {@code HandlerKnowledgeCore} stores its pattern list under. Repeated rather than shared because
     * the unreadable-entry check is about the bytes in the item, not the constant the handler writes them by.
     */
    private static final String NBT_PATTERNS = "Patterns";

    private InscriberSelfTest() {}

    public static void run(PlayerEvent.PlayerLoggedInEvent event) {
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_INSCRIBER_SELFTEST"))) {
            return;
        }
        if (hasRun) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level == null) {
            return;
        }
        hasRun = true;

        List<String> failures = new ArrayList<>();

        checkTagPatterns(level, failures, player);
        checkRoundTripIsSafe(level, failures);
        checkTaggedRecipeInTheGrid(level, failures, player);
        checkEveryRecipeResolvesFromItsGrid(level, failures);
        checkSavesAndReloads(level, failures);
        checkUnreadableEntriesSurvive(level, failures);

        // A real arcane recipe, taken from the server's own recipe manager rather than invented: the grid has
        // to genuinely resolve, or the test would pass by never reaching the states it checks.
        ThEArcanePattern pattern = anyPattern(level);
        if (pattern == null) {
            report(failures);
            System.out.println("[inscriber] skipped: no arcane recipe resolved, so the cycle cannot be driven");
            return;
        }

        BlockEntityKnowledgeInscriber inscriber = new BlockEntityKnowledgeInscriber(
                // A detached instance, not one in the world: only its level is real, and only because resolving
                // a grid needs the recipe manager. The Y is plain out-of-range rather than a minimum, since
                // BlockPos packs its coordinates and an extreme Y would overflow the packing.
                new BlockPos(0, -4096, 0),
                ModBlocks.KNOWLEDGE_INSCRIBER.get().defaultBlockState());
        inscriber.setLevel(level);

        // The core is what the machine writes into, and an empty slot reports "No Core" whatever the grid.
        inscriber.getInventory().setItem(
                        BlockEntityKnowledgeInscriber.CORE_SLOT, new ItemStack(ModItems.KNOWLEDGE_CORE.get()));

        writeGrid(inscriber, pattern);
        expect(failures, "grid resolves", BlockEntityKnowledgeInscriber.STATUS_ACTIONABLE, inscriber.status());

        inscriber.save(player);
        expect(failures, "after save", BlockEntityKnowledgeInscriber.STATUS_READY, inscriber.status());

        // Asking for the recipe back is how a delete is named, so the button should now offer to delete.
        writeGrid(inscriber, pattern);
        expect(failures, "stored grid re-resolves", BlockEntityKnowledgeInscriber.STATUS_ALREADY_STORED, inscriber.status());

        // The bug: the grid does not move, so this is the one transition that has to come from noticing the
        // core changed.
        inscriber.deleteStored(player);
        expect(failures, "after delete", BlockEntityKnowledgeInscriber.STATUS_ACTIONABLE, inscriber.status());

        HandlerKnowledgeCore core = HandlerKnowledgeCore.of(
                inscriber.getInventory().getItem(BlockEntityKnowledgeInscriber.CORE_SLOT), level.registryAccess());
        if (core == null) {
            failures.add("after delete: the core is no longer a core");
        } else if (core.patternFor(pattern.result()) != null) {
            failures.add("after delete: the core still holds a pattern for " + pattern.result());
        }

        // 6. Save has to work again, or the button would only have moved the dead end along.
        inscriber.save(player);
        writeGrid(inscriber, pattern);
        expect(failures, "re-save after delete", BlockEntityKnowledgeInscriber.STATUS_ALREADY_STORED, inscriber.status());

        report(failures);
    }

    /**
     * Checks that an ore dictionary entry survives being written into a core. The pattern used to store whichever
     * member of the tag was listed first, so a network holding a different member could not craft a recipe it
     * plainly satisfies. The tag has to be read off the recipe, survive the round trip through the core, and make
     * every member a valid input.
     */
    private static void checkTagPatterns(ServerLevel level, List<String> failures, ServerPlayer player) {
        ThEArcanePattern tagged = null;
        int taggedCell = -1;
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            if (output.isEmpty()) {
                continue;
            }
            ThEArcanePattern candidate = ThEArcanePattern.fromRecipe(arcane, output);
            if (candidate == null) {
                continue;
            }
            for (int cell = 0; cell < candidate.grid().size(); cell++) {
                if (candidate.cellTag(cell) != null) {
                    tagged = candidate;
                    taggedCell = cell;
                    break;
                }
            }
            if (tagged != null) {
                break;
            }
        }

        if (tagged == null) {
            // Not a failure: a pack with no tagged arcane recipe has nothing to check. Reported so the
            // silence is visibly a skip rather than a pass.
            System.out.println("[inscriber] no arcane recipe uses an item tag, so the tag path is unchecked");
            return;
        }

        TagKey<Item> tag = tagged.cellTag(taggedCell);
        String where = tagged.result() + " cell " + taggedCell;

        List<ItemStack> choices = tagged.cellChoices(taggedCell);
        if (choices.isEmpty()) {
            failures.add("tag " + tag.location() + " on " + where + " resolves to no items");
        }
        if (choices.size() < 2) {
            // One member proves nothing: the old bug wrote the one member too, and looked identical.
            System.out.println("[inscriber] tag " + tag.location() + " on " + where + " has only "
                    + choices.size() + " member(s); the tag path is weakly checked");
        }

        // A tag that is dropped on save leaves a pattern that can only match the one item it displays.
        CompoundTag saved = tagged.save(level.registryAccess());
        ThEArcanePattern reloaded = ThEArcanePattern.load(level.registryAccess(), saved);
        if (reloaded == null) {
            failures.add("the tagged pattern for " + tagged.result() + " did not survive a save/load");
            return;
        }
        if (reloaded.cellTag(taggedCell) == null) {
            failures.add("tag " + tag.location() + " on " + where + " was lost in the round trip");
        } else if (!reloaded.cellTag(taggedCell).equals(tag)) {
            failures.add("tag on " + where + " came back as " + reloaded.cellTag(taggedCell).location()
                    + " instead of " + tag.location());
        } else if (reloaded.cellChoices(taggedCell).size() != choices.size()) {
            failures.add("tag on " + where + " came back with "
                    + reloaded.cellChoices(taggedCell).size() + " members instead of " + choices.size());
        }

        // And every member is actually accepted, which is what "written as the tag" has to mean in the end.
        for (ItemStack member : choices) {
            if (!tagged.acceptsInputs(List.of(member))) {
                failures.add("tag " + tag.location() + " does not accept its own member " + member);
                break;
            }
        }
    }

    /**
     * Round-trips a sample of arcane patterns through NBT and checks none of them throws - here because of a
     * crash: the tag list held a null for every cell naming a plain item, and the record's compact constructor
     * uses {@code List.copyOf}, which rejects nulls. Nothing threw at encode time; the first pattern read back
     * out of a core threw while a world was loading, so the world would not start.
     */
    private static void checkRoundTripIsSafe(ServerLevel level, List<String> failures) {
        int checked = 0;
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
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
            if (checked >= 40) {
                break;
            }
            checked++;
            try {
                CompoundTag saved = pattern.save(level.registryAccess());
                if (ThEArcanePattern.load(level.registryAccess(), saved) == null) {
                    failures.add("round trip lost the pattern for " + output);
                }
            } catch (RuntimeException e) {
                // Named by recipe: a stack trace alone would not say which shape of pattern the record refused.
                failures.add("round trip threw on " + output + ": " + e);
            }
        }
        if (checked == 0) {
            failures.add("no arcane recipe could be built, so the round trip is unchecked");
        }
    }

    /**
     * An item that is not in the tag, or {@code null} when the registry offered none - the other direction of
     * the tag check. See the caller for why the cell is emptied before the wrong item goes in.
     */
    private static @Nullable ItemStack firstItemOutside(TagKey<Item> tag) {
        for (Item item : BuiltInRegistries.ITEM) {
            // Air is in no tag and is not placeable: it would leave the grid empty, and an empty grid reads as
            // "ready" rather than "no recipe", reporting a failure that is not one.
            if (item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            if (!item.builtInRegistryHolder().is(tag)) {
                return new ItemStack(item);
            }
        }
        return null;
    }

    /**
     * A grid holding {@code placed} at {@code onlyCell} and nothing else, or {@code null} if that grid is still
     * a recipe. Every other cell is emptied on purpose: one item in one cell is unambiguous, where rebuilding
     * the rest of the recipe's grid around the wrong item muddies what is under test.
     */
    private static @Nullable ThEArcanePattern gridWithOnly(
            Level level, ThEArcanePattern pattern, int onlyCell, ItemStack placed) {
        List<ItemStack> cells = new ArrayList<>(ThEArcanePattern.MAX_GRID);
        for (int cell = 0; cell < ThEArcanePattern.MAX_GRID; cell++) {
            cells.add(cell == onlyCell ? placed.copy() : ItemStack.EMPTY);
        }
        ThEArcanePattern single = withGrid(pattern, cells);
        return ThEArcanePattern.resolveGrid(level, cells) == null ? single : null;
    }

    /** A pattern whose grid is a mix of the given cells and emptiness. */
    private static ThEArcanePattern withGrid(ThEArcanePattern pattern, List<ItemStack> cells) {
        return new ThEArcanePattern(
                pattern.result(),
                cells,
                pattern.ingredients(),
                pattern.gridWidth(),
                pattern.gridHeight(),
                pattern.crystals(),
                pattern.baseVis(),
                pattern.research(),
                pattern.researchStage(),
                pattern.cellTags());
    }

    /**
     * Drives a tagged recipe through the inscriber's own grid, the way a player does. The case that matters is
     * the one a player hits constantly without meaning to: JEI's transfer, and a player with a stack in hand,
     * both place a member of the tag they happen to <em>have</em>, not the member the pattern would have picked.
     */
    private static void checkTaggedRecipeInTheGrid(
            ServerLevel level, List<String> failures, ServerPlayer player) {
        ThEArcanePattern tagged = null;
        int taggedCell = -1;
        TagKey<Item> tag = null;
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            if (output.isEmpty()) {
                continue;
            }
            ThEArcanePattern candidate = ThEArcanePattern.fromRecipe(arcane, output);
            if (candidate == null) {
                continue;
            }
            for (int cell = 0; cell < candidate.grid().size(); cell++) {
                TagKey<Item> cellTag = candidate.cellTag(cell);
                if (cellTag != null) {
                    tagged = candidate;
                    taggedCell = cell;
                    tag = cellTag;
                    break;
                }
            }
            if (tagged != null) {
                break;
            }
        }
        if (tagged == null) {
            // Not a failure: a pack with no tagged arcane recipe has nothing to drive. Reported so the silence
            // reads as a skip.
            System.out.println("[inscriber] no tagged arcane recipe, so the inscriber's grid path is unchecked");
            return;
        }

        // A tag member that is not the one the grid displays: what a player actually holds. When the tag has a
        // single member there is none, and the check still runs - only the "different member" half is conditional.
        ItemStack other = null;
        for (ItemStack member : tagged.cellChoices(taggedCell)) {
            if (!ItemStack.isSameItemSameComponents(member, tagged.grid().get(taggedCell))) {
                other = member;
                break;
            }
        }
        System.out.println("[inscriber] tagged grid check: " + tag.location() + " on " + tagged.result()
                + " cell " + taggedCell + ", " + tagged.cellChoices(taggedCell).size() + " member(s)"
                + (other == null ? ", no alternative member to place" : ", placing " + other));

        BlockEntityKnowledgeInscriber inscriber = new BlockEntityKnowledgeInscriber(
                new BlockPos(0, -4096, 0), ModBlocks.KNOWLEDGE_INSCRIBER.get().defaultBlockState());
        inscriber.setLevel(level);
        inscriber.getInventory()
                .setItem(BlockEntityKnowledgeInscriber.CORE_SLOT, new ItemStack(ModItems.KNOWLEDGE_CORE.get()));

        // The grid as the player leaves it: the tag member they had, in the cell the recipe wants.
        List<ItemStack> asPlaced = new ArrayList<>(tagged.grid());
        if (other != null) {
            asPlaced.set(taggedCell, other.copy());
        }
        ThEArcanePattern placed = withGrid(tagged, asPlaced);

        writeGrid(inscriber, placed);
        if (inscriber.status() != BlockEntityKnowledgeInscriber.STATUS_ACTIONABLE) {
            failures.add("the grid for " + tagged.result() + " (holding " + (other == null
                    ? tag.location() : other.toString())
                    + " in the tagged cell) does not resolve, so the recipe cannot be encoded at all");
            return;
        }

        // An item the tag does *not* accept has to be refused: tag matching widened what the cell takes, so a
        // version that accepted anything at all would pass every check above while quietly encoding nonsense.
        ItemStack wrong = firstItemOutside(tag);
        if (wrong != null) {
            ThEArcanePattern onlyWrong = gridWithOnly(level, tagged, taggedCell, wrong);
            if (onlyWrong != null) {
                writeGrid(inscriber, onlyWrong);
                if (inscriber.status() != BlockEntityKnowledgeInscriber.STATUS_NO_RECIPE) {
                    failures.add("a grid holding " + wrong + ", which is not in " + tag.location()
                            + ", reads as " + name(inscriber.status())
                            + " instead of no recipe - the tag cell accepts too much");
                }
            }
            // Put the real grid back before continuing.
            writeGrid(inscriber, placed);
        }

        if (inscriber.save(player) == BlockEntityKnowledgeInscriber.STATUS_RESEARCH_LOCKED) {
            // The recipe the tag resolved to happens to be one this player has not unlocked, which is the
            // machine being right, not being broken. Every assertion below is about what the core kept, so
            // none of them can run: said out loud, because a skipped check and a passed one read the same.
            System.out.println("[inscriber] tagged recipe " + tagged.result()
                    + " is gated by research for this player, so the tagged store path is unchecked");
            return;
        }

        // What the core kept has to be a grid that still stands for the same recipe, or reading the entry
        // back leaves the player looking at a grid that says "no recipe" and cannot be deleted.
        HandlerKnowledgeCore core = HandlerKnowledgeCore.of(
                inscriber.getInventory().getItem(BlockEntityKnowledgeInscriber.CORE_SLOT),
                level.registryAccess());
        if (core == null) {
            failures.add("the core vanished while encoding a tagged recipe");
            return;
        }
        ThEArcanePattern stored = core.patternFor(tagged.result());
        if (stored == null) {
            failures.add("a tagged recipe did not reach the core");
            return;
        }

        // Read it back, exactly as clicking the stored entry does.
        List<ItemStack> readBack = new ArrayList<>(ThEArcanePattern.MAX_GRID);
        for (int cell = 0; cell < ThEArcanePattern.MAX_GRID; cell++) {
            readBack.add(cell < stored.grid().size() ? stored.grid().get(cell) : ItemStack.EMPTY);
        }
        writeGrid(inscriber, stored);
        int afterReadBack = inscriber.status();
        if (afterReadBack != BlockEntityKnowledgeInscriber.STATUS_ALREADY_STORED) {
            failures.add("reading a stored tagged recipe back leaves the grid as " + name(afterReadBack)
                    + " instead of already stored, so the entry cannot be deleted");
        }

        // And the stored grid has to resolve to the same recipe, which is what makes the above meaningful:
        // a grid that resolves to some *other* recipe would report "actionable" and store a second entry.
        if (!ThEArcanePattern.satisfiesGrid(recipeFor(level, tagged), readBack, level)) {
            failures.add("the stored grid for " + tagged.result()
                    + " no longer satisfies its own recipe after a round trip");
        }
    }

    /** The live recipe that produces a pattern's result, for re-checking a stored grid against it. */
    private static IArcaneRecipe recipeFor(Level level, ThEArcanePattern pattern) {
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            if (!output.isEmpty() && ThEArcanePattern.fromRecipe(arcane, output) != null) {
                if (ItemStack.isSameItemSameComponents(output, pattern.result())) {
                    return arcane;
                }
            }
        }
        throw new IllegalStateException("no arcane recipe produces " + pattern.result());
    }

    /**
     * Re-resolves every arcane recipe from the exact grid the inscriber would show for it and reports the ones
     * that do not come back - such a recipe cannot be encoded at all, since the player lays out exactly what the
     * machine showed them and the result well says "invalid".
     */
    private static void checkEveryRecipeResolvesFromItsGrid(ServerLevel level, List<String> failures) {
        int total = 0;
        int unresolved = 0;
        int tagged = 0;
        List<String> examples = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            if (output.isEmpty()) {
                continue;
            }
            ThEArcanePattern pattern;
            try {
                pattern = ThEArcanePattern.fromRecipe(arcane, output);
            } catch (RuntimeException e) {
                // A bare stack trace says nothing about which recipe in a pack of hundreds refused to load.
                unresolved++;
                if (examples.size() < 12) {
                    examples.add(holder.id() + " -> threw while building: " + e);
                }
                continue;
            }
            if (pattern == null) {
                continue;
            }
            total++;
            boolean hasTag = false;
            for (int cell = 0; cell < pattern.grid().size(); cell++) {
                if (pattern.cellTag(cell) != null) {
                    hasTag = true;
                    break;
                }
            }
            if (hasTag) {
                tagged++;
            }

            // Resolving must not throw either; a throw would abort the scan and hide every result behind it.
            ThEArcanePattern resolved;
            try {
                resolved = ThEArcanePattern.resolveGrid(level, pad(pattern.grid()));
            } catch (RuntimeException e) {
                unresolved++;
                if (examples.size() < 12) {
                    examples.add(holder.id() + (hasTag ? " [tag]" : "") + " -> threw while resolving: " + e);
                }
                continue;
            }
            if (resolved == null || !ItemStack.isSameItemSameComponents(resolved.result(), output)) {
                unresolved++;
                // A handful of names is a lead; all of them is noise.
                if (examples.size() < 12) {
                    examples.add(holder.id() + (hasTag ? " [tag]" : "") + " -> "
                            + (resolved == null ? "nothing" : resolved.result().toString()));
                }
            }
        }
        System.out.println("[inscriber] " + total + " arcane recipe(s), " + tagged + " with an item tag, "
                + unresolved + " whose own grid does not resolve back");
        for (String example : examples) {
            System.out.println("[inscriber]   unresolved: " + example);
        }
        if (total == 0) {
            failures.add("no arcane recipe could be built at all");
        }
    }

    /**
     * Saves and reloads the machine's own slots, and checks the grid comes back where it was - the loader used
     * to read the saved list <em>by position</em>, so a grid with gaps came back collapsed and shifted and the
     * player found a layout that matched no recipe. The shape below is deliberately gappy, because a contiguous
     * one would survive the broken loader and prove nothing.
     */
    private static void checkSavesAndReloads(ServerLevel level, List<String> failures) {
        BlockEntityKnowledgeInscriber source = new BlockEntityKnowledgeInscriber(
                new BlockPos(0, -4096, 0), ModBlocks.KNOWLEDGE_INSCRIBER.get().defaultBlockState());
        source.setLevel(level);

        // Gaps on purpose: cells 2, 4, 5 and 8 stay empty, so a by-position read cannot land the rest right.
        ItemStack[] placed = new ItemStack[BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT];
        for (int cell = 0; cell < placed.length; cell++) {
            placed[cell] = cell % 3 == 2 || cell == 4 || cell == 5 ? ItemStack.EMPTY : new ItemStack(Items.STONE);
        }
        for (int cell = 0; cell < placed.length; cell++) {
            source.getInventory()
                    .setItem(BlockEntityKnowledgeInscriber.GRID_SLOT_START + cell, placed[cell]);
        }

        CompoundTag tag = new CompoundTag();
        source.saveAdditional(tag, level.registryAccess());

        // Asserted on the format itself: a round trip through the *fixed* loader alone would pass for the wrong
        // reason if the writer were ever changed to match it. The index each entry carries is the whole
        // difference from the broken format - a bare list cannot express a grid with gaps.
        ListTag saved = tag.getList(ContainerHelper.TAG_ITEMS, Tag.TAG_COMPOUND);
        int expectedEntries = 0;
        for (ItemStack stack : placed) {
            if (!stack.isEmpty()) {
                expectedEntries++;
            }
        }
        if (saved.size() != expectedEntries) {
            failures.add("the save holds " + saved.size() + " item entries for " + expectedEntries
                    + " non-empty slots");
            return;
        }
        for (int entry = 0; entry < saved.size(); entry++) {
            CompoundTag cell = saved.getCompound(entry);
            if (!cell.contains("Slot")) {
                failures.add("saved entry " + entry + " names no slot, so a grid with gaps cannot be restored"
                        + " - the layout will collapse on reload");
                return;
            }
            int named = cell.getByte("Slot");
            if (named < 0 || named >= BlockEntityKnowledgeInscriber.SLOT_COUNT) {
                failures.add("saved entry " + entry + " names slot " + named + ", outside the "
                        + BlockEntityKnowledgeInscriber.SLOT_COUNT + " this build has");
                return;
            }
        }
        // The indices have to point at the cells the items actually came from: that is what the old format
        // could not hold.
        boolean[] seen = new boolean[BlockEntityKnowledgeInscriber.SLOT_COUNT];
        for (int entry = 0; entry < saved.size(); entry++) {
            int named = saved.getCompound(entry).getByte("Slot");
            if (seen[named]) {
                failures.add("two saved entries name slot " + named);
                return;
            }
            seen[named] = true;
            if (!ItemStack.isSameItemSameComponents(placed[named - BlockEntityKnowledgeInscriber.GRID_SLOT_START],
                    ItemStack.parseOptional(level.registryAccess(), saved.getCompound(entry)))) {
                failures.add("saved entry " + entry + " names slot " + named
                        + " but does not hold what was put in that cell");
                return;
            }
        }

        BlockEntityKnowledgeInscriber reloaded = new BlockEntityKnowledgeInscriber(
                new BlockPos(0, -4096, 0), ModBlocks.KNOWLEDGE_INSCRIBER.get().defaultBlockState());
        reloaded.setLevel(level);
        reloaded.loadAdditional(tag, level.registryAccess());

        for (int cell = 0; cell < placed.length; cell++) {
            ItemStack got = reloaded.getInventory()
                    .getItem(BlockEntityKnowledgeInscriber.GRID_SLOT_START + cell);
            if (placed[cell].isEmpty() != got.isEmpty()
                    || (!got.isEmpty() && !ItemStack.isSameItemSameComponents(placed[cell], got))) {
                failures.add("grid cell " + cell + " reloaded as " + (got.isEmpty() ? "empty" : got.toString())
                        + " but was saved as " + (placed[cell].isEmpty() ? "empty" : placed[cell].toString())
                        + " - the saved slots are not being read by index");
                return;
            }
        }
        // And the core slot must not have collected a grid item on the way through.
        if (!reloaded.getInventory()
                .getItem(BlockEntityKnowledgeInscriber.CORE_SLOT)
                .isEmpty()) {
            failures.add("the core slot came back holding a grid item - the saved list was read by position");
        }
    }

    /**
     * A save must not delete what it could not read.
     *
     * <p>{@code HandlerKnowledgeCore.save} rewrites the whole pattern list from what the handler managed to
     * load, so an entry this build cannot read used to be dropped by it - and for good, because the next store
     * or delete of any other pattern wrote the shortened list back. A core whose every entry failed to read
     * therefore came back from a save looking emptied, with the log saying nothing. The entry is broken here
     * the way a schema change or a removed mod breaks one: by taking its result away.
     */
    private static void checkUnreadableEntriesSurvive(ServerLevel level, List<String> failures) {
        ThEArcanePattern first = anotherPattern(level, List.of());
        ThEArcanePattern second = anotherPattern(level, first == null ? List.of() : List.of(first));
        if (first == null || second == null) {
            System.out.println("[inscriber] fewer than two arcane recipes resolve, so the unreadable-entry path"
                    + " is unchecked");
            return;
        }
        ThEArcanePattern third = anotherPattern(level, List.of(first, second));
        if (third == null) {
            System.out.println("[inscriber] no third arcane recipe resolves, so the unreadable-entry path is"
                    + " only partly checked");
            return;
        }

        ItemStack core = new ItemStack(ModItems.KNOWLEDGE_CORE.get());
        HandlerKnowledgeCore handler = HandlerKnowledgeCore.of(core, level.registryAccess());
        if (handler == null) {
            failures.add("a new knowledge core did not wrap into a handler");
            return;
        }
        handler.store(first);
        handler.store(second);

        // Break the first entry the way an unreadable one looks: an entry whose result cannot be parsed.
        CompoundTag data = core.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag list = data.getList(NBT_PATTERNS, Tag.TAG_COMPOUND);
        if (list.size() != 2) {
            failures.add("two stored patterns were saved as " + list.size() + " entr(ies)");
            return;
        }
        CompoundTag broken = list.getCompound(0).copy();
        int brokenVis = broken.getInt("BaseVis");
        broken.remove("Output");
        list.set(0, broken);
        data.put(NBT_PATTERNS, list);
        core.set(DataComponents.CUSTOM_DATA, CustomData.of(data));

        HandlerKnowledgeCore reread = HandlerKnowledgeCore.of(core, level.registryAccess());
        if (reread == null) {
            failures.add("the core stopped being a core after one entry was broken");
            return;
        }
        if (reread.size() != 1 || reread.unreadableCount() != 1) {
            failures.add("a core with one unreadable entry reads as " + reread.size() + " pattern(s) and "
                    + reread.unreadableCount() + " unreadable, expected 1 and 1");
            return;
        }

        // The dangerous write: a store replaces the whole list with what the handler read.
        reread.store(third);
        HandlerKnowledgeCore afterStore = HandlerKnowledgeCore.of(core, level.registryAccess());
        if (afterStore == null
                || afterStore.unreadableCount() != 1
                || afterStore.size() != 2) {
            failures.add("after a store the core reads as "
                    + (afterStore == null ? "no core" : afterStore.size() + " pattern(s) and "
                            + afterStore.unreadableCount() + " unreadable")
                    + ", expected 2 and 1: the entry this build could not read was deleted");
            return;
        }

        // And a delete has to leave it alone as well.
        if (!afterStore.removeByResult(second.result())) {
            failures.add("the core would not give up the pattern it had just stored");
            return;
        }
        HandlerKnowledgeCore afterDelete = HandlerKnowledgeCore.of(core, level.registryAccess());
        if (afterDelete == null
                || afterDelete.unreadableCount() != 1
                || afterDelete.size() != 1) {
            failures.add("after a delete the core reads as "
                    + (afterDelete == null ? "no core" : afterDelete.size() + " pattern(s) and "
                            + afterDelete.unreadableCount() + " unreadable")
                    + ", expected 1 and 1: the entry this build could not read was deleted");
            return;
        }

        // Verbatim, not merely present: the entry comes back with the data it went in with.
        CompoundTag after = core.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag afterList = after.getList(NBT_PATTERNS, Tag.TAG_COMPOUND);
        int preserved = 0;
        for (int i = 0; i < afterList.size(); i++) {
            CompoundTag entry = afterList.getCompound(i);
            if (!entry.contains("Output") && entry.getInt("BaseVis") == brokenVis) {
                preserved++;
            }
        }
        if (preserved != 1) {
            failures.add("the unreadable entry is not in the core verbatim after the store and the delete");
            return;
        }
        System.out.println("[inscriber] an entry this build cannot read survives a store and a delete");
    }

    /**
     * An arcane recipe whose own stored grid resolves back to it and whose result is none of {@code excluded},
     * or {@code null}. The same filter {@link #anyPattern} applies, plus the result check, so two patterns of
     * this test cannot be the same entry.
     */
    private static @Nullable ThEArcanePattern anotherPattern(Level level, List<ThEArcanePattern> excluded) {
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            if (output.isEmpty()) {
                continue;
            }
            ThEArcanePattern candidate = ThEArcanePattern.fromRecipe(arcane, output);
            if (candidate == null || candidate.grid().isEmpty()) {
                continue;
            }
            boolean alreadyUsed = false;
            for (ThEArcanePattern seen : excluded) {
                if (ItemStack.isSameItemSameComponents(seen.result(), candidate.result())) {
                    alreadyUsed = true;
                    break;
                }
            }
            if (alreadyUsed) {
                continue;
            }
            if (ThEArcanePattern.resolveGrid(level, pad(candidate.grid())) != null) {
                return candidate;
            }
        }
        return null;
    }

    /** Writes a pattern's grid into the machine, emptying every cell the pattern does not use. */
    private static void writeGrid(BlockEntityKnowledgeInscriber inscriber, ThEArcanePattern pattern) {
        List<ItemStack> cells = pattern.grid();
        for (int cell = 0; cell < BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT; cell++) {
            ItemStack stack = cell < cells.size() ? cells.get(cell) : ItemStack.EMPTY;
            inscriber.setGridCell(cell, stack);
        }
    }

    /**
     * Any arcane recipe whose own stored grid resolves back to it. A recipe that does not resolve from its own
     * pattern is one the machine could never encode, so asserting against it would test the wrong thing.
     */
    private static ThEArcanePattern anyPattern(Level level) {
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            if (output.isEmpty()) {
                continue;
            }
            ThEArcanePattern pattern = ThEArcanePattern.fromRecipe(arcane, output);
            if (pattern == null || pattern.grid().isEmpty()) {
                continue;
            }
            if (ThEArcanePattern.resolveGrid(level, pad(pattern.grid())) != null) {
                return pattern;
            }
        }
        return null;
    }

    /** A pattern's grid padded to the workbench's nine cells. */
    private static List<ItemStack> pad(List<ItemStack> cells) {
        List<ItemStack> padded = new ArrayList<>(ThEArcanePattern.MAX_GRID);
        for (int i = 0; i < ThEArcanePattern.MAX_GRID; i++) {
            padded.add(i < cells.size() ? cells.get(i) : ItemStack.EMPTY);
        }
        return padded;
    }

    private static void expect(List<String> failures, String what, int expected, int actual) {
        if (expected != actual) {
            failures.add(what + ": expected status " + name(expected) + ", got " + name(actual));
        }
    }

    /** Status codes by name, so a failure says "delete" rather than "4". */
    private static String name(int status) {
        return switch (status) {
            case BlockEntityKnowledgeInscriber.STATUS_READY -> "ready";
            case BlockEntityKnowledgeInscriber.STATUS_ACTIONABLE -> "actionable";
            case BlockEntityKnowledgeInscriber.STATUS_ENCODED -> "encoded";
            case BlockEntityKnowledgeInscriber.STATUS_NO_RECIPE -> "no recipe";
            case BlockEntityKnowledgeInscriber.STATUS_CORE_FULL -> "core full";
            case BlockEntityKnowledgeInscriber.STATUS_ALREADY_STORED -> "already stored";
            case BlockEntityKnowledgeInscriber.STATUS_RESEARCH_LOCKED -> "research locked";
            default -> "status " + status;
        };
    }

    private static void report(List<String> failures) {
        if (failures.isEmpty()) {
            System.out.println("[inscriber] passed");
            return;
        }
        System.out.println("[inscriber] FAILED (" + failures.size() + ")");
        for (String failure : failures) {
            System.out.println("[inscriber]   - " + failure);
        }
    }
}
