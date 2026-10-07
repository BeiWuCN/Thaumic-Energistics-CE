package thaumicenergistics_ce.blockentity.gachabox;

import appeng.api.config.AccessRestriction;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
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

/** 箱子本体。网格节点、每秒一次的 tick、开转前的检查、存档都在这儿；脑、卡片、
 * cognitio、转动过程、源质端口、绑定玩家、从网格取的电各自分出去当小类。 */
public class BlockEntityGachaBox extends AENetworkedPoweredBlockEntity implements IGridTickable {

    /** 20 tick 即一秒。转动按秒走，每秒醒一次画动画，不必每 tick 唤醒箱子。 */
    private static final int TICKS_PER_SECOND = 20;

    private final GachaBrain brain = new GachaBrain(this);
    private final GachaCards cards = new GachaCards(this);
    private final GachaCognitio cognitio = new GachaCognitio(this);
    private final GachaTurn turn = new GachaTurn(this);
    private final GachaEssentiaPort port = new GachaEssentiaPort(this);
    private final GachaPower power = new GachaPower(this);
    private final GachaOwner owner = new GachaOwner();

    public BlockEntityGachaBox(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GACHA_BOX.get(), pos, state);
        setInternalMaxPower(GachaPower.ENERGY_CAPACITY);
        // 缓冲区靠箱子自己索取（[GachaPower.chargeFromGrid]）填充；公开存储是 WRITE，
        // 发电机的盈余能落进来，箱子只当索取者。
        setInternalPublicPowerStorage(true);
        setInternalPowerFlow(AccessRestriction.WRITE);
        // 要一个频道：没频道就不通电，箱子整个不动。
        getMainNode().addService(IGridTickable.class, this).setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    /** AE2 的物品栏留空。脑有独立槽位、卡片走手工填充，这里再暴露一次就是重复。 */
    @Override
    public InternalInventory getInternalInventory() {
        return InternalInventory.empty();
    }

    /** 只有屏幕两侧的面接入网格；背面接源质，顶面放上半部分。
     * AE2 在构造器里读它，那时朝向已进状态。 */
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

    /** 上半部分坐在箱子上才算完整；缺了它机器不转。 */
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

    /** 卡片当前的样子，给 tooltip 的图标行用。只读，不复制。 */
    public List<ItemStack> cards() {
        return cards.view();
    }

    public boolean addCard(ItemStack held) {
        return cards.add(held);
    }

    public List<ItemStack> takeCards() {
        return cards.take();
    }

    /** 脑槽位，漏斗和管道靠它放进或取出一颗脑。 */
    public SimpleContainer brainSlot() {
        return brain.container();
    }

    /** 放进一颗脑，调用方已确认是缸中之脑。 */
    public void addBrain(ItemStack held) {
        brain.put(held);
    }

