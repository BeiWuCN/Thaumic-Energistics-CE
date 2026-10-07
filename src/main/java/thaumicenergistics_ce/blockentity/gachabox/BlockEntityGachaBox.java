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

/** 支撑这个箱子：脑放在自己的槽位里、方块状态镜像它，它绑定的
 * 玩家就住在这里，AE 储备来自带供电的基类。工作部件都是
 * 各自独立的碎片——脑、卡片、为一次转动存入的 cognitio、进行中的转动、
 * 一次转动付出什么，以及屏幕背面那个面上的源质端口——而这个类
 * 保管机器本身：网格节点、它取的电、每秒一次的 tick、一次转动
 * 开始前还缺什么，以及什么东西必须落盘。 */
public class BlockEntityGachaBox extends AENetworkedPoweredBlockEntity implements IGridTickable {

    /** 箱子可以存入的量：四千 AE，够连续转动一轮而不需要供电线。 */
    public static final double ENERGY_CAPACITY = 4000.0;

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_OWNER_NAME = "OwnerName";

    /** 转动以秒计，这是设计使用的单位；每秒一次 tick 让模型自身的动画看起来
     * 是连续的，又不必每一 tick 都唤醒箱子。 */
    private static final int TICKS_PER_SECOND = 20;

    /** 单次索取从网格拉走多少：足以产生影响，又不是一次拿走整个缓冲。 */
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
        // 缓冲区由箱子自己索取来填充（见 chargeFromGrid），但公开存储也是发电机的
        // 盈余落进来的地方；WRITE 让箱子始终是索取者，绝不是提供者。
        setInternalPublicPowerStorage(true);
        setInternalPowerFlow(AccessRestriction.WRITE);
        // 一个频道，这样箱子像其他每台机器一样挣得自己的电力，而不是放在哪里都能存电；
        // 没有频道的话缓冲不动，箱子也不动。
        getMainNode().addService(IGridTickable.class, this).setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    /** AE2 自己的物品栏保持为空：脑的槽位按名字单独给出，四个卡片槽
     * 由手工填充，所以这里没有任何东西被暴露两次。 */
    @Override
    public InternalInventory getInternalInventory() {
        return InternalInventory.empty();
    }

    /** 只有屏幕两侧的面接入网格；屏幕背后的面接收源质，顶面
     * 承载上半部分。AE2 在构造器里读这个，此时朝向已经在状态里了。 */
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

    /** 上半部分必须坐在箱子上：没有它，没有任何东西驱动机器。 */
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

    /** 卡片当前的样子，用于 tooltip 的图标行：只读视图，所以不会复制任何东西。 */
    public List<ItemStack> cards() {
        return cards.view();
    }

    /** 把一张速度卡片放进第一个空槽位；箱子已经装满卡片时返回 false。 */
    public boolean addCard(ItemStack held) {
        return cards.add(held);
    }

    /** 把卡片取出来，给那个从箱子里取走脑的玩家。 */
    public List<ItemStack> takeCards() {
        return cards.take();
    }

    /** 脑的槽位，这样漏斗或管道可以放入或取出一颗。 */
    public SimpleContainer brainSlot() {
        return brain.container();
    }

    /** 把一颗脑放进槽位；调用方已经检查过它是缸中之脑。 */
    public void addBrain(ItemStack held) {
        brain.put(held);
    }

