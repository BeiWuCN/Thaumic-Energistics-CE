package thaumicenergistics_ce.blockentity.vibrationchamber;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.MachineMenus;
import thaumicenergistics_ce.init.ModBlockEntities;

/**
 * The Essentia Vibration Chamber burns essentia to generate AE, ticking the three parts beside it.
 * Potentia burns 1.6x duration and power, ignis at the base rate and everything else at half; the
 * buffer is a count rather than an aspect list, so the aspect kept is for display only, and
 * {@link BurnState} is the one answer to the burn. AE2 destroys what the grid refuses.
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

        public boolean mayBurn() { return this == BURNING || this == IDLE; }

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

    ChamberEssentiaTank tank() { return tank; }
    ChamberEnergyOutput energy() { return energy; }
    ChamberBurn burn() { return burn; }
    ChamberTrace trace() { return trace; }

    void markChanged() { setChanged(); markForClientUpdate(); }

    public BlockEntityEssentiaVibrationChamber(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(), pos, state);
        // Idle 0.0 and channel-free, as AE2's own generators are (VibrationChamberBlockEntity:57,
        // ChargerBlockEntity:43): a channel would go dark when a flat network needs it most.
        getMainNode().setIdlePowerUsage(0.0).setFlags().addService(IGridTickable.class, this);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TICK_RATE_BURNING, TICK_RATE_IDLE, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        return ChamberTick.advance(this, node, ticksSinceLast);
    }

    @Override
    public AspectList contents() { return tank.contents(); }

    @Override
    public int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
        return tank.insert(aspect, amount, simulate, burn.paused());
    }

    @Override
    public int extract(Holder<IAspect> aspect, int amount, boolean simulate) { return 0; }

    @Override
    public long contentRevision() { return tank.revision(); }

    @Override
    public boolean isConnectable(Direction side) { return true; }

    @Override
    public boolean canInputFrom(Direction side) { return true; }

    /** True so pipes see a destination, not a dead end, though {@link #takeEssentia} takes nothing. */
    @Override
    public boolean canOutputTo(Direction side) { return true; }

    @Override
    public void setSuction(@Nullable Holder<IAspect> aspect, int amount) {}

    @Override
    public @Nullable Holder<IAspect> getSuctionType(Direction side) {
        return null;
    }

    /** Zero when the buffer or the energy slot is full: pipes steer by this number, and a full machine
     * advertising suction would draw essentia it cannot burn. */
    @Override
    public int getSuctionAmount(Direction side) { return tank.suctionAmount(burn.paused()); }

    @Override
    public int takeEssentia(Holder<IAspect> aspect, int amount, Direction side) { return 0; }

    @Override
    public int addEssentia(Holder<IAspect> aspect, int amount, Direction side) {
        return insert(aspect, amount, false);
    }

    @Override
    public @Nullable Holder<IAspect> getEssentiaType(Direction side) {
        return tank.amount() <= 0 ? null : tank.aspect();
    }

    @Override
    public int getEssentiaAmount(Direction side) { return tank.amount(); }

    @Override
    public int getMinimumSuction() { return 1; }

    @Override
    public int spaceFor(Holder<IAspect> aspect, Direction side) { return tank.space(); }

    public int getStoredEssentia() { return tank.amount(); }
    public int getMaxEssentia() { return MAX_ESSENTIA; }
    public double getAePerTick() { return burn.aePerTick(); }
    public int getBurnTicksRemaining() { return burn.remaining(); }
    public int getTotalBurnTicks() { return burn.total(); }
    public float getBurnProgress() { return burn.progress(); }
    public BurnState getBurnState() { return burn.state(); }
    public boolean isBurning() { return burn.burning(); }
    public boolean isPaused() { return burn.paused(); }
    public double getStoredEnergy() { return energy.amount(); }
    public double getMaxEnergyStorage() { return MAX_ENERGY_STORAGE; }
    public double getMaxOutputPerTick() { return MAX_OUTPUT_PER_TICK; }
    public float getEnergyFillProgress() { return energy.fillProgress(); }
    public @Nullable ResourceLocation getCurrentAspect() { return tank.aspectId(); }
    public @Nullable Holder<IAspect> currentAspectHolder() { return tank.aspect(); }

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
        burn.applyStreamed(streamedState, streamedRate); tank.set(essentia, aspect);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        VibrationChamberSync.writePersistent(tag, this);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        VibrationChamberSync.applyPersistent(this, tag, registries);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return MachineMenus.essentiaVibrationChamber(containerId, inventory, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    public static List<String> fuelAspectHint() {
        return List.of(ChamberBurn.ASPECT_POTENTIA, ChamberBurn.ASPECT_IGNIS);
    }
}
