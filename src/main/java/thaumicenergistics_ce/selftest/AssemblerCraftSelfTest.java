package thaumicenergistics_ce.selftest;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.arcane.ArcanePatternDetails;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.init.ModBlocks;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;

/**
 * Checks the Arcane Assembler's craft bookkeeping against the arcane recipe list and a save/load
 * round trip; off unless {@code THAUMICENERGISTICS_ASSEMBLER_SELFTEST=true}.
 * <ul>
 *   <li>Puts nothing into the world: the machines it builds are never added to a level.
 *   <li>Checks that the machine can bank the priciest recipe in the pack (1728 vis, five times the
 *       buffer's target - a machine that cannot pay holds the job for ever and AE2's CPU skips it),
 *       and that a craft interrupted by a save comes back <em>able to finish</em>.
 * </ul>
 */
public final class AssemblerCraftSelfTest {

    /** One run per server: the checks do not depend on the player and are cheap. */
    private static boolean hasRun;

    private AssemblerCraftSelfTest() {}

    public static void run(PlayerEvent.PlayerLoggedInEvent event) {
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_ASSEMBLER_SELFTEST"))) {
            return;
        }
        if (hasRun || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        hasRun = true;

        ServerLevel level = player.serverLevel();
        ThEArcanePattern priciest = priciestRecipe(level);
        if (priciest == null) {
            ThaumicEnergistics.LOG.info(
                    "[asmtest] FAIL no arcane recipe could be read, so nothing was checked");
            return;
        }

        // Never added to a level; loadWithComponents is the chunk-load call the game makes.
        BlockPos pos = player.blockPosition();
        BlockEntityArcaneAssembler machine =
                new BlockEntityArcaneAssembler(pos, ModBlocks.ARCANE_ASSEMBLER.get().defaultBlockState());
        machine.forcePatternForTest(priciest);

        CompoundTag saved = machine.saveWithoutMetadata(level.registryAccess());
        BlockEntityArcaneAssembler reloaded =
                new BlockEntityArcaneAssembler(pos, ModBlocks.ARCANE_ASSEMBLER.get().defaultBlockState());
        reloaded.loadWithComponents(saved, level.registryAccess());

        ThaumicEnergistics.LOG.info(
                "[asmtest] save/load round trip: saved [{}] reloaded [{}]",
                machine.resumeReportForTest(),
                reloaded.resumeReportForTest());

        // That round trip only proved the craft state survived - the pattern did not, and an AE2 CPU
        // skips a machine that offers none. The core goes in as the game's own load does.
        reloaded.setItemForTest(BlockEntityArcaneAssembler.coreSlotForTest(), knowledgeCoreHolding(priciest, level));
        reloaded.recoverForTest(level);
        ThaumicEnergistics.LOG.info(
                "[asmtest] after a reload the machine offers {} pattern(s), and the craft in its well is {}",
                reloaded.getAvailablePatterns().size(),
                reloaded.resumeReportForTest());

        checkCoreSurvivesTheRoundTrip(level);
        sweepEveryRecipe(level);
        checkCpuTaskRoundTrip(level);
        checkTooltipReasonsTranslated();
    }

    /**
     * The core must come back out of a save with every pattern it went in with. The core goes in
     * <em>before</em> the save: a core inserted after the load proves only that the slot takes one.
     */
    private static void checkCoreSurvivesTheRoundTrip(ServerLevel level) {
        ThEArcanePattern pattern = priciestRecipe(level);
        if (pattern == null) {
            ThaumicEnergistics.LOG.warn("[asmtest] no arcane recipe to put in a core, so the core round trip"
                    + " is unchecked");
            return;
        }
        ItemStack core = knowledgeCoreHolding(pattern, level);
        if (core.isEmpty()) {
            ThaumicEnergistics.LOG.warn("[asmtest] the core could not be written, so the core round trip is"
                    + " unchecked");
            return;
        }
        BlockPos pos = BlockPos.ZERO;
        BlockEntityArcaneAssembler machine =
                new BlockEntityArcaneAssembler(pos, ModBlocks.ARCANE_ASSEMBLER.get().defaultBlockState());
        machine.setItemForTest(BlockEntityArcaneAssembler.coreSlotForTest(), core);
        CompoundTag saved = machine.saveWithoutMetadata(level.registryAccess());

        BlockEntityArcaneAssembler reloaded =
                new BlockEntityArcaneAssembler(pos, ModBlocks.ARCANE_ASSEMBLER.get().defaultBlockState());
        reloaded.loadWithComponents(saved, level.registryAccess());
        // A level only because offering patterns reads the core through one; the tag needed none.
        reloaded.setLevel(level);

        ItemStack back = reloaded.getInventory().getItem(BlockEntityArcaneAssembler.coreSlotForTest());
        HandlerKnowledgeCore handler = HandlerKnowledgeCore.of(back, level.registryAccess());
        int stored = handler == null ? -1 : handler.size();
        int offered = reloaded.getAvailablePatterns().size();
        // The whole stack: a core back without its custom data is the loss this check exists for.
        if (!ItemStack.isSameItemSameComponents(core, back)) {
            ThaumicEnergistics.LOG.warn(
                    "[asmtest] FAIL the core did not survive the round trip: saved {} [{}], reloaded {} [{}]",
                    core,
                    core.getComponentsPatch(),
                    back,
                    back.getComponentsPatch());
            return;
        }
        ThaumicEnergistics.LOG.info(
                "[asmtest] core round trip: {} pattern(s) after the reload, {} offered, stack identical to the"
                        + " one saved",
                stored,
                offered);
        if (offered != stored) {
            ThaumicEnergistics.LOG.warn(
                    "[asmtest] FAIL the reloaded machine offers {} of the {} pattern(s) the reloaded core holds",
                    offered,
                    stored);
        }
    }

