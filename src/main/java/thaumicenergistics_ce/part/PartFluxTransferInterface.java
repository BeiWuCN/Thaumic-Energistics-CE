package thaumicenergistics_ce.part;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.parts.p2p.P2PTunnelPart;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.compat.thaumaturge.TcAspects;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

public class PartFluxTransferInterface extends P2PTunnelPart<PartFluxTransferInterface>
        implements IGridTickable {

    public static final Identifier MODEL_BASE = ThEIds.id("parts/flux_transfer_interface_base");

    public static final Identifier MODEL_OFF = ThEIds.id("parts/flux_transfer_interface_off");

    public static final Identifier MODEL_ON = ThEIds.id("parts/flux_transfer_interface_on");

    public static final Identifier MODEL_HAS_CHANNEL =
            ThEIds.id("parts/flux_transfer_interface_has_channel");

    public static final List<Identifier> MODEL_LOCATIONS =
            List.of(MODEL_BASE, MODEL_OFF, MODEL_ON, MODEL_HAS_CHANNEL);

    private static final double IDLE_POWER = 1.0;

    private static final int CYCLE_TICKS = 20;

    static final int FLUX_PER_CYCLE = 4;

    private static final int POOL_LIMIT = 256;

    private static final String TAG_POOL = "fluxPool";

    private int fluxPool;

    private boolean workedThisCycle;

    public PartFluxTransferInterface(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(IDLE_POWER).addService(IGridTickable.class, this);
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(5, 5, 12, 11, 11, 14);
        bch.addBox(2, 2, 14, 14, 14, 15);
        bch.addBox(3, 3, 15, 13, 13, 16);
    }


    public @Nullable FluxWait waitReason() {
        if (!(getLevel() instanceof ServerLevel server) || !getMainNode().isActive()) {
            return FluxWait.NO_NETWORK;
        }
        PartFluxTransferInterface payer = drawEnd();
        if (payer == null || !payer.live(server)) {
            return null;
        }
        if (!payer.volumeClear(server)) {
            return FluxWait.NO_SPACE;
        }
        FluxWait blocked = payer.releaseBlockedReason(server);
        if (blocked != null) {
            return blocked;
        }
        if (isDrawEnd()) {
            return fluxAvailable(server) ? payer.fuelShort(server) : FluxWait.NO_FLUX;
        }
        if (payer.fluxPool <= 0) {
            return fluxAvailable(server) ? payer.fuelShort(server) : FluxWait.NO_FLUX;
        }
        return null;
    }

    public boolean working() {
        PartFluxTransferInterface payer = drawEnd();
        return payer != null && payer.workedThisCycle;
    }


    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(CYCLE_TICKS, CYCLE_TICKS, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        if (getLevel() instanceof ServerLevel server && getMainNode().isActive()) {
            if (isDrawEnd()) {
                drawCycle(server);
            } else {
                releaseCycle(server);
            }
        }
        return TickRateModulation.SAME;
    }

    private void drawCycle(ServerLevel server) {
        workedThisCycle = false;
        if (fluxPool >= POOL_LIMIT || !volumeClear(server) || !releaseEndReady(server)
                || !fluxAvailable(server)) {
            return;
        }
        MEStorage storage = storage();
        IEnergyService energy = energy();
        AEKey auram = aspectKey(server, TcAspects.AURAM);
        AEKey ordo = aspectKey(server, TcAspects.ORDO);
        if (storage == null || energy == null || auram == null || ordo == null) {
            return;
        }
        if (FluxFuel.take(energy, storage, auram, ordo, actionSource(), FLUX_PER_CYCLE)) {
            TcAura.drainFlux(server, fluxPos(), FLUX_PER_CYCLE, false);
            fluxPool = Math.min(POOL_LIMIT, fluxPool + FLUX_PER_CYCLE);
            workedThisCycle = true;
            getHost().markForSave();
        }
    }

    private void releaseCycle(ServerLevel server) {
        PartFluxTransferInterface payer = drawEnd();
        if (payer == null || !payer.live(server)) {
            return;
        }
        BlockPos landing = FluxCondensation.landing(server, grid(), fluxPos());
        if (releaseBlock(server, landing) != null) {
            return;
        }
        int banked = Math.min(payer.fluxPool, FluxCondensation.BURST);
        FluxCondensation.roll(
                server,
                landing,
                fluxPos(),
                banked,
                grid(),
                storage(),
                aspectKey(server, TcAspects.VITIUM),
                actionSource(),
                payer::takeFlux);
    }

    private @Nullable FluxWait fuelShort(ServerLevel server) {
        IEnergyService energy = energy();
        MEStorage storage = storage();
        AEKey auram = aspectKey(server, TcAspects.AURAM);
        AEKey ordo = aspectKey(server, TcAspects.ORDO);
        if (energy == null || storage == null || auram == null || ordo == null) {
            return FluxWait.NO_NETWORK;
        }
        return FluxFuel.shortOf(energy, storage, auram, ordo, actionSource(), FLUX_PER_CYCLE);
    }


    private boolean isDrawEnd() {
        return !isOutput();
    }

    private boolean releaseEndReady(ServerLevel server) {
        return releaseBlockedReason(server) == null;
    }

    private @Nullable FluxWait releaseBlockedReason(ServerLevel server) {
        FluxWait first = null;
        for (PartFluxTransferInterface output : getOutputStream().toList()) {
            BlockPos landing = FluxCondensation.landing(server, output.grid(), output.fluxPos());
            FluxWait block = output.releaseBlock(server, landing);
            if (block == null) {
                return null;
            }
            if (first == null) {
                first = block;
            }
        }
        return first == null ? FluxWait.NO_PARTNER : first;
    }

    private @Nullable FluxWait releaseBlock(ServerLevel server, @Nullable BlockPos landing) {
        if (!live(server)) {
            return FluxWait.OUT_UNLOADED;
        }
        if (!isActive()) {
            return FluxWait.OUT_OFFLINE;
        }
        if (!volumeClear(server)) {
            return FluxWait.OUT_BLOCKED;
        }
        return landing == null ? FluxWait.OUT_NO_LANDING : null;
    }

    private boolean live(ServerLevel server) {
        return getLevel() == server && !getBlockEntity().isRemoved() && server.isLoaded(fluxPos());
    }

    private @Nullable PartFluxTransferInterface drawEnd() {
        if (isDrawEnd()) {
            return this;
        }
        PartFluxTransferInterface input = getInput();
        return input == this ? null : input;
    }

    int takeFlux(int want) {
        int taken = Math.min(want, fluxPool);
        if (taken <= 0) {
            return 0;
        }
        fluxPool -= taken;
        workedThisCycle = true;
        getHost().markForSave();
        return taken;
    }


    private boolean volumeClear(ServerLevel server) {
        return FluxVolume.clear(server, fluxPos(), getSide());
    }

    private boolean fluxAvailable(ServerLevel server) {
        return TcAura.drainFlux(server, fluxPos(), FLUX_PER_CYCLE, true) >= FLUX_PER_CYCLE - 0.001f;
    }

    private @Nullable IGrid grid() {
        IGridNode node = getMainNode().getNode();
        return node == null ? null : node.getGrid();
    }

    private @Nullable IEnergyService energy() {
        IGrid grid = grid();
        return grid == null ? null : grid.getService(IEnergyService.class);
    }

    private @Nullable MEStorage storage() {
        IGrid grid = grid();
        return grid == null ? null : grid.getStorageService().getInventory();
    }

    private IActionSource actionSource() {
        return IActionSource.ofMachine(this);
    }

    private BlockPos fluxPos() {
        return getBlockEntity().getBlockPos();
    }

    private static @Nullable AEKey aspectKey(Level level, ResourceKey<IAspect> aspect) {
        Holder<IAspect> holder = AEssentiaKeyType.aspectOf(level, aspect.identifier());
        return holder == null ? null : AEssentiaKey.of(holder);
    }


    @Override
    public void readFromNBT(ValueInput input) {
        super.readFromNBT(input);
        fluxPool = Math.min(POOL_LIMIT, Math.max(0, input.getIntOr(TAG_POOL, 0)));
    }

    @Override
    public void writeToNBT(ValueOutput output) {
        super.writeToNBT(output);
        output.putInt(TAG_POOL, fluxPool);
    }
}
