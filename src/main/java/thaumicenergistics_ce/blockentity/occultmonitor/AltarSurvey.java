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

final class AltarSurvey {

    static final int ALTAR_SCAN_RANGE = 12;

    private static final int ALTAR_MISS_INTERVAL = 100;

    private static final int SURVEY_INTERVAL = 40;

    private static final int RECIPE_CACHE_TICKS = 40;

    private final BlockEntityOccultMonitor monitor;
    private final EssentiaReach reach;

    private @Nullable BlockPos matrixPos;

    private long nextCubeScan;
    private long nextSurvey;

    private int altarMissBackoff = BlockEntityOccultMonitor.SCAN_INTERVAL;

    private List<BlockPos> surveyedProblems = List.of();
    private @Nullable BlockPos surveyedAt;

    private ItemStack cachedCatalyst = ItemStack.EMPTY;
    private @Nullable Recipe cachedRecipe;
    private long cachedRecipeAt;

    private Report report = Report.NONE;

    private boolean altarSearched;

    private InfusionRisk risk = InfusionRisk.NONE;

    private ItemStack craftDisplay = ItemStack.EMPTY;

    AltarSurvey(BlockEntityOccultMonitor monitor, EssentiaReach reach) {
        this.monitor = monitor;
        this.reach = reach;
    }

    void scan() {
        Level level = monitor.getLevel();
        if (level == null) {
            return;
        }
        if (matrixPos != null) {
            Altar altar = TcInfusion.altarAt(level, matrixPos);
            if (altar != null) {
                altarSearched = true;
                report = read(altar, matrixPos);
                return;
            }
        }
        matrixPos = null;

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
        altarSearched = true;
        report = Report.NONE;
    }

    private Report read(Altar altar, BlockPos pos) {
        boolean crafting = altar.crafting();
        float stability = altar.stability();
        AspectList remaining = altar.remaining();

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

    private int readBaseInstability(BlockPos pos) {
        ItemStack catalyst = pedestalItem(pos);
        if (catalyst.isEmpty()) {
            return 0;
        }
        Recipe recipe = recipeFor(catalyst);
        return recipe == null ? 0 : recipe.instability();
    }

    private @Nullable Recipe recipeFor(ItemStack catalyst) {
        Level level = monitor.getLevel();
        if (level == null || catalyst.isEmpty()) {
            return null;
        }
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

    void forget() {
        altarSearched = false;
        report = Report.NONE;
    }
}
