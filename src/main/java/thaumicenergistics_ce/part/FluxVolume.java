package thaumicenergistics_ce.part;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** 释放体积是接口自己的规则：Thaumaturge 没有「咒波能否去这里」这一查询。 */
final class FluxVolume {

    private static final int DEPTH = 3;
    private static final int RADIUS = 1;

    private FluxVolume() {}

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
            // 未加载的位置算作被占用：询问它的状态会把区块拉进来。
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

    private static int across(int step) {
        return step == 0 ? RADIUS : 0;
    }
}
