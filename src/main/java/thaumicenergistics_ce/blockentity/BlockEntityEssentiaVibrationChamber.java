package thaumicenergistics_ce.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.ModBlockEntities;

/**
 * The Essentia Vibration Chamber: burns essentia to generate AE.
 *
 * <p>Potentia - Thaumaturge's aspect of energy itself - burns longest and hottest, ignis at the base rate,
 * every other aspect at half of it. Essentia arrives the way it arrives for any container: pulled from an
 * adjacent one, or pushed in by a pipe or an export bus. The chamber does not take it out of the ME network
 * itself - an export bus beside it does that, with the chamber as the container it is. The buffer is a
 * count, not an aspect list; {@link #currentAspect} is remembered only so the screen and the tooltip can
 * name the fuel.
 *
 * <p>Power is banked in a {@link #MAX_ENERGY_STORAGE 16,000 AE} slot and emptied into the network at up to
 * {@link #MAX_OUTPUT_PER_TICK 2,000 AE a tick}. {@link BurnState} is the one answer to what the machine is
 * doing and why: a full slot stops the burn rather than spending fuel on power the network would refuse,
 * and a grid with nowhere to put the power - nothing on it that can hold any, nothing drawing any - stops
 * it too, so a machine with no cable burns nothing and does not power itself. The tooltip reports those
 * states as *"能量槽已满，停止发电"* and *"无网络"*.
 *
 * <p><b>The slot, the pause and the container intake are this mod's own, not AE2's.</b> AE2's chamber keeps
 * no buffer at all: it burns a fuel item, injects what it made every tick, and destroys whatever the grid
 * will not take while the fuel is spent all the same, throttling down only to a 4 AE/t floor
 * (VibrationChamberBlockEntity:200-216, AEConfig:695-697). The reference build is cited in this file for its
 * numbers and for how it configures its node, never as parity for those three.
 */
