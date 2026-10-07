package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.compat.thaumaturge.TcAspects;
import thaumicenergistics_ce.init.MachineMenus;
import thaumicenergistics_ce.init.ModBlockEntities;

/**
 * AE2 合成机器，按需跑 Thaumaturge 的奥术配方，用环境 vis 付费：自己是一个
 * {@link ICraftingProvider}，也是同网格上供应器能驱动的 {@link ICraftingMachine}；定价与工作台一致。
 * 基础 vis 加水晶 vis，加成，减装备折扣。
 */
public class BlockEntityArcaneAssembler extends ThEBaseBlockEntity
        implements IInWorldGridNodeHost, IActionHost, IGridTickable, ICraftingProvider, ICraftingMachine {

    public static final int CORE_SLOT = 0;
    public static final int PATTERN_SLOT_START = 1;
    public static final int PATTERN_SLOT_COUNT = 21;
    public static final int PATTERN_SLOT_END = PATTERN_SLOT_START + PATTERN_SLOT_COUNT - 1;
    public static final int TARGET_SLOT = PATTERN_SLOT_END + 1;
    public static final int GEAR_SLOT_START = TARGET_SLOT + 1;
    public static final int GEAR_SLOT_COUNT = 4;
    // 追加在装备之后，不插入：已保存的槽位索引会把旧装备挪进预览区。
    public static final int PREVIEW_SLOT_START = GEAR_SLOT_START + GEAR_SLOT_COUNT;
    public static final int PREVIEW_SLOT_COUNT = 9;
    // 追加在预览之后，理由与预览相同：已保存的槽位索引一挪，
    // 就会把卡读成预览井，或把井读成卡。
    public static final int UPGRADE_SLOT_START = PREVIEW_SLOT_START + PREVIEW_SLOT_COUNT;
    /** 加速卡槽，一槽一张卡：四个槽就是这台机器的整条速度阶梯。 */
    public static final int UPGRADE_SLOT_COUNT = 4;
    public static final int SLOT_COUNT = UPGRADE_SLOT_START + UPGRADE_SLOT_COUNT;

    /** 元质，按六根 vis 柱绘制的固定顺序。 */
    public static final List<ResourceKey<IAspect>> PRIMALS = TcAspects.PRIMALS;

    final SimpleContainer inventory = new AssemblerInventoryLayout(this::onInventoryChanged);
    private final AssemblerCraftParts craftParts = new AssemblerCraftParts(this);

    final IManagedGridNode mainNode;
    final IActionSource actionSource;
    boolean active;
    boolean suppressNotify;

    // 在构造器里构建，不在这里：读这台机器的辅助对象按字段顺序构建，它读的字段就在下面。
    final AssemblerCraftState craft;
    final AssemblerDisplaySync displaySync;
    final AssemblerVisSource vis;
    final AssemblerUpgrades upgrades;
    final AssemblerPatternCache patternCache;

    AssemblerCraftJob craftJob() { return craftParts.job(); }

    AssemblerCraftRunner craftRunner() { return craftParts.runner(); }

    public BlockEntityArcaneAssembler(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCANE_ASSEMBLER.get(), pos, state);

        this.displaySync = new AssemblerDisplaySync(this);
        this.craft = new AssemblerCraftState();
        this.vis = new AssemblerVisSource(this);
        this.upgrades = new AssemblerUpgrades(this);
        this.patternCache = new AssemblerPatternCache(this);

        this.mainNode = AssemblerGridNode.create(this);
        this.actionSource = IActionSource.ofMachine(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        AssemblerNodeListener.attach(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        AssemblerNodeListener.detach(this);
    }

    /** 只掉玩家自己的东西：核心、装备和卡，别的都不掉。镜像、目标和预览区是机器写进去的副本，
     * 掉出来等于白送没付过费的物品。 */
    public void dropContents() { AssemblerContents.drop(this); }

    /** 某个区是不是玩家自己放的。展示区放的是机器写的副本，故这也是唯一允许管道碰的区。 */
    public static boolean isPlayerOwned(int slot) {
        return !AssemblerDisplaySync.isMachineOwned(slot);
    }

    public SimpleContainer getInventory() { return inventory; }
    public boolean isCrafting() { return craft.isCrafting(); }
    public boolean isActive() { return active; }
    public int getBufferedVis() { return vis.bufferedVis(); }
    public int getAspectVis(int index) { return vis.aspectVis(index); }
    public String aspectVisTrace() { return vis.aspectVisTrace(); }
    public float getAuraAround() { return vis.auraAround(); }
    public int getAuraCapacity() { return vis.auraCapacity(); }
    public int getCraftTicks() { return craft.craftTicks(); }
    public int getTicksPerCraft() { return upgrades.ticksPerCraft(); }
    public ItemStack previewStack() { return displaySync.previewStack(); }
    public @Nullable Component waitReason() { return craft.isCrafting() ? craft.lastWait() : null; }
    public @Nullable Component refusalReason() { return craft.lastRefusal(); }

    /** 速度升级与装备折扣，菜单和 Jade 提供器读这里。 */
    public AssemblerUpgrades upgrades() { return upgrades; }

    public float getCraftProgress() {
        int total = upgrades.ticksPerCraft();
        return craft.isCrafting() && total > 0 ? Math.min(1.0F, (float) craft.craftTicks() / total)
                : 0.0F;
    }

    /** 一次 {@code pattern} 合成扣的 vis，已减法杖折扣。 */
    public int craftCost(ThEArcanePattern pattern) { return craftJob().craftCost(pattern); }

    @Override
    public @Nullable IGridNode getGridNode(Direction dir) { return mainNode.getNode(); }

    @Override
    public @Nullable IGridNode getActionableNode() { return mainNode.getNode(); }

    @Override
    public AECableType getCableConnectionType(Direction dir) { return AECableType.SMART; }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        // 绝不以睡眠状态启动：核心会在空闲时插入，而睡着的节点永远不会被唤醒。
        return new TickingRequest(1, 20, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        return AssemblerGridTick.advance(this, node, ticksSinceLast);
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        patternCache.refresh();
        return patternCache.patterns();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return craftJob().accept(patternDetails, inputHolder);
    }

    @Override
    public boolean isBusy() { return craft.isCrafting(); }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() { return AssemblerGridNode.machineInfo(); }

    @Override
    public boolean acceptsPlans() { return !craft.isCrafting(); }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, Direction ejectionDirection) {
        return craftJob().acceptFromMachine(patternDetails);
    }

    private void onInventoryChanged() {
        AssemblerInventoryWatcher.changed(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        AssemblerPersistence.load(this, tag, registries);
    }

    public static int visBufferTarget() { return AssemblerVisPool.IDLE_TARGET; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        AssemblerPersistence.save(this, tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        displaySync.writeSync(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        displaySync.applySyncedState(tag, registries);
    }

    /** 在客户端应用更新标签，每 tick 更新走这条路。数据包落到这里，
     * 默认实现以 {@code loadAdditional} 结尾，那会把合成状态抹掉。 */
    @Override
    public void onDataPacket(
            Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        AssemblerPersistence.applyPacket(this, packet, registries);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        displaySync.refreshPatternSlots();
        upgrades.recalculateGearDiscount();
        return MachineMenus.arcaneAssembler(containerId, playerInventory, this);
    }

    public static void serverTick(
            Level level, BlockPos pos, BlockState state, BlockEntityArcaneAssembler assembler) {
        // 有意留空：AE2 网格 tick 是这台机器唯一的时钟。
    }
}
