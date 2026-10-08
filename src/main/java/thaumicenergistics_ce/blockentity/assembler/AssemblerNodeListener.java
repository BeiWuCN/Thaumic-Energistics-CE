package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;

/** 网格节点变化时唤醒机器：供电、重绘，以及被中断的合成。 */
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
        // loadAdditional 在节点存在之前跑；这次唤醒得覆盖被恢复的合成。
        owner.craftRunner().updateSleepiness();
    }

    /** 区块加载时创建节点。存档中途保存的合成在这里接上：
     * {@code loadAdditional} 跑的时候还没有 level 能跟它的结果比对。 */
    static void attach(BlockEntityArcaneAssembler machine) {
        if (machine.getLevel() != null && !machine.getLevel().isClientSide()) {
            machine.mainNode.create(machine.getLevel(), machine.getBlockPos());
            machine.craftJob().recoverInterruptedCraft();
        }
    }

    /** 区块卸载时移除节点。 */
    static void detach(BlockEntityArcaneAssembler machine) {
        if (machine.mainNode != null) {
            machine.mainNode.destroy();
        }
    }
}
