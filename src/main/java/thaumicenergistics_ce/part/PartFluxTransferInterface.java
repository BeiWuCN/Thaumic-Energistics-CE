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
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.p2p.P2PTunnelPart;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TCAspects;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

/**
 * The Flux Transfer Interface: a pair bound with a memory card, moving four points of flux a cycle out
 * of the drawing end's chunk and into the release end's, at the price of auram and ordo drawn out of
 * the ME network. The end the card was saved on draws, pays and holds the buffer; the other end
 * disposes of it, rolling for whether the points land as flux, as vitium in the network, or at a
 * controller. Deliberately not an {@link com.leclowndu93150.thaumaturge.api.aura.IVisRelaySource}: this
 * one moves auric junk rather than selling vis.
 */
public class PartFluxTransferInterface extends P2PTunnelPart<PartFluxTransferInterface>
        implements IGridTickable {

    @PartModels
    public static final ResourceLocation MODEL_BASE = ThEIds.id("parts/flux_transfer_interface_base");

    @PartModels
    public static final ResourceLocation MODEL_OFF = ThEIds.id("parts/flux_transfer_interface_off");

    @PartModels
    public static final ResourceLocation MODEL_ON = ThEIds.id("parts/flux_transfer_interface_on");

    @PartModels
    public static final ResourceLocation MODEL_HAS_CHANNEL =
            ThEIds.id("parts/flux_transfer_interface_has_channel");

    public static final List<ResourceLocation> MODEL_LOCATIONS =
            List.of(MODEL_BASE, MODEL_OFF, MODEL_ON, MODEL_HAS_CHANNEL);

    private static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF);
    private static final PartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON);
    private static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_HAS_CHANNEL);

    private static final double IDLE_POWER = 1.0;

    /** A cycle is a second: the beat the design was written around. */
    private static final int CYCLE_TICKS = 20;

    /** The flux a cycle moves: the design's one point a second, four times over at the author's ask. */
    static final int FLUX_PER_CYCLE = 4;

    /** The buffer's ceiling, the design's 64 scaled by the same four: past it the drawing end stops
     * buying fuel, since nothing is moving out. */
    private static final int POOL_LIMIT = 256;

    /** At or above this the landing chunk is at the rift threshold: as full as this part will make it. */
    private static final float RIFT_SATURATION = 1.0f;

    private static final String TAG_POOL = "fluxPool";

    /** Flux taken off the drawing end's own chunk and not yet released. It lives on the drawing end, so
     * it survives exactly as long as that end's chunk does - what "sync the buffer when it loads" means. */
    private int fluxPool;

    /** What this second's cycle has moved so far, cleared at the start of every cycle: the tooltip
     * reports the current second, and a pair that is blocked is idle again a second later. */
    private boolean workedThisCycle;

    public PartFluxTransferInterface(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(IDLE_POWER).addService(IGridTickable.class, this);
    }

    /** The same box AE2's storage bus uses: the borrowed model is shaped for it. */
    @Override
    public void getBoxes(IPartCollisionHelper boxes) {
        boxes.addBox(5, 5, 12, 11, 11, 14);
        boxes.addBox(2, 2, 14, 14, 14, 15);
        boxes.addBox(3, 3, 15, 13, 13, 16);
    }

    @Override
    public IPartModel getStaticModels() {
        if (!isPowered()) {
            return MODELS_OFF;
        }
        return isActive() ? MODELS_ON : MODELS_HAS_CHANNEL;
    }

    // ----- what the pair is telling the player ------------------------------

    /** The line Jade shows, or {@code null} when there is nothing to say. A blocked volume is reported
     * by the release end alone - it is the end that would be doing the releasing. */
    public @Nullable FluxWait waitReason() {
        if (!(getLevel() instanceof ServerLevel server) || !getMainNode().isActive()) {
            return FluxWait.NO_NETWORK;
        }
        PartFluxTransferInterface payer = drawEnd();
        if (payer == null || !payer.live(server)) {
            return null;
        }
        if (!isDrawEnd() && (!volumeClear(server) || !payer.volumeClear(server))) {
            return FluxWait.NO_SPACE;
        }
        if (isDrawEnd() && !fluxAvailable(server)) {
            return FluxWait.NO_FLUX;
        }
        return payer.fuelShort(server);
    }

    /** Whether the pair moved its point this second, whichever end is asked: both ends act on the
     * drawing end's buffer, so it is the drawing end that knows. */
    public boolean working() {
        PartFluxTransferInterface payer = drawEnd();
        return payer != null && payer.workedThisCycle;
    }

    // ----- ticking ----------------------------------------------------------

    /** Not a sleeping request: a device that sleeps stops noticing its partner. */
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
        AEKey auram = aspectKey(server, TCAspects.AURAM);
        AEKey ordo = aspectKey(server, TCAspects.ORDO);
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

    /** Releases the buffer into this end's chunk and rolls the dice. The fuel was paid for at the
     * drawing end, so an empty buffer is a wait, not a failure. */
    private void releaseCycle(ServerLevel server) {
        PartFluxTransferInterface payer = drawEnd();
        if (payer == null || !payer.live(server)) {
            return;
        }
        BlockPos landing = releaseSite(server);
        if (landing == null) {
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
                aspectKey(server, TCAspects.VITIUM),
                actionSource(),
                payer::takeFlux);
    }

    /** The resource half: the grid, its energy buffer, then the two fuels the cycle burns. */
    private @Nullable FluxWait fuelShort(ServerLevel server) {
        IEnergyService energy = energy();
        MEStorage storage = storage();
        AEKey auram = aspectKey(server, TCAspects.AURAM);
        AEKey ordo = aspectKey(server, TCAspects.ORDO);
        if (energy == null || storage == null || auram == null || ordo == null) {
            return FluxWait.NO_NETWORK;
        }
        return FluxFuel.shortOf(energy, storage, auram, ordo, actionSource(), FLUX_PER_CYCLE);
    }

    // ----- the pair ---------------------------------------------------------

    private boolean isDrawEnd() {
        return !isOutput();
    }

    /** Whether some release end could take a point right now. The drawing end asks this before it
     * burns a cycle's fuel, so a release that is unloaded, boxed in or already saturated stops the
     * draw at once instead of filling the buffer with flux that has nowhere to go. */
    private boolean releaseEndReady(ServerLevel server) {
        return getOutputStream().anyMatch(output -> output.releaseSite(server) != null);
    }

    /** Whether this end is still a part of {@code server}. A chunk unloading takes the block entity
     * with it and AE2 destroys the grid node behind it ({@code AEBasePart.removeFromWorld} calls
     * {@code IManagedGridNode.destroy}), but the pair's buffer lives on the drawing end, so neither
     * end touches the other's buffer without asking this first. */
    private boolean live(ServerLevel server) {
        return getLevel() == server && !getBlockEntity().isRemoved() && server.isLoaded(fluxPos());
    }

    /** The other end of the pair, from either side. {@code getInput} answers with this part when this
     * part is the input, which is why that answer is filtered out; an unpaired tunnel has neither. */
    private @Nullable PartFluxTransferInterface drawEnd() {
        if (isDrawEnd()) {
            return this;
        }
        PartFluxTransferInterface input = getInput();
        return input == this ? null : input;
    }

    /** Called by the release end, which is the only thing that empties the buffer, and answers with
     * what it could take. Both ends tick on the server thread, so the buffer needs no lock. */
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

    // ----- grid helpers -----------------------------------------------------

    /** The volume in front of this end's face, which is where this end would be releasing. */
    private boolean volumeClear(ServerLevel server) {
        return FluxVolume.clear(server, fluxPos(), getSide());
    }

    /** The landing this end would vent into right now, or {@code null} when it could not vent at all:
     * unloaded, inactive, boxed in, or a chunk already at the rift threshold. An inactive node is not
     * ticked at all, so an end that merely exists there would never take the point. */
    private @Nullable BlockPos releaseSite(ServerLevel server) {
        if (!live(server) || !isActive() || !volumeClear(server)
                || TcAura.fluxSaturation(server, fluxPos()) >= RIFT_SATURATION) {
            return null;
        }
        return FluxCondensation.landing(server, grid(), fluxPos());
    }

    /** Whether this chunk still holds a whole cycle's worth for the drawing end to take, give or take
     * float noise. Thaumaturge's own tick refills a chunk from its neighbours, so drained is not empty. */
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

    /** The grid's storage, not this part's: the fuel comes out of the network and the condensate
     * returns to it. */
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

    /** The network stores aspects as keys, so the fuels and vitium resolve through the registries at
     * hand. */
    private static @Nullable AEKey aspectKey(Level level, ResourceKey<IAspect> aspect) {
        Holder<IAspect> holder = AEssentiaKeyType.aspectOf(level, aspect.location());
        return holder == null ? null : AEssentiaKey.of(holder);
    }

    // ----- persistence ------------------------------------------------------

    /** Reading clamps: a tag written by a build with a bigger limit must not become a buffer this build
     * cannot empty. */
    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        fluxPool = Math.min(POOL_LIMIT, Math.max(0, data.getInt(TAG_POOL)));
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.putInt(TAG_POOL, fluxPool);
    }
}
