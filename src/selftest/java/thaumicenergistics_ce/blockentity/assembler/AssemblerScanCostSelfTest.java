package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import thaumicenergistics_ce.util.ThELog;

/**
 * What one interface scan costs, measured rather than assumed.
 * <ul>
 * <li>{@link AssemblerInterfaceFinder} walks a 17x17x17 cube, 4,913 block entity lookups; the plan left it
 * alone until a number was on the table.
 * <li>What matters is one scan against one tick, and the finder's own backoff caps that at one a second.
 * </ul>
 */
public final class AssemblerScanCostSelfTest {

    private static final String TAG = "assembler-scan";

    /** Runs per measurement: enough for a stable mean without holding up server start. */
    private static final int RUNS = 100;

    /** Runs thrown away first, so class loading is not charged to the reported figure. */
    private static final int WARMUP = 20;

    /** The finder's reach, and the reach it would have at four blocks out: 4,913 lookups against 729. */
    private static final int RANGE = 8;

    private static final int SHRUNK_RANGE = 4;

    /** One scan may cost this before the cube is worth shrinking or the interface worth announcing
     * itself. At the finder's worst case of one scan a second this is a tenth of a tick a second; the
     * figure is loose on purpose, because a gate that fires on noise teaches nothing. */
    private static final double SCAN_BUDGET_MILLIS = 5.0;

    /** A server tick. One scan a second is measured against this. */
    private static final double TICK_MILLIS = 50.0;

    private static boolean hasRun;

    private AssemblerScanCostSelfTest() {}

    /** Entry point for the self-test source set's bootstrap; one run per server. */
    public static void run(ServerStartedEvent event) {
        if (hasRun) {
            return;
        }
        hasRun = true;

        ServerLevel level = event.getServer().overworld();
        BlockPos origin = level.getSharedSpawnPos();

        long scanNanos = timeScans(level, origin);
        long lookupNanos = timeLookups(level, origin, RANGE);
        long shrunkNanos = timeLookups(level, origin, SHRUNK_RANGE);

        double scanMillis = millis(scanNanos);
        int lookups = cube(RANGE);
        ThELog.LOG.info(
                "[{}] one scan of {} positions: {} ms; the same positions read one by one: {} ms; "
                        + "{} positions at four blocks out: {} ms",
                TAG,
                lookups,
                round(scanMillis),
                round(millis(lookupNanos)),
                cube(SHRUNK_RANGE),
                round(millis(shrunkNanos)));
        ThELog.LOG.info(
                "[{}] {} us a lookup; at the finder's worst case of one scan a second that is {} ms "
                        + "against a 50 ms tick, {} per cent of one tick",
                TAG,
                round(millis(lookupNanos) * 1000.0 / lookups),
                round(scanMillis),
                round(scanMillis / TICK_MILLIS * 100.0));

        if (scanMillis <= 0.0) {
            ThELog.LOG.error("[{}] FAIL the scan cost no measurable time, so this check measured nothing", TAG);
            return;
        }
        if (scanMillis > SCAN_BUDGET_MILLIS) {
            ThELog.LOG.error(
                    "[{}] FAIL one scan costs {} ms, over the {} ms budget: shrink the cube or let the "
                            + "interface announce itself",
                    TAG,
                    round(scanMillis),
                    SCAN_BUDGET_MILLIS);
            return;
        }
        ThELog.LOG.info("[{}] self-test passed", TAG);
    }

    /** Times the finder itself, a fresh one each run so every call pays for a scan. */
    private static long timeScans(ServerLevel level, BlockPos origin) {
        int found = 0;
        for (int i = 0; i < WARMUP; i++) {
            if (new AssemblerInterfaceFinder(20).nearby(level, origin) != null) {
                found++;
            }
        }
        long start = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            if (new AssemblerInterfaceFinder(20).nearby(level, origin) != null) {
                found++;
            }
        }
        long elapsed = System.nanoTime() - start;
        ThELog.LOG.info("[{}] {} of {} scans found an interface beside the spawn", TAG, found, WARMUP + RUNS);
        return elapsed / RUNS;
    }

    /** The same cube without the finder's part lookups: what the reads cost on their own. */
    private static long timeLookups(ServerLevel level, BlockPos origin, int range) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < WARMUP; i++) {
            walk(level, origin, range, cursor);
        }
        long start = System.nanoTime();
        int occupied = 0;
        for (int i = 0; i < RUNS; i++) {
            occupied = walk(level, origin, range, cursor);
        }
        long elapsed = System.nanoTime() - start;
        if (range == RANGE) {
            ThELog.LOG.info(
                    "[{}] {} of the {} positions hold a block entity", TAG, occupied, cube(range));
        }
        return elapsed / RUNS;
    }

    /** Reads the cube once, returning how many positions held a block entity. */
    private static int walk(ServerLevel level, BlockPos origin, int range, BlockPos.MutableBlockPos cursor) {
        int occupied = 0;
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    cursor.setWithOffset(origin, x, y, z);
                    // The result is read and kept: a lookup whose answer is dropped is one the JIT may drop too.
                    if (level.getBlockEntity(cursor) != null) {
                        occupied++;
                    }
                }
            }
        }
        return occupied;
    }

    private static int cube(int range) {
        int side = 2 * range + 1;
        return side * side * side;
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