    /** 取出脑会解除箱子绑定；槽里没脑时返回空。 */
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
        // AE2 机器自己抽电，只等着被充电的箱子永远是零，得自己索取。
        if (getMainNode().isActive()) {
            power.chargeFromGrid();
        }
        // 能转动就补满储备，进行中的转动和闪现也算在内。
        if (setupReason(server) == null) {
            port.sip(server);
        }
        // 结果停留片刻，不然下一次转动会盖掉玩家还没看到的闪光。
        if (turn.flashing()) {
            turn.tickFlash();
            showScreen(turn.earned() ? BlockGachaBox.Screen.SUCCESS : BlockGachaBox.Screen.FAILED);
            return TickRateModulation.SAME;
        }
        GachaWait wait = waitReason(server);
        if (wait != null) {
            // 所有者回来时接着转，不重启。
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

    /** 第一个挡路的条件，按玩家会修的先后排；null 表示可以转。 */
    public @Nullable GachaWait waitReason(ServerLevel server) {
        GachaWait setup = setupReason(server);
        if (setup != null) {
            return setup;
        }
        // 进行中的转动已经付过费，这里只查下一次的燃料。
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

    /** 箱子自己缺什么，缺一项就没东西可转。 */
    @Nullable GachaWait setupReason(ServerLevel server) {
        if (!structureComplete()) {
            return GachaWait.NO_STRUCTURE;
        }
        if (!hasJar()) {
            return GachaWait.NO_BRAIN;
        }
        // 脑还在原地没人认领，点一下箱子就能认领。
        if (!owner.isBound()) {
            return GachaWait.UNBOUND_BRAIN;
        }
        if (!getMainNode().isActive()) {
            return GachaWait.NO_CHANNEL;
        }
        // 所有者不在线，箱子就等着，不把产出存起来。
        if (owner.player(server) == null) {
            return GachaWait.OWNER_OFFLINE;
        }
        return null;
    }

    /** 现在能不能真转一次；源质端口按这个状态算吸力。 */
    boolean canTurnNow() {
        return level instanceof ServerLevel server && setupReason(server) == null;
    }

    /** 已存入的 cognitio；端口读它判断一点会不会被接受。 */
    GachaCognitio cognitio() {
        return cognitio;
    }

    /** 源质能力只在背面给出，别的面都不回答。 */
    public @Nullable IEssentiaTransport essentiaTransport(@Nullable Direction face) {
        return face == null || port.isConnectable(face) ? port : null;
    }

    /** 屏幕朝放置箱子的玩家，背面就是源质端口。 */
    Direction backFace() {
        return getBlockState().getValue(BlockGachaBox.FACING).getOpposite();
    }

    /** 一次抽取同时定下时长和概率，付过费才开转。 */
    private void beginTurn(ServerLevel server) {
        GachaOdds.Turn drawn = GachaOdds.roll(server.getRandom(), cardCount());
        if (!payForTurn()) {
            return;
        }
        turn.start(drawn);
    }

    /** 扣两点 cognitio 加这次转动的电力；付不出就不开转。 */
    private boolean payForTurn() {
        if (!cognitio.ready() || !power.canPay()) {
            return false;
        }
        if (!power.spend(GachaOdds.AE_PER_TURN)) {
            return false;
        }
        return cognitio.spend();
    }

    /** 把抽到的东西发出去，结果在屏幕上停片刻。 */
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

    /** 屏幕是方块状态，方块自己落盘并发给客户端。 */
    private void showScreen(BlockGachaBox.Screen screen) {
        BlockState state = getBlockState();
        if (state.getValue(BlockGachaBox.SCREEN) != screen) {
            level.setBlockAndUpdate(worldPosition, state.setValue(BlockGachaBox.SCREEN, screen));
        }
    }

    /** 绑定到放入脑的玩家，作废上一个所有者留下的转动。 */
    public void bind(Player player) {
        owner.bind(player);
        turn.reset();
    }

    /** 解绑会把转动丢掉，不存起来；空箱子不工作。 */
    public void unbind() {
        owner.unbind();
        turn.reset();
    }

    public @Nullable String ownerName() {
        return owner.name();
    }

    /** 脑在箱子里，不属于任何人：存档中断留下的。 */
    public boolean hasUnboundBrain() {
        return hasJar() && !owner.isBound();
    }

    /** 能不能取回脑：绑定者本人；箱子持未认领的脑时谁都可以。 */
    public boolean mayTakeBrain(Player player) {
        return owner.mayTakeBrain(player);
    }

    public void dropContents(boolean holdsBrain) {
        if (level == null) {
            return;
        }
        ItemStack held = takeBrain();
        if (held.isEmpty() && holdsBrain) {
            // 旧存档把脑只留在方块状态里，这里补一颗。
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
        owner.save(tag);
        cards.save(tag, registries);
        brain.save(tag, registries);
        turn.save(tag);
        cognitio.save(tag);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        // 状态要在槽位之前读，加载会清空槽位；老存档的脑转回物品，不丢。
        boolean stateHeldBrain = getBlockState().getValue(BlockGachaBox.JAR);
        brain.load(tag, registries);
        if (stateHeldBrain && !brain.has()) {
            brain.adopt();
        }
        owner.load(tag);
        cards.load(tag, registries);
        turn.load(tag);
        cognitio.load(tag);
    }
}
