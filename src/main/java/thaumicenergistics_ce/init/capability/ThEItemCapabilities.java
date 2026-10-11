package thaumicenergistics_ce.init.capability;

import net.minecraft.core.Direction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.block.BlockArcaneAssembler;
import thaumicenergistics_ce.blockentity.BlockEntityDistillationEncoder;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.init.ModBlockEntities;

public final class ThEItemCapabilities {

    private static final BlockCapability<ResourceHandler<ItemResource>, @Nullable Direction> ITEM =
            Capabilities.Item.BLOCK;

    private ThEItemCapabilities() {}

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

    static SlotRangeItemHandler encoder(BlockEntityDistillationEncoder machine) {
        return new SlotRangeItemHandler(machine.getInventory(),
                BlockEntityDistillationEncoder.SLOT_BLANK,
                BlockEntityDistillationEncoder.SLOT_COUNT - BlockEntityDistillationEncoder.SLOT_BLANK);
    }

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

    private static SlotRangeItemHandler brain(SimpleContainer brainSlot) {
        return SlotRangeItemHandler.inputOnly(brainSlot, 0, 1);
    }

    static SlotRangeItemHandler assembler(BlockEntityArcaneAssembler machine, @Nullable Direction side) {
        if (side != null && !isFront(machine, side)) {
            return null;
        }
        return new SlotRangeItemHandler(machine.getInventory(), 0, BlockEntityArcaneAssembler.SLOT_COUNT,
                BlockEntityArcaneAssembler::isPlayerOwned);
    }

    private static boolean isFront(BlockEntityArcaneAssembler machine, Direction side) {
        BlockState state = machine.getBlockState();
        EnumProperty<Direction> facing = BlockArcaneAssembler.FACING;
        return !state.hasProperty(facing) || state.getValue(facing) == side;
    }
}
