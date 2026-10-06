package thaumicenergistics_ce.blockentity.occultmonitor;

import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor.Report;
import thaumicenergistics_ce.compat.thaumaturge.TcInfusion;
import thaumicenergistics_ce.compat.thaumaturge.TcInfusion.Altar;
import thaumicenergistics_ce.compat.thaumaturge.TcInfusion.Recipe;
import thaumicenergistics_ce.infusion.InfusionRisk;

/**
 * Finds the altar the monitor watches and reads it: the room's symmetry, the ritual's instability
 * and the catalyst the ritual is about to consume. A found altar is re-checked in place, while a
 * miss backs off, because the cube is 15,625 lookups; and the survey describes the room, not the
 * ritual, so it runs even between rituals.
 */
final class AltarSurvey {

    /** How far off the altar may stand. {@code OccultMonitorCraftPulse} scans the same cube to find the
     * machines that answer for it, so the two distances cannot drift apart. */
    static final int ALTAR_SCAN_RANGE = 12;

    private static final int ALTAR_MISS_INTERVAL = 100;

    private static final int SURVEY_INTERVAL = 40;

    private static final int RECIPE_CACHE_TICKS = 40;

    private final BlockEntityOccultMonitor monitor;
    private final EssentiaReach reach;

    private @Nullable BlockPos matrixPos;

    private long nextCubeScan;
    private long nextSurvey;

    /** Wait before the next search; doubles per miss, so a new altar is found within a second. */
    private int altarMissBackoff = BlockEntityOccultMonitor.SCAN_INTERVAL;

    private List<BlockPos> surveyedProblems = List.of();
    /** The altar {@link #surveyedProblems} was taken at. */
    private @Nullable BlockPos surveyedAt;

    private ItemStack cachedCatalyst = ItemStack.EMPTY;
    private @Nullable Recipe cachedRecipe;
    private long cachedRecipeAt;

    private Report report = Report.NONE;

    /** Whether the altar has been searched since the node was last active. "No altar" is a fact about the
     * room only once a search has run; before one - and while the node is offline, when nothing is
     * searched at all - the machine has not looked, and nothing may claim that it has. Not saved: a
     * reloaded world starts out not having looked, which is the truth. */
    private boolean altarSearched;

    private InfusionRisk risk = InfusionRisk.NONE;

    private ItemStack craftDisplay = ItemStack.EMPTY;

    AltarSurvey(BlockEntityOccultMonitor monitor, EssentiaReach reach) {
        this.monitor = monitor;
        this.reach = reach;
    }

    /** Finds and reads the altar; a cube scan, since the matrix's offset is arbitrary. */
    void scan() {
        Level level = monitor.getLevel();
        if (level == null) {
            return;
        }
        // A found altar is re-checked directly, not by searching twelve blocks twice a second.
        if (matrixPos != null) {
            Altar altar = TcInfusion.altarAt(level, matrixPos);
            if (altar != null) {
                altarSearched = true;
                report = read(altar, matrixPos);
                return;
            }
        }
        matrixPos = null;

        // A miss backs off: the cube is 15,625 lookups and finding nothing changes nothing.
        long now = level.getGameTime();
        if (now < nextCubeScan) {
            report = Report.NONE;
            return;
        }

        BlockPos centre = monitor.getBlockPos();
        for (BlockPos pos : BlockPos.betweenClosed(
                centre.offset(-ALTAR_SCAN_RANGE, -ALTAR_SCAN_RANGE, -ALTAR_SCAN_RANGE),
                centre.offset(ALTAR_SCAN_RANGE, ALTAR_SCAN_RANGE, ALTAR_SCAN_RANGE))) {
            Altar altar = TcInfusion.altarAt(level, pos);
            if (altar != null) {
                matrixPos = pos.immutable();
                altarMissBackoff = BlockEntityOccultMonitor.SCAN_INTERVAL;
                altarSearched = true;
                report = read(altar, matrixPos);
                return;
            }
        }
        nextCubeScan = now + altarMissBackoff;
        altarMissBackoff = Math.min(ALTAR_MISS_INTERVAL, altarMissBackoff * 2);
        // The cube was searched and came up empty: that is a real reading, unlike an unsearched machine.
        altarSearched = true;
        report = Report.NONE;
    }

