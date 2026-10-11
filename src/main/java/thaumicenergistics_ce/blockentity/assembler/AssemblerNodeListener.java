package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;

final class AssemblerNodeListener implements IGridNodeListener<BlockEntityArcaneAssembler> {

    static final AssemblerNodeListener INSTANCE = new AssemblerNodeListener();

    private AssemblerNodeListener() {}

    @Override
    public void onSaveChanges(BlockEntityArcaneAssembler owner, IGridNode node) {
        owner.setChanged();
    }

    @Override
    public void onStateChanged(BlockEntityArcaneAssembler owner, IGridNode node, State state) {
        if (state == State.POWER) {
            owner.active = owner.mainNode.isActive();
        }
        owner.displaySync.markForUpdate();
        owner.craftRunner().updateSleepiness();
    }

    static void attach(BlockEntityArcaneAssembler machine) {
        if (machine.getLevel() != null && !machine.getLevel().isClientSide()) {
            machine.mainNode.create(machine.getLevel(), machine.getBlockPos());
            machine.craftJob().recoverInterruptedCraft();
        }
    }

    static void detach(BlockEntityArcaneAssembler machine) {
        if (machine.mainNode != null) {
            machine.mainNode.destroy();
        }
    }
}
