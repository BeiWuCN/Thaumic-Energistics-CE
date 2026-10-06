package thaumicenergistics_ce.blockentity.assembler;

import thaumicenergistics_ce.compat.thaumaturge.TcAura;

/** The aura around the machine: how much vis it holds, what it can hold, and one drain from it. */
final class AssemblerAura {

    /** Vis reach in chunks, 3x3: one chunk is never enough, Thaumaturge caps an aura's base at 500 vis
     * while the priciest recipe costs 1728. */
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
