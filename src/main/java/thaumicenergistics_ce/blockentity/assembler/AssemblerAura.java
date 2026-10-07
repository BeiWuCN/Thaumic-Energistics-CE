package thaumicenergistics_ce.blockentity.assembler;

import thaumicenergistics_ce.compat.thaumaturge.TcAura;

/** 机器周围的灵气：它持有多少 vis、容量多少，以及一次抽取。 */
final class AssemblerAura {

    /** vis 的搜索范围以区块计，3x3：一个区块永远不够，Thaumaturge 把灵气的
     * 基础值上限设为 500 vis，而最贵的配方要花 1728。 */
    private static final int VIS_SOURCE_RADIUS = 1;

    private AssemblerAura() {
    }

    static float around(BlockEntityArcaneAssembler owner) {
        if (owner.getLevel() == null) {
            return -1.0F;
        }
        float total = 0;
        for (int dx = -VIS_SOURCE_RADIUS; dx <= VIS_SOURCE_RADIUS; dx++) {
            for (int dz = -VIS_SOURCE_RADIUS; dz <= VIS_SOURCE_RADIUS; dz++) {
                total += TcAura.vis(owner.getLevel(), owner.getBlockPos().offset(dx * 16, 0, dz * 16));
            }
        }
        return total;
    }

    static int capacity(BlockEntityArcaneAssembler owner) {
        if (owner.getLevel() == null) {
            return 0;
        }
        int total = 0;
        for (int dx = -VIS_SOURCE_RADIUS; dx <= VIS_SOURCE_RADIUS; dx++) {
            for (int dz = -VIS_SOURCE_RADIUS; dz <= VIS_SOURCE_RADIUS; dz++) {
                total += TcAura.auraBase(owner.getLevel(), owner.getBlockPos().offset(dx * 16, 0, dz * 16));
            }
        }
        return total;
    }

    static float drain(BlockEntityArcaneAssembler owner, int amount) {
        int span = VIS_SOURCE_RADIUS * 2 + 1;
        float share = (float) amount / (span * span);
        float drained = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int dx = -VIS_SOURCE_RADIUS; dx <= VIS_SOURCE_RADIUS; dx++) {
                for (int dz = -VIS_SOURCE_RADIUS; dz <= VIS_SOURCE_RADIUS; dz++) {
                    float want = pass == 0 ? share : amount - drained;
                    if (want <= 0.05F) {
                        continue;
                    }
                    drained += TcAura.drainVis(
                            owner.getLevel(), owner.getBlockPos().offset(dx * 16, 0, dz * 16), want, false);
                }
            }
        }
        return drained;
    }
}
