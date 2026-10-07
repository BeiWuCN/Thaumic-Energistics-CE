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
 * 找到监控器盯着的祭坛并读它：房间对称性、仪式不稳定度、仪式要吃的触媒。
 * 找到过的原地复查；空搜索退避，一个立方体是 15,625 次查找。
 * 勘察说的是房间，不是仪式，仪式之间也照跑。
 */
final class AltarSurvey {

    /** 祭坛能站多远。{@code OccultMonitorCraftPulse} 扫同一个立方体，两个距离不能漂开。 */
    static final int ALTAR_SCAN_RANGE = 12;

    private static final int ALTAR_MISS_INTERVAL = 100;

    private static final int SURVEY_INTERVAL = 40;

    private static final int RECIPE_CACHE_TICKS = 40;

    private final BlockEntityOccultMonitor monitor;
    private final EssentiaReach reach;

    private @Nullable BlockPos matrixPos;

    private long nextCubeScan;
    private long nextSurvey;

    /** 下次搜索前等多久；每空一次翻倍，新祭坛一秒内也能找到。 */
    private int altarMissBackoff = BlockEntityOccultMonitor.SCAN_INTERVAL;

    private List<BlockPos> surveyedProblems = List.of();
    /** {@link #surveyedProblems} 读自哪个祭坛。 */
    private @Nullable BlockPos surveyedAt;

    private ItemStack cachedCatalyst = ItemStack.EMPTY;
    private @Nullable Recipe cachedRecipe;
    private long cachedRecipeAt;

    private Report report = Report.NONE;

    /** 节点上次活跃后搜过没有。“没有祭坛”只有搜过后才算事实。
     * 不落盘：重载的世界就是没搜过。 */
    private boolean altarSearched;

    private InfusionRisk risk = InfusionRisk.NONE;

    private ItemStack craftDisplay = ItemStack.EMPTY;

    AltarSurvey(BlockEntityOccultMonitor monitor, EssentiaReach reach) {
        this.monitor = monitor;
        this.reach = reach;
    }

    /** 找祭坛并读它；矩阵偏移任意，只能扫立方体。 */
    void scan() {
        Level level = monitor.getLevel();
        if (level == null) {
            return;
        }
        // 找到过的直接复查，省得每轮搜十二个方块。
        if (matrixPos != null) {
            Altar altar = TcInfusion.altarAt(level, matrixPos);
            if (altar != null) {
                altarSearched = true;
                report = read(altar, matrixPos);
                return;
            }
        }
        matrixPos = null;

        // 空一次就退避：一个立方体是 15,625 次查找，找不到也不改变什么。
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
        // 立方体搜过且是空的：这是真实读数，跟从没搜过的机器不一样。
        altarSearched = true;
        report = Report.NONE;
    }

    /** 读一个祭坛。方块错位是玩家最先修的，仪式之间也照跑。
     * 不稳定度读触媒的，取矩阵下方两格。 */
    private Report read(Altar altar, BlockPos pos) {
        boolean crafting = altar.crafting();
        float stability = altar.stability();
        AspectList remaining = altar.remaining();

        // 勘察说的是房间，不是仪式：每两秒一次，遇到新祭坛马上做。
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
        // 短缺指祭坛找不到的源质，不是它还没耗掉的源质。
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

    /** 触媒配方的不稳定度，没有就是零。刻意不看研究进度：
     * 还没解锁该配方的玩家才需要这条警告。 */
    private int readBaseInstability(BlockPos pos) {
        ItemStack catalyst = pedestalItem(pos);
        if (catalyst.isEmpty()) {
            return 0;
        }
        Recipe recipe = recipeFor(catalyst);
        return recipe == null ? 0 : recipe.instability();
    }

    /** 触媒的配方，带缓存：一次扫描问两次，而且是线性遍历。 */
    private @Nullable Recipe recipeFor(ItemStack catalyst) {
        Level level = monitor.getLevel();
        if (level == null || catalyst.isEmpty()) {
            return null;
        }
        // 一次扫描要问两次，还得线性遍历，答案缓存几秒。
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
        // 每次读取都复制：配方每次调用都交出新的结果物品堆。
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

    /** 节点离线：没搜过，上一次读数丢掉，不当成当前读数报出去。
     * 风险留着，那是房间的风险，不是网格的。 */
    void forget() {
        altarSearched = false;
        report = Report.NONE;
    }
}
