package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.api.infusion.AltarStabiliser;
import com.leclowndu93150.thaumaturge.content.infusion.InfusionStabilitySurvey;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.AbstractSkullBlock;

final class AltarProblemSurvey {
    private AltarProblemSurvey() {}

    static List<BlockPos> find(Level level, BlockPos matrix) {
        var problems = new LinkedHashSet<BlockPos>();
        var visited = new HashSet<BlockPos>();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                if (x == 0 && z == 0) continue;
                for (int y = 3; y >= -7; y--) {
                    var pos = matrix.offset(x, y, z);
                    if (visited.contains(pos) || !InfusionStabilitySurvey.isStabiliser(level, pos)) continue;
                    var mirror = matrix.offset(-x, y, -z);
                    visited.add(pos);
                    visited.add(mirror);
                    if (identity(level, pos) != identity(level, mirror)
                            || value(level, pos) != value(level, mirror)
                            || penalised(level, pos, mirror) || penalised(level, mirror, pos)) {
                        problems.add(pos);
                        if (InfusionStabilitySurvey.isStabiliser(level, mirror)) problems.add(mirror);
                    }
                }
            }
        }
        return List.copyOf(problems);
    }

    private static Block identity(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return Blocks.AIR;
        var block = level.getBlockState(pos).getBlock();
        var identity = block instanceof AltarStabiliser stabiliser ? stabiliser.countsAs(level, pos) : block;
        return identity instanceof AbstractSkullBlock ? Blocks.SKELETON_SKULL : identity;
    }

    private static float value(Level level, BlockPos pos) {
        if (level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof AltarStabiliser stabiliser) {
            return stabiliser.pairValue(level, pos);
        }
        return AltarStabiliser.STANDARD_PAIR_VALUE;
    }

    private static boolean penalised(Level level, BlockPos pos, BlockPos mirror) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof AltarStabiliser stabiliser
                && stabiliser.penalisesPairWith(level, pos, mirror);
    }
}