    /**
     * Checks that every reason the tooltip can show is translated in each shipped language file: a missing
     * key shows a Chinese player an English sentence.
     */
    private static void checkTooltipReasonsTranslated() {
        List<String> keys = tooltipKeys();
        for (String lang : List.of("en_us", "zh_cn")) {
            Set<String> translated = langKeys(lang);
            if (translated == null) {
                ThaumicEnergistics.LOG.warn("[asmtest] tooltip reasons: could not read {}.json", lang);
                continue;
            }
            List<String> missing = new ArrayList<>();
            for (String key : keys) {
                if (!translated.contains(key)) {
                    missing.add(key.replace("jade.thaumicenergistics_ce.arcane_assembler.", ""));
                }
            }
            if (missing.isEmpty()) {
                ThaumicEnergistics.LOG.info(
                        "[asmtest] tooltip reasons: all {} key(s) present in {}", keys.size(), lang);
            } else {
                ThaumicEnergistics.LOG.warn(
                        "[asmtest] tooltip reasons: {} of {} key(s) MISSING from {}: {}",
                        missing.size(),
                        keys.size(),
                        lang,
                        missing);
            }
        }
        checkLanguageFilesAgree();
    }

    /**
     * Every key the assembler's Jade tooltip can ask for, fully qualified. The labels cannot come from the
     * machine: {@code ArcaneAssemblerProvider} is in an optional-Jade package, and this self-test runs
     * whether or not Jade is installed.
     */
    private static List<String> tooltipKeys() {
        List<String> keys = new ArrayList<>(BlockEntityArcaneAssembler.tooltipReasonKeys());
        for (String label : List.of(
                "crafting", "discount", "patterns", "produces", "speed", "vis", "waiting", "refused")) {
            keys.add("jade.thaumicenergistics_ce.arcane_assembler." + label);
        }
        return keys;
    }

    /** Checks that the two shipped language files have the same keys: a string in one and not the other
     * falls back to English or to the raw key. */
    private static void checkLanguageFilesAgree() {
        Set<String> english = langKeys("en_us");
        Set<String> chinese = langKeys("zh_cn");
        if (english == null || chinese == null) {
            return;
        }
        List<String> onlyEnglish = new ArrayList<>();
        for (String key : english) {
            if (!chinese.contains(key)) {
                onlyEnglish.add(key);
            }
        }
        // Only one direction is a defect: en_us missing from zh_cn falls back to English; a zh_cn-only
        // key is normally an overlay for another mod's keys (42 of them), not a defect.
        int overlay = 0;
        List<String> oursOnly = new ArrayList<>();
        for (String key : chinese) {
            if (english.contains(key)) {
                continue;
            }
            overlay++;
            if (key.contains("thaumicenergistics_ce") || key.startsWith("tc.")) {
                oursOnly.add(key);
            }
        }
        if (!onlyEnglish.isEmpty()) {
            ThaumicEnergistics.LOG.warn(
                    "[asmtest] {} key(s) in en_us have no zh_cn translation: {}", onlyEnglish.size(), onlyEnglish);
        }
        if (!oursOnly.isEmpty()) {
            ThaumicEnergistics.LOG.warn(
                    "[asmtest] {} key(s) in zh_cn look like ours but are in no en_us: {}", oursOnly.size(), oursOnly);
        }
        if (onlyEnglish.isEmpty() && oursOnly.isEmpty()) {
            ThaumicEnergistics.LOG.info(
                    "[asmtest] language files agree: {} key(s) in en_us, all of them in zh_cn, plus {} key(s) "
                            + "in zh_cn that translate other mods",
                    english.size(), overlay);
        }
    }

