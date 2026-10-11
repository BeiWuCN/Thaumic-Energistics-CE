package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;

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
        machine.upgrades.recountSpeedUpgrades();
        machine.patternCache.invalidate();
    }

    static void save(BlockEntityArcaneAssembler machine, ValueOutput output) {
        machine.mainNode.serialize(output);
        machine.upgrades.writeNbt(output);
        machine.vis.writeNbt(output);
        machine.craft.writeNbt(output);
        ContainerHelper.saveAllItems(output, machine.inventory.getItems());
    }

    static void applyPacket(BlockEntityArcaneAssembler machine, ValueInput input) {
        machine.displaySync.applySyncedState(input);
    }
}
