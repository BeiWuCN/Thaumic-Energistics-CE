package thaumicenergistics_ce.blockentity.gachabox;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.block.BlockGachaBox;
import thaumicenergistics_ce.block.BlockGachaBoxAggregator;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;
import thaumicenergistics_ce.init.ModBlockEntities;

/** Backs the box: the brain sits in a slot of its own and the blockstate mirrors it, the player it is
 * bound to lives here, and the AE reserve comes from the powered base class. The working parts are
 * pieces of their own - the brain, the cards, the cognitio banked toward a turn, the turn in flight,
 * what a turn pays and the essentia port on the face behind the screen - and this class keeps the
 * machine itself: the grid node, the power it draws, the second-by-second tick, what is missing
 * before a turn may start, and what has to reach disk. */
public class BlockEntityGachaBox extends AENetworkedPoweredBlockEntity implements IGridTickable {

    /** What the box may bank: four thousand AE, enough for a run of turns without a supply line. */
    public static final double ENERGY_CAPACITY = 4000.0;

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_OWNER_NAME = "OwnerName";

    /** A turn is counted in seconds, the unit the design talks in; one tick a second keeps the
     * model's own animation looking continuous without waking the box every tick. */
    private static final int TICKS_PER_SECOND = 20;

    /** How much a single ask pulls out of the grid: enough to matter, not the whole buffer at once. */
    private static final double CHARGE_PER_PASS = 800.0;

    private final GachaBrain brain = new GachaBrain(this);
    private final GachaCards cards = new GachaCards(this);
    private final GachaCognitio cognitio = new GachaCognitio(this);
    private final GachaTurn turn = new GachaTurn(this);
    private final GachaEssentiaPort port = new GachaEssentiaPort(this);

    private @Nullable UUID owner;
    private String ownerName = "";

    public BlockEntityGachaBox(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GACHA_BOX.get(), pos, state);
        setInternalMaxPower(ENERGY_CAPACITY);
        // The buffer is filled by the box's own ask (see chargeFromGrid), but a public storage is
        // also where a generator's surplus lands; WRITE keeps the box a requester, never a provider.
        setInternalPublicPowerStorage(true);
        setInternalPowerFlow(AccessRestriction.WRITE);
        // A channel, so the box earns its power like every other machine instead of banking it
        // wherever it is placed; without one the buffer sits still and so does the box.
        getMainNode().addService(IGridTickable.class, this).setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    /** AE2's own inventory stays empty: the brain's slot is handed out by name, and the four card
     * slots are filled by hand, so nothing is exposed here twice. */
    @Override
    public InternalInventory getInternalInventory() {
        return InternalInventory.empty();
    }

