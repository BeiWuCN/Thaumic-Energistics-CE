package thaumicenergistics_ce.blockentity.assembler;

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

    static void load(BlockEntityArcaneAssembler machine, CompoundTag tag, HolderLookup.Provider registries) {
        machine.mainNode.loadFromNBT(tag);
        machine.craft.readNbt(tag, registries);
        machine.upgrades.readNbt(tag);
        machine.vis.readNbt(tag);
        machine.suppressNotify = true;
        try {
            ContainerHelper.loadAllItems(tag, machine.inventory.getItems(), registries);
        } finally {
            machine.suppressNotify = false;
        }
        machine.upgrades.recalculateGearDiscount();
        // 在物品之后，不在之前：数量来自刚加载的卡，不来自菜单内容器以前写下的已保存数字。
        machine.upgrades.recountSpeedUpgrades();
        machine.patternCache.invalidate();
    }

    static void save(BlockEntityArcaneAssembler machine, CompoundTag tag, HolderLookup.Provider registries) {
        machine.mainNode.saveToNBT(tag);
        machine.upgrades.writeNbt(tag);
        machine.vis.writeNbt(tag);
        // 与合成一起存：重载后完成它只需要这个标签和井。
        machine.craft.writeNbt(tag, registries);
        ContainerHelper.saveAllItems(tag, machine.inventory.getItems(), registries);
    }

    /** 数据包的标签，在客户端应用。 */
    static void applyPacket(
            BlockEntityArcaneAssembler machine,
            ClientboundBlockEntityDataPacket packet,
            HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            machine.displaySync.applySyncedState(tag, registries);
        }
    }
}
