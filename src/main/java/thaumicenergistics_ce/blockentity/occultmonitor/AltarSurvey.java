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
 * 找到监控器所监视的祭坛并读取它：房间的对称性、仪式的
 * 不稳定度，以及仪式即将消耗的触媒。已找到的祭坛在原地复查，而
 * 未命中则退避，因为这个立方体是 15,625 次查找；并且勘察描述的是
 * 房间而非仪式，所以仪式之间也会运行。
 */
final class AltarSurvey {

    /** 祭坛可以站多远。{@code OccultMonitorCraftPulse} 扫描同一个立方体来找到
     * 替它作答的机器，所以这两个距离不能漂移开。 */
    static final int ALTAR_SCAN_RANGE = 12;

    private static final int ALTAR_MISS_INTERVAL = 100;

    private static final int SURVEY_INTERVAL = 40;

    private static final int RECIPE_CACHE_TICKS = 40;

    private final BlockEntityOccultMonitor monitor;
    private final EssentiaReach reach;

    private @Nullable BlockPos matrixPos;

    private long nextCubeScan;
    private long nextSurvey;

    /** 下次搜索前等待多久；每未命中一次翻倍，所以新祭坛一秒内就会被找到。 */
    private int altarMissBackoff = BlockEntityOccultMonitor.SCAN_INTERVAL;

    private List<BlockPos> surveyedProblems = List.of();
    /** {@link #surveyedProblems} 取自的那个祭坛。 */
    private @Nullable BlockPos surveyedAt;

    private ItemStack cachedCatalyst = ItemStack.EMPTY;
    private @Nullable Recipe cachedRecipe;
    private long cachedRecipeAt;

    private Report report = Report.NONE;

    /** 自节点上次活跃以来是否搜过祭坛。“没有祭坛”只有在搜索跑过之后
     * 才是关于房间的事实；在那之前——以及节点离线、完全不做搜索时——
     * 机器没有搜过，任何东西都不能声称它搜过。不保存：重新加载的世界一开始
     * 就是没搜过，这是实情。 */
    private boolean altarSearched;

    private InfusionRisk risk = InfusionRisk.NONE;

    private ItemStack craftDisplay = ItemStack.EMPTY;

    AltarSurvey(BlockEntityOccultMonitor monitor, EssentiaReach reach) {
        this.monitor = monitor;
        this.reach = reach;
    }

    /** 找到并读取祭坛；做立方体扫描，因为矩阵的偏移是任意的。 */
    void scan() {
        Level level = monitor.getLevel();
        if (level == null) {
            return;
        }
        // 已找到的祭坛直接复查，不靠每秒两次搜索十二个方块。
        if (matrixPos != null) {
            Altar altar = TcInfusion.altarAt(level, matrixPos);
            if (altar != null) {
                altarSearched = true;
                report = read(altar, matrixPos);
                return;
            }
        }
        matrixPos = null;

        // 未命中就退避：这个立方体是 15,625 次查找，而没找到不会改变任何东西。
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
        // 立方体搜过了并返回空：这是真实的读数，与从未搜索过的机器不同。
        altarSearched = true;
        report = Report.NONE;
    }

    /** 读取一个祭坛。仪式之间也运行勘察，因为方块错位正是
     * 玩家最先修的；不稳定度读的是触媒的，在矩阵下方两格。 */
    private Report read(Altar altar, BlockPos pos) {
        boolean crafting = altar.crafting();
        float stability = altar.stability();
        AspectList remaining = altar.remaining();

        // 勘察描述的是房间而不是仪式：每两秒一次，遇到新祭坛立刻做。
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
        // 短缺是祭坛找不到的源质，不是它还没用完的源质。
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

    /** 触媒配方的不稳定度，没有则为零。有意忽略研究：还没解锁
     * 该配方的玩家才是需要这条警告的人。 */
    private int readBaseInstability(BlockPos pos) {
        ItemStack catalyst = pedestalItem(pos);
        if (catalyst.isEmpty()) {
            return 0;
        }
        Recipe recipe = recipeFor(catalyst);
        return recipe == null ? 0 : recipe.instability();
    }

    /** 触媒的配方，带缓存：每次扫描要问两次，而且是线性遍历。 */
    private @Nullable Recipe recipeFor(ItemStack catalyst) {
        Level level = monitor.getLevel();
        if (level == null || catalyst.isEmpty()) {
            return null;
        }
        // 线性遍历且每次扫描要问两次，所以答案缓存几秒钟。
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
        // 每次读取都复制：配方在每次调用时都会给出一个新的结果物品堆。
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

    /** 节点离线：什么都没搜过，所以丢弃上一次读数，而不是把它当作
     * 当前读数端出去。风险保留——那是房间的，不是网格的。 */
    void forget() {
        altarSearched = false;
        report = Report.NONE;
    }
}
