package thaumicenergistics_ce.part;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.parts.misc.InterfacePart;
import appeng.parts.storagebus.StorageBusPart;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;

/** Thaumaturge 没有「咒波能否落在这里」这一问，所以落点沿一条链逐级挑选。 */
final class FluxCondensation {

    // 设计写的是 35%；作者取了它的十分之一。
    private static final double CONDENSE_CHANCE = 0.10;

    // 设计按驱动器区分，分别为 5% 与 40%；作者设成了单一的 1.5%。
    private static final double SPILL_CHANCE = 0.015;

    static final int BURST = 16;

    private static final int POINT = PartFluxTransferInterface.FLUX_PER_CYCLE;

    private FluxCondensation() {}

    static void roll(
            ServerLevel server,
            BlockPos landing,
            BlockPos fluxPos,
            int budget,
            @Nullable IGrid grid,
            @Nullable MEStorage storage,
            @Nullable AEKey vitium,
            IActionSource source,
            IntUnaryOperator take) {
        boolean condensing = grid != null
                && !grid.getMachines(DriveBlockEntity.class).isEmpty()
                && storage != null
                && vitium != null
                && storage.insert(vitium, BURST, Actionable.SIMULATE, source) > 0;
        double roll = server.random.nextDouble();
        if (condensing && roll < CONDENSE_CHANCE) {
            int drawn = take.applyAsInt(budget);
            if (drawn <= 0) {
                return;
            }
            long condensed = storage.insert(vitium, drawn, Actionable.MODULATE, source);
            // 网络不肯收下的部分回退成咒波，而不是就此消失。
            if (condensed < drawn) {
                TcAura.addFlux(server, landing, drawn - condensed);
            }
            return;
        }
        // 排空窗口位于凝结窗口之上，所以两条路径排空的都是同样的 1.5%。
        double spillCeiling = condensing ? CONDENSE_CHANCE + SPILL_CHANCE : SPILL_CHANCE;
        if (roll < spillCeiling) {
            spill(server, landing, budget, take);
            return;
        }
        int drawn = take.applyAsInt(POINT);
        if (drawn > 0) {
            TcAura.addFlux(server, fluxPos, drawn);
        }
    }

    private static void spill(ServerLevel server, BlockPos landing, int budget, IntUnaryOperator take) {
        int drawn = take.applyAsInt(budget);
        if (drawn > 0) {
            TcAura.addFlux(server, landing, drawn);
        }
    }

    static @Nullable BlockPos landing(ServerLevel server, @Nullable IGrid grid, BlockPos self) {
        if (grid == null) {
            return null;
        }
        Set<ControllerBlockEntity> controllers = grid.getMachines(ControllerBlockEntity.class);
        if (!controllers.isEmpty()) {
            for (ControllerBlockEntity controller : controllers) {
                if (server.isLoaded(controller.getBlockPos())) {
                    return controller.getBlockPos();
                }
            }
            // 未加载的控制器给不出落点：不采用回退，也不强制加载任何区块。
            return null;
        }
        for (StorageBusPart bus : grid.getMachines(StorageBusPart.class)) {
            BlockPos pos = bus.getBlockEntity().getBlockPos();
            if (server.isLoaded(pos)) {
                return pos;
            }
        }
        for (InterfacePart face : grid.getMachines(InterfacePart.class)) {
            BlockPos pos = face.getBlockEntity().getBlockPos();
            if (server.isLoaded(pos)) {
                return pos;
            }
        }
        return self;
    }
}
