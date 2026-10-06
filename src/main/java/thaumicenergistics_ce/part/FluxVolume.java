package thaumicenergistics_ce.part;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The room a release needs: three blocks out from the face and three by three across it, counted from
 * the block the face points at. Thaumaturge has no "may this chunk take flux" question to ask, so the
 * volume is the interface's own rule.
 */
final class FluxVolume {

    private static final int DEPTH = 3;
    private static final int RADIUS = 1;

    private FluxVolume() {}

    /** Whether every block of the volume in front of {@code face}, seen from {@code origin}, is loose. */
    static boolean clear(ServerLevel server, BlockPos origin, Direction face) {
        BlockPos near = origin.relative(face);
        BlockPos far = origin.relative(face, DEPTH);
        BlockPos from = new BlockPos(
                Math.min(near.getX(), far.getX()) - across(face.getStepX()),
                Math.min(near.getY(), far.getY()) - across(face.getStepY()),
                Math.min(near.getZ(), far.getZ()) - across(face.getStepZ()));
        BlockPos to = new BlockPos(
                Math.max(near.getX(), far.getX()) + across(face.getStepX()),
                Math.max(near.getY(), far.getY()) + across(face.getStepY()),
                Math.max(near.getZ(), far.getZ()) + across(face.getStepZ()));
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            // An unloaded position counts as occupied: asking for its state would pull the chunk in,
            // and a position nobody has loaded cannot be seen to have room either.
            if (!server.isLoaded(pos)) {
                return false;
            }
            BlockState state = server.getBlockState(pos);
            if (!state.isAir() && !state.canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    /** One block either side on the two axes the face does not point along. */
    private static int across(int step) {
        return step == 0 ? RADIUS : 0;
    }
}
