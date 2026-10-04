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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.ModBlockEntities;

/**
 * The Essentia Vibration Chamber burns essentia to generate AE.
 * <ul>
 *   <li>Potentia burns 1.6x duration and power, ignis at the base rate, everything else at half.
 *   <li>The buffer is a count, not an aspect list; the aspect kept is for display only.
 *   <li>{@link BurnState} is the one answer to the burn: a full slot or a grid that refuses holds it back.
 *   <li>AE2 destroys what the grid refuses (VibrationChamberBlockEntity:200-216).
 * </ul>
 */
public class BlockEntityEssentiaVibrationChamber extends AENetworkedBlockEntity
        implements IGridTickable, IEssentiaStorage, IEssentiaTransport, MenuProvider {

    /** How much essentia the chamber can hold, in units. */
    public static final int MAX_ESSENTIA = 64;

    /** Ticks one unit of ignis burns; a full buffer of 64 is most of an hour. */
    private static final int BASE_BURN_TICKS = 800;

    /** Power per tick while burning ignis. Potentia multiplies this, everything else halves it. */
    private static final double BASE_AE_PER_TICK = 200.0;

    /** Energy slot size in AE; AE2 quotes 16 kAE as 32,000 FE at two FE to the AE. */
    public static final double MAX_ENERGY_STORAGE = 16_000.0;

    /** Most of the slot handed out per tick; ten times one ignis unit's output, so it rarely binds. */
    public static final double MAX_OUTPUT_PER_TICK = 2_000.0;

    /** Room that must open in the slot before the burn resumes: hysteresis, larger than one potentia tick. */
    private static final double RESUME_MARGIN = 400.0;

    /** How often the chamber looks at the network: while burning, and while idle. */
    private static final int TICK_RATE_BURNING = 10;
    private static final int TICK_RATE_IDLE = 40;

    /** Pull strength on a pipe; wildcard, so any aspect is offered. */
    private static final int SUCTION = 128;

    /** The two aspects that burn better than the rest, by path. */
    private static final String ASPECT_POTENTIA = "potentia";
    private static final String ASPECT_IGNIS = "ignis";

    /**
     * What the machine is doing, and why when it is not burning: what the burn, the intake, the suction,
     * the tooltip and the screen all read.
     */
    public enum BurnState {
        /** Converting essentia into AE right now. */
        BURNING,
        /** Held back: the energy slot cannot take another tick of the burn. */
        PAUSED_FULL,
        /** Nothing but this machine is on its grid: nowhere for the power to go. */
        NO_NETWORK,
        /** Nothing loaded to burn, and room for it. */
        IDLE;

        /** Whether the machine may spend fuel, rather than being held back by the slot or the network. */
        public boolean mayBurn() {
            return this == BURNING || this == IDLE;
        }

        /** The state an ordinal names, or {@link #IDLE} for an out-of-range index from a payload. */
        public static BurnState byOrdinal(int ordinal) {
            BurnState[] states = values();
            return ordinal >= 0 && ordinal < states.length ? states[ordinal] : IDLE;
        }
    }

    /** Essentia waiting to be burned. */
    private int storedEssentia;

    /** The aspect burned next, or burned last. Display only. */
    private @Nullable Holder<IAspect> currentAspect;

    private int burnTicksRemaining;
    private int totalBurnTicks;
    private double aePerTick;

    /** Power made and not yet handed to the network. */
    private double storedEnergy;

    /** What the machine is doing and why; the one answer every reader and display goes through. */
    private BurnState burnState = BurnState.IDLE;

    /**
     * Whether to log what the chamber sees of its neighbours, once a second; on with
     * {@code THAUMICENERGISTICS_EVC_TRACE=true}. Tells a pipe not reaching from one out-pulled.
     */
    private static final boolean TRACE = "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_EVC_TRACE"));

    private int tracedEssentia;

    private long nextTrace;

    /** Bumped whenever the buffer changes, so a cache of this container's contents notices. */
    private long revision;

    public BlockEntityEssentiaVibrationChamber(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(), pos, state);
        // Idle 0.0 and channel-free, as AE2's own generators are (VibrationChamberBlockEntity:57,
        // ChargerBlockEntity:43): a channel would go dark when a flat network needs it most.
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

        // Runs while the node is inactive too: fuel needs no network, and refusing it would back a pipe up.
        // Nothing is taken while the slot is full: the fuel's power would have nowhere to go.
        if (storedEssentia < MAX_ESSENTIA && !isPaused()) {
            pullEssentia();
        }
        traceIntake();

        // Grid, not active grid: a generator must not wait to be powered, and a flat grid needs it most.
        IGrid grid = node.getGrid();
        if (grid == null) {
            // The answer a grid of one gives too: there is nothing to hand the power to.
            updateBurnState(false);
            return TickRateModulation.SLOWER;
        }

        // Asked of what the grid holds, not of how big it is: {@link #hasNetwork}.
        boolean onNetwork = hasNetwork(grid, node);

        // Output first, so room opens in the same visit that finds the slot full.
        if (onNetwork) {
            outputEnergy(grid, ticksSinceLast);
        }
        updateBurnState(onNetwork);

        // Held back: no room in the slot for the power, or nothing on the grid to take it.
        if (!burnState.mayBurn()) {
            return TickRateModulation.SAME;
        }

        if (burnTicksRemaining > 0) {
            // Only ticks whose power fits are burnt, the rest later: the unit freezes, it does not restart.
            // Crediting a whole wake-up is what spent fuel on power with nowhere to put it.
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
        // Re-evaluate: the rate just changed, so a unit may be held back from the start.
        updateBurnState(onNetwork);
        return TickRateModulation.URGENT;
    }

    /**
     * Whether this grid can take the power: a node holding it ({@link IAEPowerStorage}) or machines that draw
     * it. A mere grid is not enough: its 25 AE per node buffer (GridEnergyStorage:83) takes it.
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

    /** Hands power to the network, up to {@link #MAX_OUTPUT_PER_TICK} a tick; what it refuses stays. */
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
     * Re-decides {@link #burnState} and announces it only when it changes; not read by callers. Full stays
     * full until {@link #RESUME_MARGIN} of room opens again.
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

    /** One tick of the burn, or of the smallest burn possible; never zero, so room/rate is a tick count. */
    private double burnTickPower() {
        return burnTicksRemaining > 0 ? Math.max(aePerTick, 1.0) : BASE_AE_PER_TICK / 2.0;
    }

    // Fuel

    /**
     * Draws one unit from a neighbouring container: there is no "as much as fits" call, and returning an
     * excess is where essentia gets lost.
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
     * Pulls from a Thaumaturge essentia tube, which does not push into the machines it passes: the
     * destination is the side that asks, as in Thaumaturge's port. The tests below are that port's.
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

    /** Reports, once a second, one line per side that has anything on it. See {@link #TRACE}. */
    private void traceIntake() {
        if (!TRACE || level == null || !(level instanceof ServerLevel server)) {
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

    /** Puts essentia in the buffer and remembers the aspect, for the tooltip and the screen. */
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
        // The tooltip reads the buffer client-side; fuel arrives a unit at a time, not per tick.
        markForClientUpdate();
    }

    /** Spends one buffered unit and starts its burn; its power is made tick by tick. */
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

    /** The whole buffer under the aspect put in last: the buffer is a count, so this answer is lossy. */
    @Override
    public AspectList contents() {
        if (storedEssentia <= 0 || currentAspect == null) {
            return AspectList.EMPTY;
        }
        return AspectList.EMPTY.add(currentAspect, storedEssentia);
    }

    @Override
    public int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
        // Same rule as the pull and the suction: a full slot takes nothing, however it is offered.
        if (aspect == null || amount <= 0 || isPaused()) {
            return 0;
        }
        // Floored at 0: a saved count above the cap would go negative, read as "nothing taken".
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

    /** True so pipes see a destination, not a dead end, though {@link #takeEssentia} takes nothing. */
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

    /** Zero when the buffer or the energy slot is full: pipes steer by this number, and a full machine
     * advertising suction would draw essentia it cannot burn. */
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

    /** Whether the chamber is converting essentia right now; a held-back burn spends nothing. */
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
     * The client's copy - state, burn rate and buffered fuel - so Jade's once-per-hover snapshot is not
     * what the tooltip shows. Sent only by {@link #markForClientUpdate()}, never per tick, or the
     * countdown and the slot's energy would freeze while watched.
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
        // Read off the slot, not saved with it: a tag carrying both could carry them disagreeing.
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
    public AbstractContainerMenu createMenu(
            int containerId, Inventory inventory,
            Player player) {
        return new thaumicenergistics_ce.menu.MenuEssentiaVibrationChamber(containerId, inventory, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(
                getBlockState().getBlock().getDescriptionId());
    }

    /** The aspects that burn better than the rest, for Jade and the screen. */
    public static List<String> fuelAspectHint() {
        return List.of(ASPECT_POTENTIA, ASPECT_IGNIS);
    }
}
