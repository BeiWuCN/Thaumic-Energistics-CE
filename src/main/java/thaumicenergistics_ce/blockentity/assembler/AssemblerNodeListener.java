package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;

/** Wakes the machine when its grid node changes: power, a redraw, and a craft that was interrupted. */
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
        // loadAdditional runs before the node exists; this wake has to cover a resumed craft.
        owner.craftJob().updateSleepiness();
    }
}
