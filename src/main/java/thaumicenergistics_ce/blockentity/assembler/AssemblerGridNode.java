package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.stacks.AEItemKey;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import thaumicenergistics_ce.registry.ModItems;

final class AssemblerGridNode {

    private AssemblerGridNode() {}

    static IManagedGridNode create(BlockEntityArcaneAssembler machine) {
        return GridHelper.createManagedNode(machine, AssemblerNodeListener.INSTANCE)
                .setVisualRepresentation(ModItems.ARCANE_ASSEMBLER.get())
                .setInWorldNode(true)
                .setTagName("proxy")
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setIdlePowerUsage(0.0)
                .addService(IGridTickable.class, machine)
                .addService(ICraftingProvider.class, machine);
    }

    static PatternContainerGroup machineInfo() {
        return new PatternContainerGroup(
                AEItemKey.of(ModItems.ARCANE_ASSEMBLER.get()),
                Component.translatable("block.thaumicenergistics_ce.arcane_assembler"),
                List.of());
    }
}
