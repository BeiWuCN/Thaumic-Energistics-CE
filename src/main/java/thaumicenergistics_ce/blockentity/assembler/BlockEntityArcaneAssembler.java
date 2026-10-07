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
import com.leclowndu93150.thaumaturge.api.aspect.TCAspects;
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
import thaumicenergistics_ce.init.MachineMenus;
import thaumicenergistics_ce.init.ModBlockEntities;

/**
 * 一台 AE2 合成机器，按需运行 Thaumaturge 的奥术配方，以环境 vis 付费：它自己是一个
 * {@link ICraftingProvider}，也是一个 {@link ICraftingMachine}，同网格上的供应器可以驱动它，定价
 * 与工作台一致——基础 vis 加水晶 vis，再加成，减去装备折扣。
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
    // 追加在装备之后，从不插入：已保存的槽位索引会把旧装备挪进预览区。
    public static final int PREVIEW_SLOT_START = GEAR_SLOT_START + GEAR_SLOT_COUNT;
    public static final int PREVIEW_SLOT_COUNT = 9;
    // 追加在预览之后，理由与预览相同：已保存的槽位索引一旦移动，就会把
    // 一张卡读成一个预览槽，或把一个槽读成一张卡。
    public static final int UPGRADE_SLOT_START = PREVIEW_SLOT_START + PREVIEW_SLOT_COUNT;
    /** 加速卡槽位，每张卡一个：四个槽位就是这台机器的整条速度阶梯。 */
    public static final int UPGRADE_SLOT_COUNT = 4;
    public static final int SLOT_COUNT = UPGRADE_SLOT_START + UPGRADE_SLOT_COUNT;

    /** 元质，按六根 vis 柱绘制所用的固定顺序。 */
    public static final List<ResourceKey<IAspect>> PRIMALS = TCAspects.PRIMALS;

    final SimpleContainer inventory = new AssemblerInventoryLayout(this::onInventoryChanged);
    private final AssemblerCraftParts craftParts = new AssemblerCraftParts(this);

    final IManagedGridNode mainNode;
    final IActionSource actionSource;
    boolean active;
    boolean suppressNotify;

    // 在构造器里构建，不在这里：读这台机器的辅助对象要按顺序构建，而它读的
    // 字段就在它下面。
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

    /** 掉落玩家拥有的东西——核心、装备和卡——别的都不掉：镜像、
     * 目标和预览区放的是机器造出的副本，掉落它们等于白送未付费的物品。 */
    public void dropContents() { AssemblerContents.drop(this); }

    /** 某个区是否放着玩家自己放进去的东西。展示区放的是机器写入的
     * 副本，所以这也是管道可以够到的区。 */
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

    /** 速度升级与装备折扣，由菜单与 Jade 提供器读取。 */
    public AssemblerUpgrades upgrades() { return upgrades; }

    public float getCraftProgress() {
        int total = upgrades.ticksPerCraft();
        return craft.isCrafting() && total > 0 ? Math.min(1.0F, (float) craft.craftTicks() / total)
                : 0.0F;
    }

    /** 一次 {@code pattern} 合成所扣的 vis，已扣掉装备折扣。 */
    public int craftCost(ThEArcanePattern pattern) { return craftJob().craftCost(pattern); }

    @Override
    public @Nullable IGridNode getGridNode(Direction dir) { return mainNode.getNode(); }

    @Override
    public @Nullable IGridNode getActionableNode() { return mainNode.getNode(); }

    @Override
    public AECableType getCableConnectionType(Direction dir) { return AECableType.SMART; }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        // 绝不从睡眠开始：核心可能在空闲时被插入，而睡眠的节点永远不会被唤醒。
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

    /** 在客户端应用更新标签，这是每 tick 更新的路径。数据包落在这里，它的
     * 默认实现以 {@code loadAdditional} 结尾，而那个会把合成状态抹掉。 */
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
