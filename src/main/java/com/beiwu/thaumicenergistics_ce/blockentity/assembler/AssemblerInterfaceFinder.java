package com.beiwu.thaumicenergistics_ce.blockentity.assembler;

import appeng.api.parts.IPartHost;
import appeng.util.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.part.PartVisInterface;

/** 找到最近的活跃 vis 接口并缓存，扫描无果后逐次退避。 */
final class AssemblerInterfaceFinder {

    /** 机器搜本 mod 的 vis 接口搜多远：中继点自身的覆盖范围。 */
    private static final int INTERFACE_RANGE = 8;

    /** 扫描接口无果后等多久。那个立方体是 4,913 次方块实体查找。 */
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
        PartVisInterface attached = attached(server, machinePos);
        if (attached != null && attached.isActive()) return attached;
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

    static @Nullable PartVisInterface attached(ServerLevel server, BlockPos machinePos) {
        PartVisInterface inactive = null;
        for (Direction direction : Direction.values()) {
            BlockPos cablePos = machinePos.relative(direction);
            var chunk = server.getChunkSource().getChunkNow(cablePos.getX() >> 4, cablePos.getZ() >> 4);
            if (chunk == null) continue;
            if (chunk.getBlockEntity(cablePos) instanceof IPartHost host
                    && host.getPart(direction.getOpposite()) instanceof PartVisInterface part) {
                if (part.isActive()) return part;
                if (inactive == null) inactive = part;
            }
        }
        return inactive;
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
