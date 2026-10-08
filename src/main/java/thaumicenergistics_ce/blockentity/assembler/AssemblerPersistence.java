package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;

/**
 * 机器的存档状态，以及数据包发给客户端时带的标签。放在机器之外，
 * 各部分依赖的顺序：物品之后读卡数、合成与它的井一起写，能在一处看懂。
 */
final class AssemblerPersistence {

    private AssemblerPersistence() {}

    static void load(BlockEntityArcaneAssembler machine, ValueInput input) {
        machine.mainNode.deserialize(input);
        machine.craft.readNbt(input);
        machine.upgrades.readNbt(input);
        machine.vis.readNbt(input);
        machine.suppressNotify = true;
        try {
            ContainerHelper.loadAllItems(input, machine.inventory.getItems());
        } finally {
            machine.suppressNotify = false;
        }
        machine.upgrades.recalculateGearDiscount();
        // 在物品之后，不在之前：数量来自刚加载的卡，不来自菜单内容器以前写下的已保存数字。
        machine.upgrades.recountSpeedUpgrades();
        machine.patternCache.invalidate();
    }

    static void save(BlockEntityArcaneAssembler machine, ValueOutput output) {
        machine.mainNode.serialize(output);
        machine.upgrades.writeNbt(output);
        machine.vis.writeNbt(output);
        // 与合成一起存：重载后完成它只需要这个标签和井。
        machine.craft.writeNbt(output);
        ContainerHelper.saveAllItems(output, machine.inventory.getItems());
    }

    /** 数据包的标签，在客户端应用。 */
    static void applyPacket(BlockEntityArcaneAssembler machine, ValueInput input) {
        machine.displaySync.applySyncedState(input);
    }
}
