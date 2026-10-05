package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.core.definitions.AEItems;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TCAspects;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
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
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.inventory.GearSlots;
import thaumicenergistics_ce.menu.MenuArcaneAssembler;
import thaumicenergistics_ce.util.ThELog;

/**
 * An AE2 crafting machine that runs Thaumaturge arcane recipes on demand, paying in ambient vis.
 * <ul>
 *   <li>Is its own {@link ICraftingProvider} and an {@link ICraftingMachine} a provider can drive.
 *   <li>Priced as the workbench does: base vis plus crystal vis, surcharged, less gear discounts.
 * </ul>
 */
public class BlockEntityArcaneAssembler extends ThEBaseBlockEntity
        implements IInWorldGridNodeHost, IActionHost, IGridTickable, ICraftingProvider, ICraftingMachine {

    // ---- Inventory layout -------------------------------------------------
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

    final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            if (slot >= UPGRADE_SLOT_START) {
                // The card band: the slots the menu's four card wells point at.
                return AEItems.SPEED_CARD.is(stack);
            }
            if (AssemblerDisplaySync.isDisplaySlot(slot)) {
                // The machine's own display: it takes nothing from a player.
                return false;
            }
            return switch (slot) {
                case CORE_SLOT -> stack.is(ModItems.KNOWLEDGE_CORE.get());
                default -> slot >= GEAR_SLOT_START && GearSlots.accepts(slot - GEAR_SLOT_START, stack);
            };
        }

        @Override
        public void setChanged() {
            super.setChanged();
            BlockEntityArcaneAssembler.this.onInventoryChanged();
        }
    };

    final IManagedGridNode mainNode;
    final IActionSource actionSource;
    private final IGridNodeListener<BlockEntityArcaneAssembler> nodeListener =
            new IGridNodeListener<BlockEntityArcaneAssembler>() {
                @Override
                public void onSaveChanges(BlockEntityArcaneAssembler owner, IGridNode node) {
                    owner.setChanged();
                }

                @Override
                public void onStateChanged(BlockEntityArcaneAssembler owner, IGridNode node, State state) {
                    if (state == State.POWER) {
                        owner.active = owner.mainNode.isActive();
                    }
                    owner.displaySync.markForUpdate();
                    // loadAdditional runs before the node exists; this wake has to cover a resumed craft.
                    owner.craftJob().updateSleepiness();
                }
            };

    private boolean active;

    // Built in the constructor, not here: a helper that reads this machine is built in order, and the
    // fields below it are the ones it reads.
    final AssemblerCraftState craft;
    final AssemblerDisplaySync displaySync;
    final AssemblerVisSource vis;
    final AssemblerUpgrades upgrades;

    private AssemblerCraftJob craftJob;

    AssemblerCraftJob craftJob() {
        if (craftJob == null) {
            craftJob = new AssemblerCraftJob(this);
        }
        return craftJob;
    }

    /**
     * The block's coordinates, for a helper in this package: the protected field behind it is out of a
     * helper's reach, so it asks through here. {@link #level()} does the same for the level.
     */
    BlockPos blockPos() {
        return worldPosition;
    }

    Level level() {
        return level;
    }

    boolean patternsDirty = true;
    boolean suppressNotify;

    List<IPatternDetails> cachedPatterns = List.of();

    public BlockEntityArcaneAssembler(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCANE_ASSEMBLER.get(), pos, state);

        this.displaySync = new AssemblerDisplaySync(this);
        this.craft = new AssemblerCraftState();
        this.vis = new AssemblerVisSource(this);
        this.upgrades = new AssemblerUpgrades(this);

        this.mainNode = GridHelper.createManagedNode(this, nodeListener)
                .setVisualRepresentation(ModItems.ARCANE_ASSEMBLER.get())
                .setInWorldNode(true)
                .setTagName("proxy")
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setIdlePowerUsage(0.0)
                .addService(IGridTickable.class, this)
                .addService(ICraftingProvider.class, this);
        this.actionSource = IActionSource.ofMachine(this);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            mainNode.create(level, getBlockPos());
            // loadAdditional ran before setLevel, so a restored craft had no world to match against.
            craftJob().recoverInterruptedCraft();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (mainNode != null) {
            mainNode.destroy();
        }
    }

    /** Drops what the player owns - the core, the gear and the cards - and nothing else: the mirror,
     * target and preview bands hold copies the machine made, so dropping them would hand out unpaid
     * items. */
    public void dropContents() {
        if (level == null || level.isClientSide()) {
            return;
        }
        // Give back ingredients the network has already paid for before the block goes.
        craftJob().returnHeldInputs();
        suppressNotify = true;
        try {
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                if (AssemblerDisplaySync.isMachineOwned(slot)) {
                    continue;
                }
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(
                            level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
                    inventory.setItem(slot, ItemStack.EMPTY);
                }
            }
        } finally {
            suppressNotify = false;
        }
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    public boolean isCrafting() {
        return craft.isCrafting();
    }

    public boolean isActive() {
        return active;
    }

    public int getBufferedVis() {
        return vis.bufferedVis();
    }

    public int getAspectVis(int index) {
        return vis.aspectVis(index);
    }

    public String aspectVisTrace() {
        return vis.aspectVisTrace();
    }

    public float getAuraAround() {
        return vis.auraAround();
    }

    public int getAuraCapacity() {
        return vis.auraCapacity();
    }

    /** The speed upgrades and the gear discount, read by the menu and the Jade provider. */
    public AssemblerUpgrades upgrades() {
        return upgrades;
    }

    public int getCraftTicks() {
        return craft.craftTicks();
    }

    public int getTicksPerCraft() {
        return upgrades.ticksPerCraft();
    }

    public float getCraftProgress() {
        int total = upgrades.ticksPerCraft();
        return craft.isCrafting() && total > 0 ? Math.min(1.0F, (float) craft.craftTicks() / total)
                : 0.0F;
    }

    public ItemStack previewStack() {
        return displaySync.previewStack();
    }

    /** The vis a craft of {@code pattern} is charged, after the gear discount. */
    public int craftCost(ThEArcanePattern pattern) {
        return craftJob().craftCost(pattern);
    }

    /** Whether {@code stack} belongs in a gear slot at all; shift-click routing uses this, while the
     * per-slot check additionally requires the right equipment type. */
    public static boolean isGearItem(ItemStack stack) {
        return GearSlots.isGear(stack);
    }

    // ------------------------------------------------------------------
    // AE2 grid plumbing
    // ------------------------------------------------------------------

    @Override
    public @Nullable IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    @Override
    public @Nullable IGridNode getActionableNode() {
        return mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        // Never start asleep: a core can be inserted while idle, and a sleeping node is never woken.
        return new TickingRequest(1, 20, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        if (level == null || level.isClientSide()) {
            return TickRateModulation.SLEEP;
        }
        if (patternsDirty) {
            // Settled only on a successful read, so a rebuild with no level yet retries. See rebuildPatterns.
            patternsDirty = !rebuildPatterns();
            ICraftingProvider.requestUpdate(mainNode);
        }
        if (!mainNode.isActive()) {
            return TickRateModulation.IDLE;
        }
        if (vis.bufferedVis() < vis.visTarget(craft.isCrafting(), craft.craftPrice())) {
            vis.replenishVis();
        }
        if (!craft.isCrafting()) {
            return TickRateModulation.IDLE;
        }
        IGrid grid = node.getGrid();
        if (grid == null) {
            return TickRateModulation.IDLE;
        }
        return craftJob().craftingTick(grid, ticksSinceLast);
    }

    // ------------------------------------------------------------------
    // Crafting
    // ------------------------------------------------------------------

    public @Nullable Component waitReason() {
        return craft.isCrafting() ? craft.lastWait() : null;
    }

    public @Nullable Component refusalReason() {
        return craft.lastRefusal();
    }

    // ------------------------------------------------------------------
    // ICraftingProvider
    // ------------------------------------------------------------------

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        if (patternsDirty) {
            // Only a rebuild that could read the core settles it: AE2 asks while loading, with no level yet.
            patternsDirty = !rebuildPatterns();
        }
        return cachedPatterns;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return craftJob().accept(patternDetails, inputHolder);
    }

    @Override
    public boolean isBusy() {
        return craft.isCrafting();
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        return new PatternContainerGroup(
                AEItemKey.of(ModItems.ARCANE_ASSEMBLER.get()),
                Component.translatable("block.thaumicenergistics_ce.arcane_assembler"),
                List.of());
    }

    @Override
    public boolean acceptsPlans() {
        return !craft.isCrafting();
    }

    // ------------------------------------------------------------------
    // ICraftingMachine, for a colocated AE2 pattern provider
    // ------------------------------------------------------------------

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, Direction ejectionDirection) {
        return craftJob().acceptFromMachine(patternDetails);
    }

    // ------------------------------------------------------------------
    // Pattern cache
    // ------------------------------------------------------------------

    /** Rebuilds the advertised set from the core, not the live recipe manager: a core needs registry
     * access, so clear {@code patternsDirty} only when this returns {@code true}.
     * @return {@code true} when the core was readable and the cache is complete */
    boolean rebuildPatterns() {
        return craftJob().rebuildPatterns();
    }

    private void onInventoryChanged() {
        if (suppressNotify) {
            return;
        }
        patternsDirty = true;
        // The cards sit in the machine's own slots now, so their count is read off the inventory.
        upgrades.refreshSpeedUpgrades();
        upgrades.recalculateGearDiscount();
        setChanged();
        if (level != null && !level.isClientSide() && mainNode.getGrid() != null) {
            patternsDirty = !rebuildPatterns();
            ICraftingProvider.requestUpdate(mainNode);
        }
        if (level != null && !level.isClientSide()) {
            displaySync.refreshPatternSlots();
        }
    }

    // ------------------------------------------------------------------
    // Persistence and sync
    // ------------------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
        craft.readNbt(tag, registries);
        upgrades.readNbt(tag);
        vis.readNbt(tag);
        suppressNotify = true;
        try {
            ContainerHelper.loadAllItems(tag, inventory.getItems(), registries);
        } finally {
            suppressNotify = false;
        }
        upgrades.recalculateGearDiscount();
        // After the items, not before: the count comes from the cards that just loaded, not from the
        // saved number a menu-local container used to write.
        upgrades.recountSpeedUpgrades();
        patternsDirty = true;
    }

    public static int visBufferTarget() {
        return AssemblerVisPool.IDLE_TARGET;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        upgrades.writeNbt(tag);
        vis.writeNbt(tag);
        // Saved with the craft, so finishing it after a reload needs nothing but this tag and the well.
        craft.writeNbt(tag, registries);
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
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
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            displaySync.applySyncedState(tag, registries);
        }
    }

    // ------------------------------------------------------------------
    // Menu
    // ------------------------------------------------------------------

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        displaySync.refreshPatternSlots();
        upgrades.recalculateGearDiscount();
        return new MenuArcaneAssembler(containerId, playerInventory, this);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlockEntityArcaneAssembler be) {
        // Intentionally empty: the AE2 grid tick is the machine's only clock.
    }
}
