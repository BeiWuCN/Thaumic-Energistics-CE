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
import thaumicenergistics_ce.init.ModItems;

/**
 * 机器如何向 AE2 展现自己：访问它所用的托管节点，以及它在
 * 网格合成机器列表中的条目。两者都在这里构建，所以节点的形状——频道、暴露面、
 * 空闲耗电——在一处读取，而不是写在机器构造函数里。
 */
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