public class BlockEntityEssentiaVibrationChamber extends AENetworkedBlockEntity
        implements IGridTickable, IEssentiaStorage, IEssentiaTransport, net.minecraft.world.MenuProvider {

    /** How much essentia the chamber can hold, in units. */
    public static final int MAX_ESSENTIA = 64;

    /** Ticks one unit of ignis burns; a full buffer of 64 is most of an hour of generation. */
    private static final int BASE_BURN_TICKS = 800;

    /** Power per tick while burning ignis. Potentia multiplies this, everything else halves it. */
    private static final double BASE_AE_PER_TICK = 200.0;

    /**
     * Energy slot size in AE; the tooltip shows it as 16 kAE / 32,000 FE, AE2 quoting both at two FE to the AE.
     */
    public static final double MAX_ENERGY_STORAGE = 16_000.0;

    /**
     * How much of the slot may be handed to the network per tick - ten times one ignis unit's output, so the
     * cap only binds on a slot that has been filling for a while.
     */
    public static final double MAX_OUTPUT_PER_TICK = 2_000.0;

    /**
     * How much room has to open in the slot before the burn resumes. Hysteresis: the output drains the slot
     * before the burn fills it again, so without a margin the pause would be entered and left on every
     * visit. A few hundred AE is more than one tick of even potentia's burn, and small enough that the slot
     * still reads as full.
     */
    private static final double RESUME_MARGIN = 400.0;

    /** How often the chamber looks at the network while burning, and while idle. */
    private static final int TICK_RATE_BURNING = 10;
    private static final int TICK_RATE_IDLE = 40;

    /**
     * How hard the chamber pulls on a pipe. The pull is wildcard: {@link #getSuctionType} answers null so a
     * pipe will offer any aspect, not only the one currently burning.
     */
    private static final int SUCTION = 128;

    /** The two aspects that burn better than the rest, by path. */
    private static final String ASPECT_POTENTIA = "potentia";
    private static final String ASPECT_IGNIS = "ignis";

    /**
     * What the machine is doing, and - while it is not burning - why. One answer, read by the burn, the
     * intake, the suction, the tooltip and the screen, so that none of them works it out from the numbers:
     * a condition asked again wherever it is read is a condition answered differently in different places,
     * which is what both of this chamber's bugs were.
     */
    public enum BurnState {
        /** Converting essentia into AE right now. */
        BURNING,
        /** Held back: the energy slot cannot take another tick of the burn. */
        PAUSED_FULL,
        /** Nothing but this machine is on its grid, so there is nowhere for the power to go. */
        NO_NETWORK,
        /** Nothing loaded to burn, and room for it. */
        IDLE;

        /** Whether the machine may spend fuel, or is being held back for one of the two reasons above. */
        public boolean mayBurn() {
            return this == BURNING || this == IDLE;
        }

        /**
         * The state an ordinal names, or {@link #IDLE} for one that names nothing: the number arrives in a
         * payload from the other side, and an index out of range would take the client down rather than
         * show a wrong line.
         */
        public static BurnState byOrdinal(int ordinal) {
            BurnState[] states = values();
            return ordinal >= 0 && ordinal < states.length ? states[ordinal] : IDLE;
        }
    }

    /** Essentia waiting to be burned. */
    private int storedEssentia;

    /** The aspect that will be burned next, or was burned last. Display only. */
    private @Nullable Holder<IAspect> currentAspect;

    private int burnTicksRemaining;
    private int totalBurnTicks;
    private double aePerTick;

    /** Power made and not yet handed to the network. */
    private double storedEnergy;

    /** What the machine is doing and why; the one answer every reader and display goes through. */
    private BurnState burnState = BurnState.IDLE;

    /**
     * Whether to report what the chamber sees of its neighbours, once a second. Off unless
     * {@code THAUMICENERGISTICS_EVC_TRACE=true}: a machine that will not take fuel gives no error, and this
     * tells a pipe that is not reaching it apart from one that is being out-pulled.
     */
    private static final boolean TRACE = "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_EVC_TRACE"));

    private int tracedEssentia;

    private long nextTrace;

    /** Bumped whenever the buffer changes, so anything caching this container's contents notices. */
    private long revision;

    public BlockEntityEssentiaVibrationChamber(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(), pos, state);
        // A generator pays nothing to be on the grid and asks for no channel: AE2's fuel chamber, crystal
        // resonance generator and charger are all idle 0.0 and channel-free (VibrationChamberBlockEntity:57,
        // ChargerBlockEntity:43), and the machines that consume are the ones that pay (Growth Accelerator
        // 8.0). Either one on its own would take this machine dark exactly when the network is flat and a
        // player reaches for it.
        getMainNode().setIdlePowerUsage(0.0).setFlags().addService(IGridTickable.class, this);
    }

    // Grid

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TICK_RATE_BURNING, TICK_RATE_IDLE, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        if (level == null || level.isClientSide()) {
            return TickRateModulation.IDLE;
        }

        // Intake runs whether or not the node is active: fuel does not need the network to arrive, and a
        // machine that refused it would back a pipe up behind it. It arrives the way it arrives for any
        // container - pulled from a neighbour, or pushed in by a pipe or an export bus. Nothing is taken
        // while the slot is full, though, or the fuel's power would have nowhere to go.
        if (storedEssentia < MAX_ESSENTIA && !isPaused()) {
            pullEssentia();
        }
        traceIntake();

        // A grid, not an active one: this machine makes power, so it must not wait to be powered before it
        // works - a network that has run flat is exactly when a player reaches for it.
        IGrid grid = node.getGrid();
        if (grid == null) {
            // Not on a grid at all is the answer a grid of one gives: there is nothing to hand power to.
            updateBurnState(false);
            return TickRateModulation.SLOWER;
        }

        // Whether there is anywhere for the power to go, asked of what the grid holds rather than of how big
        // it is. See {@link #hasNetwork}.
        boolean onNetwork = hasNetwork(grid, node);

        // Output before input, so room is made in the same visit that notices the slot was full - and the
        // state is decided on what the output left behind.
        if (onNetwork) {
            outputEnergy(grid, ticksSinceLast);
        }
        updateBurnState(onNetwork);

        // Held back: no room for the power in the slot, or nothing on the grid to take it.
        if (!burnState.mayBurn()) {
            return TickRateModulation.SAME;
        }

        if (burnTicksRemaining > 0) {
            // Only the ticks whose power the slot can take are burnt. A grid with machines on it frees a
            // trickle of room every tick, and crediting a whole wake-up for that trickle is what spent a
            // full chamber's fuel on power that had nowhere to go. The ticks held back are burnt when the
            // room is there, so the burn freezes rather than restarting the unit.
            double perTick = burnTickPower();
            int burnt = (int) Math.min(
                    burnTicksRemaining,
                    Math.min(ticksSinceLast, (MAX_ENERGY_STORAGE - storedEnergy) / perTick));
            storedEnergy = Math.min(MAX_ENERGY_STORAGE, storedEnergy + burnt * perTick);
            burnTicksRemaining -= burnt;
            updateBurnState(onNetwork);
            if (burnTicksRemaining <= 0) {
                burnTicksRemaining = 0;
                aePerTick = 0;
                setChanged();
                markForClientUpdate();
            }
            return TickRateModulation.SAME;
        }

        if (storedEssentia <= 0) {
            return TickRateModulation.SLOWER;
        }

        startBurning();
        // The rate the room is measured in has just changed: a unit loaded into a slot that cannot take a
        // tick of it is held back from the start.
        updateBurnState(onNetwork);
        return TickRateModulation.URGENT;
    }

    /**
     * Whether there is anywhere on this grid for the machine's power to go: a node that can hold it, or a
     * grid that draws power of its own. A lone machine still has a grid - AE2 builds one around its single
     * node - so a grid that exists is not a network, and neither is one that swallows a mouthful: every grid
     * carries an inherent buffer of 25 AE a node (GridEnergyStorage:83, AEConfig:648), which would accept a
     * first offer and leave a machine burning its slot full to pay for a grid of one.
     *
     * <p>Both halves are AE2's own answers: its energy service asks each node for {@link IAEPowerStorage} to
     * find what can hold power (EnergyService:375), and a grid that declares an idle draw has machines that
     * will consume what the chamber makes even where nothing can store it.
     */
    private boolean hasNetwork(IGrid grid, IGridNode self) {
        IEnergyService energy = grid.getService(IEnergyService.class);
        if (energy == null) {
            return false;
        }
        if (energy.getIdlePowerUsage() > 0.0) {
            return true;
        }
        for (IGridNode other : grid.getNodes()) {
            if (other != self && other.getService(IAEPowerStorage.class) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hands the slot's power to the network, up to {@link #MAX_OUTPUT_PER_TICK} a tick. Whatever the network
     * refuses stays in the slot, so an unattended chamber fills up, stops and waits.
     */
    private void outputEnergy(IGrid grid, int ticksSinceLast) {
        if (storedEnergy <= 0) {
            return;
        }
        IEnergyService energy = grid.getService(IEnergyService.class);
        if (energy == null) {
            return;
        }
        double offered = Math.min(storedEnergy, MAX_OUTPUT_PER_TICK * ticksSinceLast);
        double rejected = energy.injectPower(offered, Actionable.MODULATE);
        if (offered - rejected > 0) {
            storedEnergy = Math.max(0, storedEnergy - (offered - rejected));
            setChanged();
        }
    }

    /**
     * Re-decides {@link #burnState} and announces it only when it changes. Called from the places that can
     * change the answer - the output, the burn that banks what it made, the start of a burn, whose rate the
     * room is measured in, and the network test every visit begins with - rather than from every reader,
     * because a condition asked again wherever it is read is a condition answered differently in different
     * places.
     *
     * <p>The slot is full when it cannot take another tick of the burn, and it stays full until
     * {@link #RESUME_MARGIN} of room has opened: a grid that is drawing frees a trickle of room every tick,
     * and a plain "is it full?" would call that trickle room enough to burn into. The state is its own latch,
     * so there is one answer and not two.
     */
    private void updateBurnState(boolean onNetwork) {
        double room = MAX_ENERGY_STORAGE - storedEnergy;
        boolean full = burnState == BurnState.PAUSED_FULL ? room < RESUME_MARGIN : room < burnTickPower();

        BurnState next;
        if (!onNetwork) {
            next = BurnState.NO_NETWORK;
        } else if (full) {
            next = BurnState.PAUSED_FULL;
        } else if (burnTicksRemaining > 0) {
            next = BurnState.BURNING;
        } else {
            next = BurnState.IDLE;
        }

        if (next != burnState) {
            burnState = next;
            setChanged();
            markForClientUpdate();
        }
    }

    /**
     * One tick of the burn, or of the smallest burn the chamber could be given while nothing is burning.
     * Never zero, so the room divided by it is a real number of ticks even for a tag that saved no rate.
     */
    private double burnTickPower() {
        return burnTicksRemaining > 0 ? Math.max(aePerTick, 1.0) : BASE_AE_PER_TICK / 2.0;
    }

    // Fuel

    /**
     * Draws one unit of essentia from a neighbouring container. One unit at a time because containers expose
     * no "give me as much as fits" call, and putting an excess back is where essentia gets lost.
     */
    private void pullEssentia() {
        if (level == null || storedEssentia >= MAX_ESSENTIA) {
            return;
        }
        for (Direction side : Direction.values()) {
            if (pullFromContainer(side) || pullFromTube(side)) {
                return;
            }
        }
    }

    /** A jar, a reservoir, another machine: anything that offers essentia as storage. */
    private boolean pullFromContainer(Direction side) {
        IEssentiaStorage storage = level.getCapability(
                EssentiaCapabilities.STORAGE, worldPosition.relative(side), side.getOpposite());
        if (storage == null) {
            return false;
        }
        for (AspectInstance entry : storage.contents().entries()) {
            Holder<IAspect> aspect = entry.aspect();
            if (entry.amount() <= 0) {
                continue;
            }
            int taken = storage.extract(aspect, 1, false);
            if (taken > 0) {
                accept(aspect, taken);
                return true;
            }
        }
        return false;
    }

    /**
     * Pulls from a Thaumaturge essentia tube. A tube does not push into the machines it passes - the
     * destination is the side that asks, as in Thaumaturge's own essentia port - so a chamber that only
     * waited to be filled would sit empty beside a working pipe. The three conditions below are the port's.
     */
    private boolean pullFromTube(Direction side) {
        Direction facing = side.getOpposite();
        IEssentiaTransport tube = level.getCapability(
                EssentiaCapabilities.TRANSPORT, worldPosition.relative(side), facing);
        if (tube == null || !tube.canOutputTo(facing)) {
            return false;
        }
        if (tube.getEssentiaAmount(facing) <= 0
                || tube.getSuctionAmount(facing) >= getSuctionAmount(side)
                || getSuctionAmount(side) < tube.getMinimumSuction()) {
            return false;
        }
        Holder<IAspect> aspect = tube.getEssentiaType(facing);
        if (aspect == null) {
            return false;
        }
        int taken = tube.takeEssentia(aspect, 1, facing);
        if (taken > 0) {
            accept(aspect, taken);
            return true;
        }
        return false;
    }

    /**
     * Reports, once a second, one line per side that has anything on it. See {@link #TRACE}.
     */
    private void traceIntake() {
        if (!TRACE || level == null || !(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        long now = server.getGameTime();
        if (now < nextTrace) {
            return;
        }
        nextTrace = now + 20;
        StringBuilder sides = new StringBuilder();
        for (Direction side : Direction.values()) {
            BlockPos neighbour = worldPosition.relative(side);
            Direction facing = side.getOpposite();
            IEssentiaTransport tube = level.getCapability(EssentiaCapabilities.TRANSPORT, neighbour, facing);
            IEssentiaStorage container = level.getCapability(EssentiaCapabilities.STORAGE, neighbour, facing);
            if (tube == null && container == null) {
                continue;
            }
            if (sides.length() > 0) {
                sides.append(" | ");
            }
            sides.append(side).append(' ');
            if (tube != null) {
                sides.append("tube[canOut=").append(tube.canOutputTo(facing))
                        .append(" suck=").append(tube.getSuctionAmount(facing))
                        .append(" type=").append(tube.getSuctionType(facing) == null ? "any" : "set")
                        .append(" has=").append(tube.getEssentiaAmount(facing))
                        .append(" mine=").append(getSuctionAmount(side))
                        .append(" min=").append(tube.getMinimumSuction())
                        .append(']');
            }
            if (container != null) {
                sides.append(" storage[").append(container.contents().size()).append(" kinds]");
            }
        }
        thaumicenergistics_ce.ThaumicEnergistics.LOG.info(
                "[evc] at {} stored={}/{} energy={}/{} state={} pulled={} lastSecond | {}",
                worldPosition, storedEssentia, MAX_ESSENTIA,
                Math.round(storedEnergy), (long) MAX_ENERGY_STORAGE,
                burnState, tracedEssentia,
                sides.length() == 0 ? "nothing adjacent" : sides);
        tracedEssentia = 0;
    }

    /** Puts essentia in the buffer and remembers which aspect it was, for the tooltip and the screen. */
    private void accept(Holder<IAspect> aspect, int amount) {
        int space = MAX_ESSENTIA - storedEssentia;
        int taken = Math.min(amount, space);
        if (taken <= 0) {
            return;
        }
        storedEssentia += taken;
        tracedEssentia += taken;
        currentAspect = aspect;
        revision++;
        setChanged();
        // The tooltip reads the buffer from the client, so it has to be told when the buffer moves. Fuel
        // arrives one unit at a time and stops once the buffer is full or the slot is holding the burn back,
        // so this is not a per-tick stream.
        markForClientUpdate();
    }

    /**
     * Spends one buffered unit and starts its burn; the power for it is made tick by tick as the burn runs.
     */
    private void startBurning() {
        int burnTicks = burnTicksFor();
        double power = powerFor();

        storedEssentia--;
        revision++;
        burnTicksRemaining = burnTicks;
        totalBurnTicks = burnTicks;
        aePerTick = power;
        setChanged();
        markForClientUpdate();
    }

    private int burnTicksFor() {
        String path = aspectPath();
        if (ASPECT_POTENTIA.equals(path)) {
            return (int) (BASE_BURN_TICKS / 1.6F);
        }
        return BASE_BURN_TICKS / 2;
    }

    private double powerFor() {
        String path = aspectPath();
        if (ASPECT_POTENTIA.equals(path)) {
            return BASE_AE_PER_TICK * 1.6;
        }
        if (ASPECT_IGNIS.equals(path)) {
            return BASE_AE_PER_TICK;
        }
        return BASE_AE_PER_TICK / 2.0;
    }

    private String aspectPath() {
        return currentAspect == null
                ? ""
                : currentAspect.unwrapKey().map(key -> key.location().getPath()).orElse("");
    }

    /** The aspect by id, for the NBT tag and the tooltip. */
    public @Nullable ResourceLocation getCurrentAspect() {
        return currentAspect == null
                ? null
                : currentAspect.unwrapKey().map(ResourceKey::location).orElse(null);
    }

    // IEssentiaStorage - the chamber as a container, so it can be filled

    /**
     * The whole buffer reported under the aspect put in last: the buffer is a count rather than an aspect
     * list, so this is the same lossy answer the reference build gives.
     */
    @Override
    public AspectList contents() {
        if (storedEssentia <= 0 || currentAspect == null) {
            return AspectList.EMPTY;
        }
        return AspectList.EMPTY.add(currentAspect, storedEssentia);
    }

    @Override
    public int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
        // The same rule the pull and the suction follow: a chamber whose slot is full takes nothing, or an
        // export bus beside it fills a buffer that cannot burn.
        if (aspect == null || amount <= 0 || isPaused()) {
            return 0;
        }
        // Floored: a saved count above the cap would otherwise make this negative, and a caller that treats a
        // negative acceptance as "nothing taken" voids the essentia it has already taken from its neighbour.
        int accepted = Math.min(amount, Math.max(0, MAX_ESSENTIA - storedEssentia));
        if (accepted > 0 && !simulate) {
            accept(aspect, accepted);
        }
        return accepted;
    }

    @Override
    public int extract(Holder<IAspect> aspect, int amount, boolean simulate) {
        return 0;
    }

    @Override
    public long contentRevision() {
        return revision;
    }

    // IEssentiaTransport - what a pipe sees

    @Override
    public boolean isConnectable(Direction side) {
        return true;
    }

    @Override
    public boolean canInputFrom(Direction side) {
        return true;
    }

    /**
     * Reported true - as the reference build does - so pipes treat the chamber as somewhere essentia can go
     * rather than dead-ending at it, even though {@link #takeEssentia} never gives anything back.
     */
    @Override
    public boolean canOutputTo(Direction side) {
        return true;
    }

    /** Ignored: the pull is wildcard. See {@link #SUCTION}. */
    @Override
    public void setSuction(@Nullable Holder<IAspect> aspect, int amount) {
    }

    @Override
    public @Nullable Holder<IAspect> getSuctionType(Direction side) {
        return null;
    }

    /**
     * Zero while the buffer or the energy slot is full. The pipes steer by this number, so a full machine
     * advertising suction would draw essentia out of them with nowhere to put it or its power.
     */
    @Override
    public int getSuctionAmount(Direction side) {
        return storedEssentia < MAX_ESSENTIA && !isPaused() ? SUCTION : 0;
    }

    @Override
    public int takeEssentia(Holder<IAspect> aspect, int amount, Direction side) {
        return 0;
    }

    @Override
    public int addEssentia(Holder<IAspect> aspect, int amount, Direction side) {
        return insert(aspect, amount, false);
    }

    @Override
    public @Nullable Holder<IAspect> getEssentiaType(Direction side) {
        return storedEssentia <= 0 ? null : currentAspect;
    }

    @Override
    public int getEssentiaAmount(Direction side) {
        return storedEssentia;
    }

    @Override
    public int getMinimumSuction() {
        return 1;
    }

    @Override
    public int spaceFor(Holder<IAspect> aspect, Direction side) {
        return Math.max(0, MAX_ESSENTIA - storedEssentia);
    }

    // What the screen and the tooltip read

    public int getStoredEssentia() {
        return storedEssentia;
    }

    public int getMaxEssentia() {
        return MAX_ESSENTIA;
    }

    public double getAePerTick() {
        return aePerTick;
    }

    public int getBurnTicksRemaining() {
        return burnTicksRemaining;
    }

    public int getTotalBurnTicks() {
        return totalBurnTicks;
    }

    public float getBurnProgress() {
        return totalBurnTicks <= 0 ? 0.0F : 1.0F - (float) burnTicksRemaining / totalBurnTicks;
    }

    /** What the machine is doing and why, for the tooltip and the screen. */
    public BurnState getBurnState() {
        return burnState;
    }

    /**
     * Whether the chamber is converting essentia right now; a full slot, or a grid with nothing but this
     * machine on it, holds the burn back without spending it.
     */
    public boolean isBurning() {
        return burnState == BurnState.BURNING;
    }

    /** Whether the energy slot is full enough to hold the burn back, a unit loaded or not. */
    public boolean isPaused() {
        return burnState == BurnState.PAUSED_FULL;
    }

    public double getStoredEnergy() {
        return storedEnergy;
    }

    public double getMaxEnergyStorage() {
        return MAX_ENERGY_STORAGE;
    }

    public double getMaxOutputPerTick() {
        return MAX_OUTPUT_PER_TICK;
    }

    public float getEnergyFillProgress() {
        return (float) (storedEnergy / MAX_ENERGY_STORAGE);
    }

    // What the client is told

    /**
     * The client's copy: the state, the rate it burns at and the fuel in the buffer, so a tooltip is drawn
     * from what the machine is doing now rather than from a snapshot taken when it was first looked at.
     * Jade's server data is collected once per hover, and the owner watched a machine that had just lost its
     * only consumer go on saying "Device Online" for as long as the tooltip stayed open.
     *
     * <p>Sent by {@link #markForClientUpdate()}, which the state transitions and every change of fuel
     * already call, so nothing here is broadcast per tick. What is deliberately left out is what moves every
     * tick or every visit - the burn's countdown and the energy in the slot: a number that freezes while it
     * is watched is worse than no number, and syncing those would be traffic for a tooltip. The machine's
     * own screen carries them live through its menu.
     */
    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeByte(burnState.ordinal());
        data.writeDouble(aePerTick);
        data.writeVarInt(storedEssentia);
        ResourceLocation aspect = getCurrentAspect();
        data.writeBoolean(aspect != null);
        if (aspect != null) {
            data.writeResourceLocation(aspect);
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        BurnState state = BurnState.byOrdinal(data.readByte());
        double rate = data.readDouble();
        int essentia = data.readVarInt();
        ResourceLocation aspect = data.readBoolean() ? data.readResourceLocation() : null;

        changed |= burnState != state
                || aePerTick != rate
                || storedEssentia != essentia
                || !Objects.equals(getCurrentAspect(), aspect);
        burnState = state;
        aePerTick = rate;
        storedEssentia = essentia;
        currentAspect = aspect == null
                ? null
                : Aspects.resolve(data.registryAccess(), ResourceKey.create(IAspect.REGISTRY_KEY, aspect));
        return changed;
    }

    // Persistence

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("StoredEssentia", storedEssentia);
        tag.putInt("BurnTicksRemaining", burnTicksRemaining);
        tag.putInt("TotalBurnTicks", totalBurnTicks);
        tag.putDouble("AePerTick", aePerTick);
        tag.putDouble("StoredEnergy", storedEnergy);
        ResourceLocation id = getCurrentAspect();
        if (id != null) {
            tag.putString("CurrentAspect", id.toString());
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        storedEssentia = Math.clamp(tag.getInt("StoredEssentia"), 0, MAX_ESSENTIA);
        burnTicksRemaining = tag.getInt("BurnTicksRemaining");
        totalBurnTicks = tag.getInt("TotalBurnTicks");
        aePerTick = tag.getDouble("AePerTick");
        storedEnergy = Math.min(tag.getDouble("StoredEnergy"), MAX_ENERGY_STORAGE);
        // Read off the slot rather than saved with it: a tag that carried both could carry them disagreeing.
        // Whether there is a network is not knowable here; the first tick decides it.
        burnState = MAX_ENERGY_STORAGE - storedEnergy < burnTickPower()
                ? BurnState.PAUSED_FULL
                : BurnState.IDLE;
        currentAspect = null;
        if (tag.contains("CurrentAspect")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("CurrentAspect"));
            if (id != null) {
                currentAspect = Aspects.resolve(registries, ResourceKey.create(IAspect.REGISTRY_KEY, id));
            }
        }
    }

    /** The buffer's aspect as a holder; the screen wants its colour and the tooltip wants its name. */
    public @Nullable Holder<IAspect> currentAspectHolder() {
        return currentAspect;
    }

    /** The screen, opened by right-clicking the machine. See {@code BlockEssentiaVibrationChamber}. */
    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int containerId, net.minecraft.world.entity.player.Inventory inventory,
            net.minecraft.world.entity.player.Player player) {
        return new thaumicenergistics_ce.menu.MenuEssentiaVibrationChamber(containerId, inventory, this);
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable(
                getBlockState().getBlock().getDescriptionId());
    }

    /** The aspects that burn better than the rest, for Jade and the screen. */
    public static List<String> fuelAspectHint() {
        return List.of(ASPECT_POTENTIA, ASPECT_IGNIS);
    }
}
