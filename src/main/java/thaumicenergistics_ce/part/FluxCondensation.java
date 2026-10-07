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

/**
 * The design's condensation table and the hunt for where a spill lands. Every point the machine moves
 * lands somewhere: normally in the release end's own chunk, one time in ten in the network as vitium,
 * and one time in about sixty-seven in a controller's chunk. A banked buffer puts its whole batch
 * through one roll, so nothing here makes flux out of thin air. Thaumaturge offers no "may I add flux
 * here" question, so the landing is picked down the design's chain.
 */
final class FluxCondensation {

    /** The design said 35%; the rate is a tenth by author's decision. */
    private static final double CONDENSE_CHANCE = 0.10;

    /** The design split this by drive, 5% and 40%; the author set a single 1.5% instead. */
    private static final double SPILL_CHANCE = 0.015;

    /** How many banked points a condense or a spill puts through at once. */
    static final int BURST = 16;

    /** What a quiet cycle moves on to the release end: the same pace the drawing end banks. */
    private static final int POINT = PartFluxTransferInterface.FLUX_PER_CYCLE;

    private FluxCondensation() {}

    /** Disposes of up to {@code budget} banked points. {@code take} answers with what the drawing end
     * could really give. */
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
        // The vent window sits above the condense window, so a network that can take vitium vents
        // 1.5% of cycles and one that cannot vents the same 1.5% from the foot of the roll.
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

    /** A spill drops the whole banked batch in the landing chunk instead of the release end's own. */
    private static void spill(ServerLevel server, BlockPos landing, int budget, IntUnaryOperator take) {
        int drawn = take.applyAsInt(budget);
        if (drawn > 0) {
            TcAura.addFlux(server, landing, drawn);
        }
    }

    /** Where a vent goes: one controller's chunk, or the design's fallback when the network has no
     * controller, or {@code null} when the controller it should have used is not loaded. */
    static @Nullable BlockPos landing(ServerLevel server, @Nullable IGrid grid, BlockPos self) {
        if (grid == null) {
            return null;
        }
        Set<ControllerBlockEntity> controllers = grid.getMachines(ControllerBlockEntity.class);
        if (!controllers.isEmpty()) {
            // A multipart controller answers with several machines and any loaded one will do.
            for (ControllerBlockEntity controller : controllers) {
                if (server.isLoaded(controller.getBlockPos())) {
                    return controller.getBlockPos();
                }
            }
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
