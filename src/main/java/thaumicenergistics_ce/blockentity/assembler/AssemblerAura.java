package thaumicenergistics_ce.blockentity.assembler;

import thaumicenergistics_ce.compat.thaumaturge.TcAura;

final class AssemblerAura {
    private AssemblerAura() {}

    static float around(BlockEntityArcaneAssembler owner) {
        return owner.getLevel() == null ? -1.0F : TcAura.vis(owner.getLevel(), owner.getBlockPos());
    }

    static int capacity(BlockEntityArcaneAssembler owner) {
        return owner.getLevel() == null ? 0 : TcAura.auraBase(owner.getLevel(), owner.getBlockPos());
    }

    static float drain(BlockEntityArcaneAssembler owner, int amount) {
        return owner.getLevel() == null || amount <= 0 ? 0
                : TcAura.drainVis(owner.getLevel(), owner.getBlockPos(), amount, false);
    }
}
