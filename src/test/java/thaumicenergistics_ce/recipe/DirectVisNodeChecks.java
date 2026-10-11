package thaumicenergistics_ce.recipe;

import appeng.api.parts.IPartHost;
import appeng.core.definitions.AEBlocks;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityNode;
import com.leclowndu93150.thaumaturge.registry.TTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.registry.ModItems;
import thaumicenergistics_ce.part.PartVisInterface;

final class DirectVisNodeChecks {
    static void run(MinecraftServer server) {
        var level = server.overworld();
        var hostPos = new BlockPos(12, 245, 12);
        var nodePos = hostPos.east();
        require(level.getBlockState(hostPos).isAir() && level.getBlockState(nodePos).isAir(), "Fixture occupied");
        level.getChunk(hostPos);
        try {
            level.setBlock(hostPos, AEBlocks.CABLE_BUS.block().defaultBlockState(), 3);
            level.setBlock(nodePos, TTBlocks.NODE.get().defaultBlockState(), 3);
            var node = (BlockEntityNode) level.getBlockEntity(nodePos);
            var aspect = server.registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY).getOrThrow(TTAspects.IGNIS);
            node.setAspects(AspectList.EMPTY.add(aspect, 25));
            require(TcAura.energizedNodeInFront(level, hostPos, Direction.EAST) == null, "Uncharged node accepted");
            node.setEnergized(true);
            require(nodePos.equals(TcAura.energizedNodeInFront(level, hostPos, Direction.EAST)), "Front node not found");
            require(TcAura.energizedNodeInFront(level, hostPos, Direction.WEST) == null, "Wrong face accepted");
            var host = (IPartHost) level.getBlockEntity(hostPos);
            var part = (PartVisInterface) host.addPart(ModItems.VIS_INTERFACE.get(), Direction.EAST, FakePlayerFactory.getMinecraft(level));
            require(nodePos.equals(part.permitPosition()), "Interface did not resolve direct node");
            require(TcAura.nodeHolds(level, nodePos, TTAspects.IGNIS), "Node palette not available");
            node.setEnergized(false);
            require(part.permitPosition() == null && !TcAura.nodeHolds(level, nodePos, TTAspects.IGNIS), "Uncharged node still supplying");
            level.removeBlock(nodePos, false);
            require(part.permitPosition() == null, "Removed node still supplying");
        } finally {
            level.removeBlock(hostPos, false);
            level.removeBlock(nodePos, false);
        }
        System.out.println("[direct-vis-node] PASS: front face, charged state, direct interface source, immediate removal/deactivation");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
