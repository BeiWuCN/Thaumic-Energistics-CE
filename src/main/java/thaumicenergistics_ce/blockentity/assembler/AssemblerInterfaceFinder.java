package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.parts.IPartHost;
import appeng.util.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.part.PartVisInterface;

/** Finds the nearest active vis interface and caches it, backing off after fruitless scans. */
final class AssemblerInterfaceFinder {

    /** How far the machine looks for one of this mod's vis interfaces: the relay's own reach. */
    private static final int INTERFACE_RANGE = 8;

    /** How long a fruitless interface scan waits. The cube is 4,913 block entity lookups. */
    private static final int INTERFACE_MISS_MAX = 200;

    private final int pollInterval;

    private @Nullable PartVisInterface nearbyInterface;
    private long nextInterfaceLookup;

    private int interfaceMissBackoff;

    AssemblerInterfaceFinder(int pollInterval) {
        this.pollInterval = pollInterval;
        this.interfaceMissBackoff = pollInterval;
    }

    @Nullable PartVisInterface nearby(ServerLevel server, BlockPos machinePos) {
        long now = server.getGameTime();
        if (now < nextInterfaceLookup) {
            return nearbyInterface;
        }
        nextInterfaceLookup = now + interfaceMissBackoff;
        nearbyInterface = find(server, machinePos);
        interfaceMissBackoff = nearbyInterface == null
                ? Math.min(INTERFACE_MISS_MAX, interfaceMissBackoff * 2)
                : pollInterval;
        return nearbyInterface;
    }

    private @Nullable PartVisInterface find(ServerLevel server, BlockPos machinePos) {
        PartVisInterface best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -INTERFACE_RANGE; x <= INTERFACE_RANGE; x++) {
            for (int y = -INTERFACE_RANGE; y <= INTERFACE_RANGE; y++) {
                for (int z = -INTERFACE_RANGE; z <= INTERFACE_RANGE; z++) {
                    cursor.setWithOffset(machinePos, x, y, z);
                    if (!(server.getBlockEntity(cursor) instanceof IPartHost host)) {
                        continue;
                    }
                    for (Direction side : Platform.DIRECTIONS_WITH_NULL) {
                        if (host.getPart(side) instanceof PartVisInterface part && part.isActive()) {
                            double distance = cursor.distSqr(machinePos);
                            if (distance < bestDistance) {
                                bestDistance = distance;
                                best = part;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }
}
