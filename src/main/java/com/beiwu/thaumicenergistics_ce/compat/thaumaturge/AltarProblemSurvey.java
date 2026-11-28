package com.beiwu.thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.content.infusion.InfusionStabilitySurvey;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

final class AltarProblemSurvey {
    private AltarProblemSurvey() {}

    static List<BlockPos> find(Level level, BlockPos matrix) {
        // 祭坛没建成时 Thaumaturge 给 null，和 TcInfusion 一样要接住。
        var survey = InfusionStabilitySurvey.survey(level, matrix);
        return survey == null ? List.of() : survey.problemBlocks();
    }
}
