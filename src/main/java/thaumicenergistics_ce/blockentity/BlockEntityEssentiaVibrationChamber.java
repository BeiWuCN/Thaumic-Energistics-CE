package thaumicenergistics_ce.blockentity;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.ModBlockEntities;

/**
 * The Essentia Vibration Chamber burns essentia to generate AE, ticking the three parts beside it.
 * <ul>
 *   <li>Potentia burns 1.6x duration and power, ignis at the base rate, everything else at half.
 *   <li>The buffer is a count, not an aspect list; the aspect kept is for display only.
 *   <li>{@link BurnState} is the one answer to the burn; AE2 destroys what the grid refuses.
 * </ul>
 */
public class BlockEntityEssentiaVibrationChamber extends AENetworkedBlockEntity
        implements IGridTickable, IEssentiaStorage, IEssentiaTransport, MenuProvider {

    public static final int MAX_ESSENTIA = 64;

    /** Energy slot size in AE; AE2 quotes 16 kAE as 32,000 FE at two FE to the AE. */
    public static final double MAX_ENERGY_STORAGE = 16_000.0;

    public static final double MAX_OUTPUT_PER_TICK = 2_000.0;

    private static final int TICK_RATE_BURNING = 10;
    private static final int TICK_RATE_IDLE = 40;

    public enum BurnState {
        BURNING,
        /** Held back: the energy slot is full to within a tick's worth, so nothing fits. */
        PAUSED_FULL,
        /** Nothing but this machine is on its grid: nowhere for the power to go. */
        NO_NETWORK,
        IDLE;

        public boolean mayBurn() {
            return this == BURNING || this == IDLE;
        }

        public static BurnState byOrdinal(int ordinal) {
            BurnState[] states = values();
            return ordinal >= 0 && ordinal < states.length ? states[ordinal] : IDLE;
        }
    }

    // The fuel, the burn and the power live in ChamberBurn, ChamberEssentiaTank and ChamberEnergyOutput:
    // this class ticks them and is the face the grid, the pipes and the screen are answered by.
    private final ChamberEssentiaTank tank = new ChamberEssentiaTank(this);
    private final ChamberEnergyOutput energy = new ChamberEnergyOutput(this);
    private final ChamberBurn burn = new ChamberBurn(this, tank, energy);
    private final ChamberTrace trace = new ChamberTrace(this, tank, energy, burn);

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
        if (tank.hasRoom() && !burn.paused()) {
            tank.pull();
        }
        trace.log();

        // Grid, not active grid: a generator must not wait to be powered, and a flat grid needs it most.
        IGrid grid = node.getGrid();
        if (grid == null) {
            // The answer a grid of one gives too: there is nothing to hand the power to.
            burn.update(false);
            return TickRateModulation.SLOWER;
        }

        // Asked of what the grid holds, not of how big it is: {@link ChamberEnergyOutput#hasNetwork}.
        boolean onNetwork = energy.hasNetwork(grid, node);

        // Output first, so room opens in the same visit that finds the slot full.
        if (onNetwork) {
            energy.output(grid, ticksSinceLast);
        }
        burn.update(onNetwork);

        // Held back: no room in the slot for the power, or nothing on the grid to take it.
        if (!burn.mayBurn()) {
            return TickRateModulation.SAME;
        }

        if (burn.remaining() > 0) {
            // Only ticks whose power fits are burnt, the rest later: the unit freezes, it does not restart.
            // Crediting a whole wake-up is what spent fuel on power with nowhere to put it.
            double perTick = burn.tickPower();
            int burnt = burn.ticksThatFit(ticksSinceLast);
            energy.add(burnt * perTick);
            boolean burntOut = burn.spend(burnt);
            burn.update(onNetwork);
            if (burntOut) {
                setChanged();
                markForClientUpdate();
            }
            return TickRateModulation.SAME;
        }

        if (tank.amount() <= 0) {
            return TickRateModulation.SLOWER;
        }

        burn.start();
        // Re-evaluate: the rate just changed, so a unit may be held back from the start.
        burn.update(onNetwork);
        return TickRateModulation.URGENT;
    }

    // IEssentiaStorage - the chamber as a container, so it can be filled

    @Override
    public AspectList contents() {
        return tank.contents();
    }

    @Override
    public int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
        return tank.insert(aspect, amount, simulate, burn.paused());
    }

    @Override
    public int extract(Holder<IAspect> aspect, int amount, boolean simulate) {
        return 0;
    }

    @Override
    public long contentRevision() {
        return tank.revision();
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
        return tank.suctionAmount(burn.paused());
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
        return tank.amount() <= 0 ? null : tank.aspect();
    }

    @Override
    public int getEssentiaAmount(Direction side) {
        return tank.amount();
    }

    @Override
    public int getMinimumSuction() {
        return 1;
    }

    @Override
    public int spaceFor(Holder<IAspect> aspect, Direction side) {
        return tank.space();
    }

    // What the screen and the tooltip read

    public int getStoredEssentia() {
        return tank.amount();
    }

    public int getMaxEssentia() {
        return MAX_ESSENTIA;
    }

    public double getAePerTick() {
        return burn.aePerTick();
    }

    public int getBurnTicksRemaining() {
        return burn.remaining();
    }

    public int getTotalBurnTicks() {
        return burn.total();
    }

    public float getBurnProgress() {
        return burn.progress();
    }

    public BurnState getBurnState() {
        return burn.state();
    }

    public boolean isBurning() {
        return burn.burning();
    }

    public boolean isPaused() {
        return burn.paused();
    }

    public double getStoredEnergy() {
        return energy.amount();
    }

    public double getMaxEnergyStorage() {
        return MAX_ENERGY_STORAGE;
    }

    public double getMaxOutputPerTick() {
        return MAX_OUTPUT_PER_TICK;
    }

    public float getEnergyFillProgress() {
        return energy.fillProgress();
    }

    public @Nullable ResourceLocation getCurrentAspect() {
        return tank.aspectId();
    }

    public @Nullable Holder<IAspect> currentAspectHolder() {
        return tank.aspect();
    }

    // What the client is told

    /**
     * The client's copy - state, burn rate and buffered fuel - sent only by {@link #markForClientUpdate()},
     * never per tick, or the countdown would freeze while watched.
     */
    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        VibrationChamberSync.writeStream(data, this);
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        // Both run: the base says whether it changed, the sync unit puts the stream into the machine.
        return super.readFromStream(data) | VibrationChamberSync.applyStreamed(this, data);
    }

    /** Package-private for {@link VibrationChamberSync}, which read the stream, and the reload below. */
    void applyStreamed(BurnState streamedState, double streamedRate, int essentia,
            @Nullable Holder<IAspect> aspect) {
        burn.applyStreamed(streamedState, streamedRate);
        tank.set(essentia, aspect);
    }

    // Persistence

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        VibrationChamberSync.writePersistent(tag, this);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        VibrationChamberSync.Persisted persisted = VibrationChamberSync.readPersistent(tag);
        tank.set(persisted.essentia(), persisted.aspect() == null
                ? null
                : Aspects.resolve(registries, ResourceKey.create(IAspect.REGISTRY_KEY, persisted.aspect())));
        burn.restore(persisted.burnTicksRemaining(), persisted.totalBurnTicks(), persisted.aePerTick());
        energy.restore(persisted.storedEnergy());
        // Read off the slot, not saved with it: a tag carrying both could carry them disagreeing.
        burn.setState(energy.isFull() ? BurnState.PAUSED_FULL : BurnState.IDLE);
    }

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

    public static List<String> fuelAspectHint() {
        return List.of(ChamberBurn.ASPECT_POTENTIA, ChamberBurn.ASPECT_IGNIS);
    }
}
