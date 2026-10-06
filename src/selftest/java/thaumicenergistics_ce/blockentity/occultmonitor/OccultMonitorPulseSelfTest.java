package thaumicenergistics_ce.blockentity.occultmonitor;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.ModBlocks;
import thaumicenergistics_ce.util.ThELog;

/**
 * The finished-craft pulse, on a real block in a real level: it must read 15 the moment a ritual ends,
 * and be gone by itself, because a signal that never falls is the one failure a player cannot fix.
 * <ul>
 * <li>Placed in the air over the spawn on the first tick after the server starts, so the headless gate
 * runs it without a player.
 * <li>Waits past the pulse's own length: a same-tick check would pass even if the scheduled tick that
 * takes the pulse down never came.
 * </ul>
 */
public final class OccultMonitorPulseSelfTest {

    private static final String TAG = "monitor-pulse";

    /** How long to wait before the pulse must be down: the pulse itself, plus a tick of slack. */
    private static final int WAIT_TICKS = BlockEntityOccultMonitor.PULSE_TICKS + 10;

    private static boolean hasRun;

    private static @Nullable ServerLevel level;
    private static @Nullable BlockPos pos;
    private static int waitedTicks;

    private static final List<String> failures = new ArrayList<>();

    private OccultMonitorPulseSelfTest() {}

    public static void run(ServerStartedEvent event) {
        if (hasRun) {
            return;
        }
        hasRun = true;
        level = event.getServer().overworld();
        waitedTicks = 0;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel world = level;
        if (world == null) {
            return;
        }
        if (waitedTicks == 0) {
            place(world);
        }
        waitedTicks++;
        if (waitedTicks <= WAIT_TICKS) {
            return;
        }
        level = null;
        end(world);
        report();
    }

    /** Puts a monitor in the air over the spawn and pulses it, then reads what its neighbours would. */
    private static void place(ServerLevel world) {
        BlockPos at = world.getSharedSpawnPos().above(4);
        pos = at;
        world.setBlockAndUpdate(at, ModBlocks.OCCULT_MONITOR.get().defaultBlockState());
        if (!(world.getBlockEntity(at) instanceof BlockEntityOccultMonitor monitor)) {
            failures.add("the monitor block came up without its machine, so nothing could be pulsed");
            return;
        }
        monitor.startPulse();
        int signal = world.getSignal(at, Direction.UP);
        if (signal != 15) {
            failures.add("the pulse reads " + signal + " as it starts, not 15");
        }
        if (!monitor.pulsing()) {
            failures.add("the machine says it is not pulsing while its block reads " + signal);
        }
    }

    /** The other half: nothing may still be standing a second later, and the test world goes back. */
    private static void end(ServerLevel world) {
        BlockPos at = pos;
        if (at == null) {
            return;
        }
        int signal = world.getSignal(at, Direction.UP);
        if (signal != 0) {
            failures.add("the pulse still reads " + signal + " after " + WAIT_TICKS + " ticks");
        }
        if (world.getBlockEntity(at) instanceof BlockEntityOccultMonitor monitor && monitor.pulsing()) {
            failures.add("the machine still calls itself pulsing once the pulse should be over");
        }
        world.removeBlock(at, false);
    }

    private static void report() {
        if (failures.isEmpty()) {
            ThELog.LOG.info("[{}] self-test passed", TAG);
            return;
        }
        for (String failure : failures) {
            ThELog.LOG.error("[{}] FAIL {}", TAG, failure);
        }
    }
}
