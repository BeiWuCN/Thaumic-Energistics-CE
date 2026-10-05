package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;

/**
 * The machine's saved state and the tag a packet carries to a client. Apart from the machine, the order
 * the pieces depend on - a card count read after the items, a craft written with its well - is readable in
 * one place.
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
        // After the items, not before: the count comes from the cards that just loaded, not from the
        // saved number a menu-local container used to write.
        machine.upgrades.recountSpeedUpgrades();
        machine.patternCache.invalidate();
    }

    static void save(BlockEntityArcaneAssembler machine, CompoundTag tag, HolderLookup.Provider registries) {
        machine.mainNode.saveToNBT(tag);
        machine.upgrades.writeNbt(tag);
        machine.vis.writeNbt(tag);
        // Saved with the craft, so finishing it after a reload needs nothing but this tag and the well.
        machine.craft.writeNbt(tag, registries);
        ContainerHelper.saveAllItems(tag, machine.inventory.getItems(), registries);
    }

    /** A packet's tag, applied on the client. */
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