    /** Reads one altar. The survey runs even between rituals, since blocks out of place are what a
     * player fixes first; the instability read is the catalyst's, two blocks below the matrix. */
    private Report read(Altar altar, BlockPos pos) {
        boolean crafting = altar.crafting();
        float stability = altar.stability();
        AspectList remaining = altar.remaining();

        // The survey describes the room, not the ritual: every two seconds, and at once on a new altar.
        Level level = monitor.getLevel();
        if (level != null) {
            long now = level.getGameTime();
            if (now >= nextSurvey || !pos.equals(surveyedAt)) {
                nextSurvey = now + SURVEY_INTERVAL;
                surveyedAt = pos.immutable();
                List<BlockPos> problems = TcInfusion.problemBlocks(level, pos);
                if (problems != null) {
                    surveyedProblems = problems;
                }
            }
        }
        List<BlockPos> problems = surveyedProblems;
        // A shortage is essentia the altar cannot find, not essentia it has not finished with.
        boolean shortages = crafting && reach.shortOf(pos, remaining);
        risk = new InfusionRisk(readBaseInstability(pos), problems.size(), shortages, stability);

        Recipe recipe = crafting ? recipeFor(pedestalItem(pos)) : null;
        craftDisplay = crafting ? craftName(pedestalItem(pos), recipe) : ItemStack.EMPTY;
        if (crafting) {
            reach.read(remaining, recipe);
        } else {
            reach.clear();
        }
        return new Report(true, crafting, stability, remaining, problems);
    }

    /** Instability of the catalyst's recipe, or zero. Research is ignored on purpose: the player who
     * has not unlocked the recipe is the one who needs the warning. */
    private int readBaseInstability(BlockPos pos) {
        ItemStack catalyst = pedestalItem(pos);
        if (catalyst.isEmpty()) {
            return 0;
        }
        Recipe recipe = recipeFor(catalyst);
        return recipe == null ? 0 : recipe.instability();
    }

    /** The catalyst's recipe, cached: it is asked for twice per scan and walked linearly. */
    private @Nullable Recipe recipeFor(ItemStack catalyst) {
        Level level = monitor.getLevel();
        if (level == null || catalyst.isEmpty()) {
            return null;
        }
        // Walked linearly and asked twice per scan, so the answer is cached for a couple of seconds.
        long now = level.getGameTime();
        if (now - cachedRecipeAt <= RECIPE_CACHE_TICKS
                && ItemStack.isSameItemSameComponents(cachedCatalyst, catalyst)) {
            return cachedRecipe;
        }
        Recipe recipe = TcInfusion.recipeFor(level, catalyst);
        if (recipe == null) {
            cachedCatalyst = ItemStack.EMPTY;
            cachedRecipe = null;
            return null;
        }
        cachedCatalyst = catalyst.copy();
        cachedRecipe = recipe;
        cachedRecipeAt = now;
        return recipe;
    }

    private ItemStack craftName(ItemStack catalyst, @Nullable Recipe recipe) {
        if (catalyst.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // Copied per read: the recipe hands out a fresh result stack on every call.
        return recipe == null ? catalyst : recipe.result().copy();
    }

    private ItemStack pedestalItem(BlockPos pos) {
        Level level = monitor.getLevel();
        if (level == null) {
            return ItemStack.EMPTY;
        }
        return TcInfusion.catalystUnder(level, pos);
    }

    Report report() {
        return report;
    }

    InfusionRisk risk() {
        return risk;
    }

    boolean searched() {
        return altarSearched;
    }

    ItemStack craftDisplay() {
        return craftDisplay;
    }

    @Nullable BlockPos matrixPos() {
        return matrixPos;
    }

    void setMatrixPos(@Nullable BlockPos matrixPos) {
        this.matrixPos = matrixPos;
    }

    /** The node went offline: nothing was searched, so the last reading is dropped rather than served as
     * if it were current. The risk is kept - it is the room's, not the grid's. */
    void forget() {
        altarSearched = false;
        report = Report.NONE;
    }
}