    /** Only the faces beside the screen meet the grid; the one behind it takes essentia and the top
     * carries the upper half. AE2 reads this in the constructor, facing already in the state. */
    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        Direction facing = getBlockState().getValue(BlockGachaBox.FACING);
        return facing.getAxis() == Direction.Axis.X
                ? EnumSet.of(Direction.NORTH, Direction.SOUTH)
                : EnumSet.of(Direction.EAST, Direction.WEST);
    }

    public boolean hasJar() {
        return getBlockState().getValue(BlockGachaBox.JAR);
    }

    /** The upper half has to sit on the box: without it nothing drives the machine. */
    public boolean structureComplete() {
        if (getLevel() == null) {
            return false;
        }
        return getLevel().getBlockState(getBlockPos().above()).getBlock() instanceof BlockGachaBoxAggregator;
    }

    public int cardCount() {
        return cards.count();
    }

    public boolean hasRoomForCard() {
        return cards.hasRoom();
    }

    /** The cards as they sit, for the tooltip's icon row: a read-only view, so nothing gets copied. */
    public List<ItemStack> cards() {
        return cards.view();
    }

    /** Puts one speed card in the first empty slot; false when the box is already full of them. */
    public boolean addCard(ItemStack held) {
        return cards.add(held);
    }

    /** Takes the cards back out, for the player who took the brain out of the box. */
    public List<ItemStack> takeCards() {
        return cards.take();
    }

    /** The brain's slot, so a hopper or a pipe can put one in or take one out. */
    public SimpleContainer brainSlot() {
        return brain.container();
    }

    /** Puts a brain in the slot; the caller has already checked that it is a jar brain. */
    public void addBrain(ItemStack held) {
        brain.put(held);
    }

    /** Takes the brain out of the slot, which leaves the box unbound; empty when there was none. */
    public ItemStack takeBrain() {
        return brain.take();
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TICKS_PER_SECOND, TICKS_PER_SECOND, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        if (!(level instanceof ServerLevel server)) {
            return TickRateModulation.IDLE;
        }
        // The buffer is filled by asking: AE2 machines draw their own power, so a box that only
        // waited to be charged would sit at zero however much the network behind it holds.
        if (getMainNode().isActive()) {
            chargeFromGrid();
        }
        // The reserve is topped up whenever the box could turn, a turn in flight or a flash included:
        // the tube delivers only on request, and one turn's worth is the most it may hold back.
        if (setupReason(server) == null) {
            port.sip(server);
        }
        // A result stands for a moment first: the next turn would otherwise overwrite the flash
        // before the player looking at the screen ever saw it.
        if (turn.flashing()) {
            turn.tickFlash();
            showScreen(turn.earned() ? BlockGachaBox.Screen.SUCCESS : BlockGachaBox.Screen.FAILED);
            return TickRateModulation.SAME;
        }
        GachaWait wait = waitReason(server);
        if (wait != null) {
            // A turn already in flight is held, not restarted: an owner who comes back resumes it.
            showScreen(wait.blankScreen() ? BlockGachaBox.Screen.OFF : BlockGachaBox.Screen.ON);
            return TickRateModulation.SAME;
        }
        showScreen(BlockGachaBox.Screen.WORKING);
        if (!turn.running()) {
            beginTurn(server);
            return TickRateModulation.SAME;
        }
        turn.advance();
        if (!turn.running()) {
            finishTurn(server);
        }
        return TickRateModulation.SAME;
    }

    /** The first thing standing in the way, in the order a player would fix them; null means turn. */
    public @Nullable GachaWait waitReason(ServerLevel server) {
        GachaWait setup = setupReason(server);
        if (setup != null) {
            return setup;
        }
        // A turn already in flight has been paid for: what is missing is the fuel for the next one,
        // and the box keeps its word rather than stopping half way through what it started.
        if (turn.running()) {
            return null;
        }
        if (!canPayEnergy()) {
            return GachaWait.NO_POWER;
        }
        if (cognitio.wants()) {
            return GachaWait.NO_ESSENTIA;
        }
        return null;
    }

    /** What is wrong with the box itself: without any of these there is nothing to turn at all. */
    @Nullable GachaWait setupReason(ServerLevel server) {
        if (!structureComplete()) {
            return GachaWait.NO_STRUCTURE;
        }
        if (!hasJar()) {
            return GachaWait.NO_BRAIN;
        }
        // A brain whose owner never reached disk is not a missing brain: it sits there unclaimed, and
        // one click on the box claims it, which is better than throwing away a jar the player placed.
        if (this.owner == null) {
            return GachaWait.UNBOUND_BRAIN;
        }
        if (!getMainNode().isActive()) {
            return GachaWait.NO_CHANNEL;
        }
        // Nobody is there to be given anything, and the design has the box wait rather than bank it.
        if (server.getPlayerByUUID(this.owner) == null) {
            return GachaWait.OWNER_OFFLINE;
        }
        return null;
    }

    /** Whether the box could actually run a turn now: the state the port asks its suction from. */
    boolean canTurnNow() {
        return level instanceof ServerLevel server && setupReason(server) == null;
    }

    /** The banked cognitio, which the port reads to know whether a point would be welcome. */
    GachaCognitio cognitio() {
        return cognitio;
    }

    /** The essentia port, for the capability lookup: every other face answers nothing at all. */
    public @Nullable IEssentiaTransport essentiaTransport(@Nullable Direction face) {
        return face == null || port.isConnectable(face) ? port : null;
    }

    /** The screen faces the player who placed the box, so the essentia port is the opposite face. */
    Direction backFace() {
        return getBlockState().getValue(BlockGachaBox.FACING).getOpposite();
    }

    /** Draws the length and the odds as one roll, then pays for the turn before it is allowed to run. */
    private void beginTurn(ServerLevel server) {
        GachaOdds.Turn drawn = GachaOdds.roll(server.getRandom(), cardCount());
        if (!payForTurn()) {
            return;
        }
        turn.start(drawn);
    }

    /** Two banked cognitio and the power for the turn, or the turn does not start at all. */
    private boolean payForTurn() {
        if (!cognitio.ready() || !canPayEnergy()) {
            return false;
        }
        if (!drawEnergy(GachaOdds.AE_PER_TURN)) {
            return false;
        }
        return cognitio.spend();
    }

    /** Pulls what the buffer still has room for straight out of the grid, the charger's own way. */
    private void chargeFromGrid() {
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        double room = Math.min(CHARGE_PER_PASS, getInternalMaxPower() - getInternalCurrentPower());
        if (room <= 0) {
            return;
        }
        IEnergyService energy = grid.getEnergyService();
        double pulled = energy.extractAEPower(room, Actionable.MODULATE, PowerMultiplier.ONE);
        if (pulled > 0) {
            injectExternalPower(PowerUnit.AE, pulled, Actionable.MODULATE);
        }
    }

    /** True when a turn could be paid for now, counting the buffer and the network behind it. */
    private boolean canPayEnergy() {
        double banked = getInternalCurrentPower();
        if (banked >= GachaOdds.AE_PER_TURN) {
            return true;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return false;
        }
        IEnergyService energy = grid.getEnergyService();
        return banked + energy.getStoredPower() >= GachaOdds.AE_PER_TURN;
    }

    /** Takes the turn's power out of the buffer, the grid covering whatever the buffer is missing. */
    private boolean drawEnergy(double need) {
        double banked = extractAEPower(need, Actionable.MODULATE, PowerMultiplier.ONE);
        double missing = need - banked;
        if (missing <= 0) {
            return true;
        }
        IGrid grid = getMainNode().getGrid();
        double pulled = 0;
        if (grid != null) {
            IEnergyService energy = grid.getEnergyService();
            pulled = energy.extractAEPower(missing, Actionable.MODULATE, PowerMultiplier.ONE);
        }
        if (pulled >= missing) {
            return true;
        }
        // The turn is not happening, so what came out of the buffer goes back into it.
        injectExternalPower(PowerUnit.AE, banked, Actionable.MODULATE);
        return false;
    }

    /** Hands out what this turn drew, then leaves the result on the screen for a moment. */
    private void finishTurn(ServerLevel server) {
        turn.settle();
        if (!turn.earned() || this.owner == null) {
            return;
        }
        if (!(server.getPlayerByUUID(this.owner) instanceof ServerPlayer player)) {
            return;
        }
        GachaPayout.grant(player);
    }

    /** The screen is a blockstate, so the block itself saves the change and sends it to clients. */
    private void showScreen(BlockGachaBox.Screen screen) {
        BlockState state = getBlockState();
        if (state.getValue(BlockGachaBox.SCREEN) != screen) {
            level.setBlockAndUpdate(worldPosition, state.setValue(BlockGachaBox.SCREEN, screen));
        }
    }

    /** Binds the box to whoever put the brain in, and voids the turn the last owner left behind. */
    public void bind(Player player) {
        this.owner = player.getUUID();
        this.ownerName = player.getName().getString();
        turn.reset();
    }

    /** Unbinding throws the turn away rather than banking it: an empty box does not work. */
    public void unbind() {
        this.owner = null;
        this.ownerName = "";
        turn.reset();
    }

    /** The name of the bound player, or null while the box holds no brain. */
    public @Nullable String ownerName() {
        return this.owner == null ? null : this.ownerName;
    }

    /** A brain is sitting in the box but belongs to nobody: what an interrupted save leaves behind. */
    public boolean hasUnboundBrain() {
        return hasJar() && this.owner == null;
    }

    /** Whether this player may take the brain back: the one the box is bound to, or anyone while the
     * box holds an unclaimed brain and so belongs to nobody yet. */
    public boolean mayTakeBrain(Player player) {
        return this.owner == null || this.owner.equals(player.getUUID());
    }

    /** Spills what the box is holding: the brain out of its slot, and the cards. */
    public void dropContents(boolean holdsBrain) {
        if (level == null) {
            return;
        }
        ItemStack held = takeBrain();
        if (held.isEmpty() && holdsBrain) {
            // A save from the scheme that kept the brain in the blockstate alone still owes one.
            held = TcRegistry.jarBrainStack();
        }
        spill(held);
        for (ItemStack card : cards.take()) {
            spill(card);
        }
    }

    private void spill(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        Containers.dropItemStack(
                level,
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5,
                stack);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.owner != null) {
            tag.putUUID(TAG_OWNER, this.owner);
            tag.putString(TAG_OWNER_NAME, this.ownerName);
        }
        cards.save(tag, registries);
        brain.save(tag, registries);
        turn.save(tag);
        cognitio.save(tag);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        // The state is read before the slot, which clears it while loading: a save that kept the
        // brain in the blockstate alone is turned back into an item rather than losing it there.
        boolean stateHeldBrain = getBlockState().getValue(BlockGachaBox.JAR);
        brain.load(tag, registries);
        if (stateHeldBrain && !brain.has()) {
            brain.adopt();
        }
        this.owner = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        this.ownerName = tag.getString(TAG_OWNER_NAME);
        cards.load(tag, registries);
        turn.load(tag);
        cognitio.load(tag);
    }
}
