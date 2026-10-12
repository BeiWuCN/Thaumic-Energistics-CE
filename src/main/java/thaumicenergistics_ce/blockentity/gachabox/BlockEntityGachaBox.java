package thaumicenergistics_ce.blockentity.gachabox;

import thaumicenergistics_ce.config.ThEConfig;

import appeng.api.config.AccessRestriction;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.block.BlockGachaBox;
import thaumicenergistics_ce.block.BlockGachaBoxAggregator;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;
import thaumicenergistics_ce.registry.TECEBlockEntities;

public class BlockEntityGachaBox extends AENetworkedPoweredBlockEntity implements IGridTickable {

    private static final int TICKS_PER_SECOND = 20;

    private final GachaBrain brain = new GachaBrain(this);
    private final GachaCards cards = new GachaCards(this);
    private final GachaCognitio cognitio = new GachaCognitio(this);
    private final GachaTurn turn = new GachaTurn(this);
    private final GachaEssentiaPort port = new GachaEssentiaPort(this);
    private final GachaPower power = new GachaPower(this);
    private final GachaOwner owner = new GachaOwner();

    public BlockEntityGachaBox(BlockPos pos, BlockState state) {
        super(TECEBlockEntities.GACHA_BOX.get(), pos, state);
        setInternalMaxPower(GachaPower.ENERGY_CAPACITY);
        setInternalPublicPowerStorage(true);
        setInternalPowerFlow(AccessRestriction.WRITE);
        getMainNode().addService(IGridTickable.class, this).setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    @Override
    public InternalInventory getInternalInventory() {
        return InternalInventory.empty();
    }

    public boolean hasJar() {
        return getBlockState().hasProperty(BlockGachaBox.JAR)
                && getBlockState().getValue(BlockGachaBox.JAR);
    }

    public boolean structureComplete() {
        return level != null && level.getBlockState(getBlockPos().above()).getBlock() instanceof BlockGachaBoxAggregator;
    }

    public int cardCount() {
        return cards.count();
    }

    public boolean hasRoomForCard() {
        return cards.hasRoom();
    }

    public List<ItemStack> cards() {
        return cards.view();
    }

    public boolean addCard(ItemStack held) {
        return cards.add(held);
    }

    public List<ItemStack> takeCards() {
        return cards.take();
    }

    public SimpleContainer brainSlot() {
        return brain.container();
    }

    public void addBrain(ItemStack held) {
        brain.put(held);
    }

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
        if (!ThEConfig.GACHA_ENABLED.get()) {
            showScreen(BlockGachaBox.Screen.OFF);
            return TickRateModulation.SAME;
        }
        if (getMainNode().isActive()) {
            power.chargeFromGrid();
        }
        if (setupReason(server) == null) {
            port.sip(server);
        }
        if (turn.flashing()) {
            turn.tickFlash();
            showScreen(turn.earned() ? BlockGachaBox.Screen.SUCCESS : BlockGachaBox.Screen.FAILED);
            return TickRateModulation.SAME;
        }
        GachaWait wait = waitReason(server);
        if (wait != null) {
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

    public @Nullable GachaWait waitReason(ServerLevel server) {
        GachaWait setup = setupReason(server);
        if (setup != null) {
            return setup;
        }
        if (turn.running()) {
            return null;
        }
        if (!power.canPay()) {
            return GachaWait.NO_POWER;
        }
        if (cognitio.wants()) {
            return GachaWait.NO_ESSENTIA;
        }
        return null;
    }

    @Nullable GachaWait setupReason(ServerLevel server) {
        if (!ThEConfig.GACHA_ENABLED.get()) {
            return GachaWait.DISABLED;
        }
        if (!structureComplete()) {
            return GachaWait.NO_STRUCTURE;
        }
        if (!hasJar()) {
            return GachaWait.NO_BRAIN;
        }
        if (!owner.isBound()) {
            return GachaWait.UNBOUND_BRAIN;
        }
        if (!getMainNode().isActive()) {
            return GachaWait.NO_CHANNEL;
        }
        if (owner.player(server) == null) {
            return GachaWait.OWNER_OFFLINE;
        }
        return null;
    }

    boolean canTurnNow() {
        return level instanceof ServerLevel server && setupReason(server) == null;
    }

    GachaCognitio cognitio() {
        return cognitio;
    }

    public @Nullable IEssentiaTransport essentiaTransport(@Nullable Direction face) {
        return face == null || port.isConnectable(face) ? port : null;
    }

    Direction backFace() {
        return getBlockState().getValue(BlockGachaBox.FACING).getOpposite();
    }

    private void beginTurn(ServerLevel server) {
        GachaOdds.Turn drawn = GachaOdds.roll(server.getRandom(), cardCount());
        if (!payForTurn()) {
            return;
        }
        turn.start(drawn);
    }

    private boolean payForTurn() {
        if (!cognitio.ready() || !power.canPay()) {
            return false;
        }
        if (!power.spend(ThEConfig.GACHA_POWER.get())) {
            return false;
        }
        return cognitio.spend();
    }

    private void finishTurn(ServerLevel server) {
        turn.settle();
        if (!turn.earned()) {
            return;
        }
        if (!(owner.player(server) instanceof ServerPlayer player)) {
            return;
        }
        GachaPayout.grant(player);
    }

    private void showScreen(BlockGachaBox.Screen screen) {
        BlockState state = getBlockState();
        if (state.getValue(BlockGachaBox.SCREEN) != screen) {
            level.setBlockAndUpdate(worldPosition, state.setValue(BlockGachaBox.SCREEN, screen));
        }
    }

    public void bind(Player player) {
        owner.bind(player);
        turn.reset();
    }

    public void unbind() {
        owner.unbind();
        turn.reset();
    }

    public @Nullable String ownerName() {
        return owner.name();
    }

    public boolean hasUnboundBrain() {
        return hasJar() && !owner.isBound();
    }

    public boolean mayTakeBrain(Player player) {
        return owner.mayTakeBrain(player);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        dropContents(state.getValue(BlockGachaBox.JAR));
    }

    public void dropContents(boolean holdsBrain) {
        if (level == null) {
            return;
        }
        ItemStack held = takeBrain();
        if (held.isEmpty() && holdsBrain) {
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
    public void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        owner.save(output);
        cards.save(output);
        brain.save(output);
        turn.save(output);
        cognitio.save(output);
    }

    @Override
    public void loadTag(ValueInput input) {
        super.loadTag(input);
        boolean stateHeldBrain = getBlockState().getValue(BlockGachaBox.JAR);
        brain.load(input);
        if (stateHeldBrain && !brain.has()) {
            brain.adopt();
        }
        owner.load(input);
        cards.load(input);
        turn.load(input);
        cognitio.load(input);
    }
}
