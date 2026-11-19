package com.beiwu.thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.content.infusion.InfusionStabilitySurvey;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

final class AltarProblemSurvey {
    private AltarProblemSurvey() {}

    static List<BlockPos> find(Level level, BlockPos matrix) {
        return InfusionStabilitySurvey.survey(level, matrix).problemBlocks();
    }
}
