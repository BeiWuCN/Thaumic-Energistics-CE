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
 * An AE2 crafting machine that runs Thaumaturge arcane recipes on demand, paying in ambient vis: its own
 * {@link ICraftingProvider} and an {@link ICraftingMachine} a provider on the same grid can drive, priced
 * as the workbench is - base vis plus crystal vis, surcharged, less the gear discount.
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
    // Appended after the gear, never inserted: saved slot indices would move old gear into the preview band.
    public static final int PREVIEW_SLOT_START = GEAR_SLOT_START + GEAR_SLOT_COUNT;
    public static final int PREVIEW_SLOT_COUNT = 9;
    // Appended after the preview, for the preview's own reason: a saved slot index that moved would read
    // a card as a preview well, or a well as a card.
    public static final int UPGRADE_SLOT_START = PREVIEW_SLOT_START + PREVIEW_SLOT_COUNT;
    /** The acceleration-card slots, one card each: four of them are the machine's whole speed ladder. */
    public static final int UPGRADE_SLOT_COUNT = 4;
    public static final int SLOT_COUNT = UPGRADE_SLOT_START + UPGRADE_SLOT_COUNT;

    /** The primal aspects, in the fixed order the six vis columns are drawn in. */
    public static final List<ResourceKey<IAspect>> PRIMALS = TCAspects.PRIMALS;

    final SimpleContainer inventory = new AssemblerInventoryLayout(this::onInventoryChanged);
    private final AssemblerCraftParts craftParts = new AssemblerCraftParts(this);

    final IManagedGridNode mainNode;
    final IActionSource actionSource;
    boolean active;
    boolean suppressNotify;

    // Built in the constructor, not here: a helper that reads this machine is built in order, and the
    // fields below it are the ones it reads.
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

    /** Drops what the player owns - the core, the gear and the cards - and nothing else: the mirror,
     * target and preview bands hold copies the machine made, so dropping them hands out unpaid items. */
    public void dropContents() { AssemblerContents.drop(this); }

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

    /** The speed upgrades and the gear discount, read by the menu and the Jade provider. */
    public AssemblerUpgrades upgrades() { return upgrades; }

    public float getCraftProgress() {
        int total = upgrades.ticksPerCraft();
        return craft.isCrafting() && total > 0 ? Math.min(1.0F, (float) craft.craftTicks() / total)
                : 0.0F;
    }

    /** The vis a craft of {@code pattern} is charged, after the gear discount. */
    public int craftCost(ThEArcanePattern pattern) { return craftJob().craftCost(pattern); }

    @Override
    public @Nullable IGridNode getGridNode(Direction dir) { return mainNode.getNode(); }

    @Override
    public @Nullable IGridNode getActionableNode() { return mainNode.getNode(); }

    @Override
    public AECableType getCableConnectionType(Direction dir) { return AECableType.SMART; }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        // Never start asleep: a core can be inserted while idle, and a sleeping node is never woken.
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

    /** Applies an update tag on the client, the route a per-tick update takes. A packet lands here, its
     * default implementation ending in {@code loadAdditional}, which wiped the craft state. */
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
        // Intentionally empty: the AE2 grid tick is the machine's only clock.
    }
}