    /** 把脑从槽位里取出，这会让箱子解除绑定；原本没有脑时返回空。 */
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
        // 缓冲区靠索取填充：AE2 机器自己抽取电力，所以一个只等着被充电的箱子
        // 无论背后网络持有多大都会停在零。
        if (getMainNode().isActive()) {
            chargeFromGrid();
        }
        // 只要箱子能够转动，储备就会被补满，进行中的转动或一次闪现也算在内：
        // 管道只在被请求时递送，而一次转动的量是它最多能扣留的。
        if (setupReason(server) == null) {
            port.sip(server);
        }
        // 结果会先停留片刻：否则下一次转动会在玩家看到屏幕上的闪光之前
        // 就把它覆盖掉。
        if (turn.flashing()) {
            turn.tickFlash();
            showScreen(turn.earned() ? BlockGachaBox.Screen.SUCCESS : BlockGachaBox.Screen.FAILED);
            return TickRateModulation.SAME;
        }
        GachaWait wait = waitReason(server);
        if (wait != null) {
            // 已经在进行中的转动被保持，而不是重启：回来的所有者会接着它继续。
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

    /** 第一个挡路的东西，按玩家会修复它们的顺序排列；null 表示可以转动。 */
    public @Nullable GachaWait waitReason(ServerLevel server) {
        GachaWait setup = setupReason(server);
        if (setup != null) {
            return setup;
        }
        // 已经在进行中的转动已经付过费了：缺的是下一次转动的燃料，
        // 箱子会信守承诺，而不是在它已经开始的事情中途停下。
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

    /** 箱子自身出了什么问题：没有其中任何一项，就根本没有东西可转。 */
    @Nullable GachaWait setupReason(ServerLevel server) {
        if (!structureComplete()) {
            return GachaWait.NO_STRUCTURE;
        }
        if (!hasJar()) {
            return GachaWait.NO_BRAIN;
        }
        // 所有者从未落盘的脑不是缺失的脑：它就搁在那里无人认领，点一下箱子
        // 就能认领，这比扔掉玩家放置的一个罐要好。
        if (this.owner == null) {
            return GachaWait.UNBOUND_BRAIN;
        }
        if (!getMainNode().isActive()) {
            return GachaWait.NO_CHANNEL;
        }
        // 没有人在那里可以接收任何东西，而设计让箱子等待，而不是把它存起来。
        if (server.getPlayerByUUID(this.owner) == null) {
            return GachaWait.OWNER_OFFLINE;
        }
        return null;
    }

    /** 箱子现在是否真的能转一次：端口就是从这个状态得出它的吸力的。 */
    boolean canTurnNow() {
        return level instanceof ServerLevel server && setupReason(server) == null;
    }

    /** 已存入的 cognitio，端口读取它来得知一点是否会被接受。 */
    GachaCognitio cognitio() {
        return cognitio;
    }

    /** 源质端口，用于能力查找：其他所有面都完全不回答。 */
    public @Nullable IEssentiaTransport essentiaTransport(@Nullable Direction face) {
        return face == null || port.isConnectable(face) ? port : null;
    }

    /** 屏幕面向放置箱子的玩家，所以源质端口是相反的那个面。 */
    Direction backFace() {
        return getBlockState().getValue(BlockGachaBox.FACING).getOpposite();
    }

    /** 一次抽取同时决定时长和概率，然后在允许转动之前先为它付费。 */
    private void beginTurn(ServerLevel server) {
        GachaOdds.Turn drawn = GachaOdds.roll(server.getRandom(), cardCount());
        if (!payForTurn()) {
            return;
        }
        turn.start(drawn);
    }

    /** 两点已存入的 cognitio 加上这次转动的电力，否则这次转动根本不开始。 */
    private boolean payForTurn() {
        if (!cognitio.ready() || !canPayEnergy()) {
            return false;
        }
        if (!drawEnergy(GachaOdds.AE_PER_TURN)) {
            return false;
        }
        return cognitio.spend();
    }

    /** 把缓冲区还有空间容纳的部分直接从网格拉出来，用的就是充电器自己的方式。 */
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

    /** 当现在能支付一次转动时为真，把缓冲区和它背后的网络都算进去。 */
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

    /** 从缓冲区里取走这次转动的电力，缓冲区缺的部分由网格补上。 */
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
        // 这次转动没有发生，所以从缓冲区里出来的电力又放回缓冲区。
        injectExternalPower(PowerUnit.AE, banked, Actionable.MODULATE);
        return false;
    }

    /** 分发这次转动抽到的东西，然后把结果留在屏幕上片刻。 */
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

    /** 屏幕是一个方块状态，所以方块自己保存改动并把它发给客户端。 */
    private void showScreen(BlockGachaBox.Screen screen) {
        BlockState state = getBlockState();
        if (state.getValue(BlockGachaBox.SCREEN) != screen) {
            level.setBlockAndUpdate(worldPosition, state.setValue(BlockGachaBox.SCREEN, screen));
        }
    }

    /** 把箱子绑定到放入脑的那个人，并作废上一个所有者留下的转动。 */
    public void bind(Player player) {
        this.owner = player.getUUID();
        this.ownerName = player.getName().getString();
        turn.reset();
    }

    /** 解绑把转动丢掉而不是存起来：空的箱子不工作。 */
    public void unbind() {
        this.owner = null;
        this.ownerName = "";
        turn.reset();
    }

    /** 已绑定玩家的名字，或箱子没有脑时的 null。 */
    public @Nullable String ownerName() {
        return this.owner == null ? null : this.ownerName;
    }

    /** 一颗脑在箱子里但不属于任何人：这是存档中断留下的东西。 */
    public boolean hasUnboundBrain() {
        return hasJar() && this.owner == null;
    }

    /** 这个玩家是否可以取回脑：箱子绑定的那个人，或在箱子持有未认领的脑、
     * 因而还不属于任何人时任何人都可以。 */
    public boolean mayTakeBrain(Player player) {
        return this.owner == null || this.owner.equals(player.getUUID());
    }

    /** 倒出箱子持有的东西：脑从其槽位里出来，还有卡片。 */
    public void dropContents(boolean holdsBrain) {
        if (level == null) {
            return;
        }
        ItemStack held = takeBrain();
        if (held.isEmpty() && holdsBrain) {
            // 来自那种把脑只留在方块状态里的方案的存档仍然欠一颗。
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
        // 状态在槽位之前读取，而加载会清空槽位：只把脑留在方块状态里的存档
        // 会被转回物品，而不是在那里丢掉。
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