    /** The key set of one shipped language file, or {@code null} if it cannot be read. */
    private static @Nullable Set<String> langKeys(String lang) {
        String path = "/assets/thaumicenergistics_ce/lang/" + lang + ".json";
        try (var stream = AssemblerCraftSelfTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                return null;
            }
            JsonObject json = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            return json.keySet();
        } catch (IOException | RuntimeException e) {
            ThaumicEnergistics.LOG.warn("[asmtest] could not read {}: {}", path, e.toString());
            return null;
        }
    }

    /**
     * Reproduces what an AE2 crafting CPU does to a task when the world is saved and reloaded. A null drop
     * leaves the job's output and waiting-for list intact - the plan survives, the work does not.
     */
    private static void checkCpuTaskRoundTrip(ServerLevel level) {
        ThEArcanePattern recipe = priciestRecipe(level);
        if (recipe == null) {
            ThaumicEnergistics.LOG.warn("[asmtest] no arcane recipe to test the CPU task round trip with");
            return;
        }
        ArcanePatternDetails details = ArcanePatternDetails.of(recipe, level.registryAccess());
        if (details == null) {
            ThaumicEnergistics.LOG.warn("[asmtest] the priciest recipe has no AE2 view to test with");
            return;
        }

        // Exactly what ExecutingCraftingJob writes, and what it reads back.
        CompoundTag asCpuSavesIt = details.getDefinition().toTag(level.registryAccess());
        var decoded = PatternDetailsHelper.decodePattern(
                AEItemKey.fromTag(level.registryAccess(), asCpuSavesIt), level);

        // Decoding only half of it: AE2 looks the machine up in a HashMap keyed by IPatternDetails
        // equals/hashCode, so a decoded task unequal to the offered one matches no machine.
        ThaumicEnergistics.LOG.info(
                "[asmtest] CPU task round trip: definition={} isEncodedPattern={} decodedBack={} equalToOffered={}",
                details.getDefinition(),
                PatternDetailsHelper.isEncodedPattern(
                        details.getDefinition().getReadOnlyStack()),
                decoded == null ? "NULL - the CPU drops this task on load" : decoded.getClass().getSimpleName(),
                decoded == null ? "n/a" : String.valueOf(details.equals(decoded)));

        // When they differ it is the definition tags alone: equals() compares exactly that field.
        if (decoded instanceof IPatternDetails other) {
            CompoundTag offered = details.getDefinition().toTag(level.registryAccess());
            CompoundTag decodedTag = other.getDefinition().toTag(level.registryAccess());
            if (!offered.equals(decodedTag)) {
                ThaumicEnergistics.LOG.warn("[asmtest] offered task tag: {}", offered);
                ThaumicEnergistics.LOG.warn("[asmtest] decoded task tag: {}", decodedTag);
            }
        }
    }

    /**
     * Encodes, saves, reloads and re-recognises every arcane recipe in the pack, and counts what is lost.
     * <ul>
     *   <li>Counted, not noticed: one pattern surviving says nothing about the next, and each failure removes
     *       a single recipe while the rest keep working.
     *   <li>Counted per stage because the stages point at different files - lost between the core and the
     *       reload is an encoding fault, lost at recognition an adapter fault.
     * </ul>
     */
    private static void sweepEveryRecipe(ServerLevel level) {
        int recipes = 0;
        int unencodable = 0;
        int lostInReload = 0;
        int unrecognised = 0;
        String firstUnrecognised = null;

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
                unencodable++;
                continue;
            }
            recipes++;

            // What a knowledge core does: written by the core, read back by a fresh handler.
            CompoundTag saved = pattern.save(level.registryAccess());
            ThEArcanePattern reloaded = ThEArcanePattern.load(level.registryAccess(), saved);
            if (reloaded == null) {
                lostInReload++;
                continue;
            }

            // Then exactly what the assembler asks when deciding whether to offer it.
            StringBuilder refusal = new StringBuilder();
            if (ArcanePatternDetails.of(reloaded, level.registryAccess(), refusal::append) == null) {
                unrecognised++;
                if (firstUnrecognised == null) {
                    firstUnrecognised = output.getHoverName().getString() + " - " + refusal;
                }
            }
        }

        ThaumicEnergistics.LOG.info(
                "[asmtest] pattern sweep: {} arcane recipe(s); {} unencodable; {} lost in save/load; "
                        + "{} refused by the AE2 adapter. First refusal: {}",
                recipes,
                unencodable,
                lostInReload,
                unrecognised,
                firstUnrecognised == null ? "(none)" : firstUnrecognised);
    }

    /** A knowledge core holding {@code pattern}, built through the mod's own API so the encoding is real. */
    private static ItemStack knowledgeCoreHolding(ThEArcanePattern pattern, ServerLevel level) {
        ItemStack core = new ItemStack(ModItems.KNOWLEDGE_CORE.get());
        HandlerKnowledgeCore handler = HandlerKnowledgeCore.of(core, level.registryAccess());
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        handler.store(pattern);
        return core;
    }

    /** The priciest arcane recipe the pack has: a check against an invented number proves nothing. */
    private static @Nullable ThEArcanePattern priciestRecipe(ServerLevel level) {
        ThEArcanePattern priciest = null;
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            if (output.isEmpty()) {
                continue;
            }
            ThEArcanePattern candidate = ThEArcanePattern.fromRecipe(arcane, output);
            if (candidate != null && (priciest == null || candidate.chargedVis() > priciest.chargedVis())) {
                priciest = candidate;
            }
        }
        return priciest;
    }
}
