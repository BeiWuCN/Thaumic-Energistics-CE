package thaumicenergistics_ce.init.capability;

import net.minecraft.core.Direction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.block.BlockArcaneAssembler;
import thaumicenergistics_ce.blockentity.BlockEntityDistillationEncoder;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.init.ModBlockEntities;

/**
 * The mod's machines as item handlers, so a hopper or a pipe can see them. Every machine is one
 * flat container on the inside and the bands below are indices into it, with what JEI dragged
 * in and what the machine writes for itself left out of every band. The assembler answers on
 * its own facing only, so a bank of them does not feed off the front, and the gacha box answers
 * with the brain's slot alone, which is a container of its own rather than a band.
 */
public final class ThEItemCapabilities {

    /** The handler type, spelled once: the machine's own queries take the same one. */
    private static final BlockCapability<IItemHandler, @Nullable Direction> ITEM = Capabilities.ItemHandler.BLOCK;

    private ThEItemCapabilities() {}

    /** Called from the mod constructor's capability listener. */
    public static void install(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(ITEM, ModBlockEntities.DISTILLATION_ENCODER.get(), (machine, side) -> encoder(machine));
        event.registerBlockEntity(ITEM, ModBlockEntities.KNOWLEDGE_INSCRIBER.get(),
                (machine, side) -> inscriber(machine));
        event.registerBlockEntity(ITEM, ModBlockEntities.ESSENTIA_CELL_WORKBENCH.get(), (machine, side) -> workbench(machine));
        event.registerBlockEntity(ITEM, ModBlockEntities.OCCULT_MONITOR.get(), (machine, side) -> monitor(machine.getInventory()));
        event.registerBlockEntity(ITEM, ModBlockEntities.ARCANE_ASSEMBLER.get(), (machine, side) -> assembler(machine, side));
        event.registerBlockEntity(ITEM, ModBlockEntities.GACHA_BOX.get(),
                (machine, side) -> brain(machine.brainSlot()));
    }

    /** The pattern wells only: the source well names an item JEI never hands over, so a pipe reaching
     * it would mint one. A pipe gets back what a broken block gives back, and no more. */
    static SlotRangeItemHandler encoder(BlockEntityDistillationEncoder machine) {
        return new SlotRangeItemHandler(machine.getInventory(),
                BlockEntityDistillationEncoder.SLOT_BLANK,
                BlockEntityDistillationEncoder.SLOT_COUNT - BlockEntityDistillationEncoder.SLOT_BLANK);
    }

    /** The core alone. The wells beside it only mirror what the core stores and the grid is the
     * player's scratch pad, so a pipe reaching either would hand back a copy or lose the recipe. */
    static SlotRangeItemHandler inscriber(BlockEntityKnowledgeInscriber machine) {
        return new SlotRangeItemHandler(
                machine.getInventory(), BlockEntityKnowledgeInscriber.CORE_SLOT, 1);
    }

    private static SlotRangeItemHandler workbench(BlockEntityEssentiaCellWorkbench machine) {
        return new SlotRangeItemHandler(machine.getInventory(), BlockEntityEssentiaCellWorkbench.CELL_SLOT, 1);
    }

    private static SlotRangeItemHandler monitor(SimpleContainer bookSlot) {
        return new SlotRangeItemHandler(bookSlot, BlockEntityOccultMonitor.BOOK_SLOT, 1);
    }

    /** The brain's slot alone, and input only: a pipe may fill it, never empty it. */
    private static SlotRangeItemHandler brain(SimpleContainer brainSlot) {
        return SlotRangeItemHandler.inputOnly(brainSlot, 0, 1);
    }

    static SlotRangeItemHandler assembler(BlockEntityArcaneAssembler machine, @Nullable Direction side) {
        if (side != null && !isFront(machine, side)) {
            return null;
        }
        // One container of forty slots, but the machine writes its mirror, target and preview bands
        // itself and those hold copies, so a pipe may only reach the slots a broken block gives back.
        return new SlotRangeItemHandler(machine.getInventory(), 0, BlockEntityArcaneAssembler.SLOT_COUNT,
                BlockEntityArcaneAssembler::isPlayerOwned);
    }

    /** The facing is the block's own property; the machine reads it, and a query with no side is allowed
     * through because a machine on a bench or in a cable's own lookup is asking about the block itself. */
    private static boolean isFront(BlockEntityArcaneAssembler machine, Direction side) {
        BlockState state = machine.getBlockState();
        DirectionProperty facing = BlockArcaneAssembler.FACING;
        return !state.hasProperty(facing) || state.getValue(facing) == side;
    }
}
