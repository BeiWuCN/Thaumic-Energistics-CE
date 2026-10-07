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

/** Thaumaturge has no "may flux land here" question, so the landing is picked down a chain. */
final class FluxCondensation {

    // The design said 35%; the author took a tenth of it.
    private static final double CONDENSE_CHANCE = 0.10;

    // The design split this by drive, 5% and 40%; the author set a single 1.5%.
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
            // What the network will not take falls back to flux rather than vanishing.
            if (condensed < drawn) {
                TcAura.addFlux(server, landing, drawn - condensed);
            }
            return;
        }
        // The vent window sits above the condense window, so both paths vent the same 1.5%.
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
            // Unloaded controllers give no landing: no fallback, and no chunk is force-loaded.
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
