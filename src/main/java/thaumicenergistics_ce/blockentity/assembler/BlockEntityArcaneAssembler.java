package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
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
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.core.definitions.AEItems;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TCAspects;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ArcanePatternDetails;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.inventory.GearSlots;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;
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

    // ---- Tuning -----------------------------------------------------------
    private static final double ACTIVE_POWER = 1.5;
    private static final float MIN_CONSUMPTION_MODIFIER = 0.1F;
    private static final int STALLED_CRAFT_REPORT_TICKS = 100;

    /** Ticks of unbroken stalling after which the craft is finished anyway: a minute. AE2 has no
     * cancellation callback on a provider, and a craft waiting for ever keeps the machine busy. */
    private static final int STALL_RELEASE_TICKS = 1200;

    /** The primal aspects, in the fixed order the six vis columns are drawn in. */
    public static final List<ResourceKey<IAspect>> PRIMALS = TCAspects.PRIMALS;

    final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            if (slot >= UPGRADE_SLOT_START) {
                // The card band: the slots the menu's four card wells point at.
                return AEItems.SPEED_CARD.is(stack);
            }
            return switch (slot) {
                case CORE_SLOT -> stack.is(ModItems.KNOWLEDGE_CORE.get());
                // Both are the machine's own display and take nothing from a player.
                case TARGET_SLOT -> false;
                default -> slot >= PREVIEW_SLOT_START
                        ? false
                        : slot >= GEAR_SLOT_START && GearSlots.accepts(slot - GEAR_SLOT_START, stack);
            };
        }

        @Override
        public void setChanged() {
            super.setChanged();
            BlockEntityArcaneAssembler.this.onInventoryChanged();
        }
    };

    private final IManagedGridNode mainNode;
    private final IActionSource actionSource;
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
                    owner.updateSleepiness();
                }
            };

    private boolean active;

    final AssemblerCraftState craft = new AssemblerCraftState();

    final AssemblerDisplaySync displaySync = new AssemblerDisplaySync(this);

    final AssemblerVisSource vis = new AssemblerVisSource(this);
    final AssemblerUpgrades upgrades = new AssemblerUpgrades(this);

    /**
     * The two {@link BlockEntity} members a same-package sibling cannot reach: {@code worldPosition} and
     * {@code level} are protected, so the helper asks rather than reads.
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
            recoverInterruptedCraft();
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
        returnHeldInputs();
        suppressNotify = true;
        try {
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                boolean display = slot >= PATTERN_SLOT_START && slot < GEAR_SLOT_START
                        || slot >= PREVIEW_SLOT_START && slot < UPGRADE_SLOT_START;
                if (display) {
                    // The machine's own display; none of it was ever the player's.
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

    public void forceCraftForTest(boolean crafting, int craftTicks) {
        craft.setCrafting(crafting);
        craft.setCraftTicks(craftTicks);
        displaySync.markForUpdate();
    }

    /** Holds a real recipe as if pushed, and reports what the machine would bank for it. */
    public void forcePatternForTest(ThEArcanePattern pattern) {
        craft.setCurrentPattern(pattern);
        craft.setCrafting(true);
        craft.setCraftPrice(craftCost(pattern));
        craft.setCraftCrystals(crystalStacksOf(pattern));
        // Exactly as beginCraft puts it in; the round-trip check is worthless without it.
        this.inventory.setItem(TARGET_SLOT, pattern.result().copy());
        ThELog.LOG.info(
                "[asmtest] vis target for {} ({} vis) is {}, with {} in the buffer",
                pattern.result(),
                craftCost(pattern),
                vis.visTarget(craft.isCrafting(), craft.craftPrice()),
                vis.bufferedVis());
    }

    public String resumeReportForTest() {
        return "crafting=" + craft.isCrafting() + " price=" + craft.craftPrice() + " crystals="
                + craft.craftCrystals().size() + " output=" + inventory.getItem(TARGET_SLOT);
    }

    public void recoverForTest(Level level) {
        setLevel(level);
        recoverInterruptedCraft();
    }

    public void setItemForTest(int slot, ItemStack stack) {
        suppressNotify = true;
        try {
            inventory.setItem(slot, stack);
        } finally {
            suppressNotify = false;
        }
    }

    /** Runs the container listener by hand: {@link #setItemForTest} suppresses it, and a card put into the
     * machine has to move the count before anything is ever saved. */
    public void onInventoryChangedForTest() {
        onInventoryChanged();
    }

    public static int coreSlotForTest() {
        return CORE_SLOT;
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
        return craftingTick(grid, ticksSinceLast);
    }

    // ------------------------------------------------------------------
    // Crafting
    // ------------------------------------------------------------------

    private TickRateModulation craftingTick(IGrid grid, int ticksSinceLast) {
        // No test for a missing pattern: a craft is defined by what it produces and what it owes.
        if (craft.craftTicks() >= upgrades.ticksPerCraft()) {
            return completeCraft(grid);
        }

        IEnergyService energy = grid.getService(IEnergyService.class);
        if (energy != null) {
            double needed = ACTIVE_POWER * ticksSinceLast;
            double extracted = energy.extractAEPower(needed, Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (extracted < needed * 0.9) {
                noteStall(AssemblerStatus.wait(AssemblerStatus.WAIT_NO_POWER, "no power"));
                return TickRateModulation.SAME;
            }
        }
        craft.clearStall();
        craft.addCraftTicks(ticksSinceLast);
        // URGENT, not SAME: at the idle rate a busy craft runs twenty times too slow.
        displaySync.markDisplayForUpdate();
        return TickRateModulation.URGENT;
    }

    /** Reports once that a craft is waiting, then keeps waiting: AE2 already extracted the ingredients.
     * @return always {@code false}: a craft is never abandoned for waiting */
    private boolean noteStall(Component reason) {
        craft.noteStall(reason);
        if (craft.stalledTicks() == STALLED_CRAFT_REPORT_TICKS) {
            ThELog.LOG.info(
                    "[assembler] at {} a craft is waiting for {} ({} ticks so far); it will finish when it"
                            + " can",
                    worldPosition,
                    reason.getString(),
                    craft.stalledTicks());
        }
        return false;
    }

    public @Nullable Component waitReason() {
        return craft.isCrafting() ? craft.lastWait() : null;
    }

    public @Nullable Component refusalReason() {
        return craft.lastRefusal();
    }

    private TickRateModulation completeCraft(IGrid grid) {
        int price = craft.craftPrice();
        // Waiting for this price is forever unless a relay or interface reaches vis the aura cannot hold.
        boolean unpayableForever = price > 0
                && vis.auraCapacity() > 0
                && price > vis.auraCapacity()
                && !vis.relayNetworkInReach()
                && !vis.interfaceInReach();
        // A relay that exists but never pays is not a promise either - see STALL_RELEASE_TICKS.
        boolean stalledOut = !unpayableForever && craft.stalledTicks() >= STALL_RELEASE_TICKS;
        if (vis.bufferedVis() < price && !unpayableForever && !stalledOut) {
            // Waiting on vis; the tick handler keeps refilling the buffer.
            noteStall(AssemblerStatus.wait(AssemblerStatus.WAIT_NO_VIS, "no vis (%s banked of %s needed, target %s)",
                    vis.bufferedVis(), price, vis.visTarget(craft.isCrafting(), craft.craftPrice())));
            return TickRateModulation.SAME;
        }
        if (vis.bufferedVis() < price && stalledOut) {
            ThELog.LOG.warn(
                    "[assembler] at {} delivers {} after {} ticks of waiting for {} vis: a machine that waits"
                            + " for ever refuses every later job",
                    worldPosition, inventory.getItem(TARGET_SLOT), craft.stalledTicks(), price);
        }
        if (vis.bufferedVis() < price) {
            // Delivered anyway, the lesser evil: AE2 already took the ingredients and waits with no timeout.
            ThELog.LOG.info(
                    "[assembler] at {} delivers {} without charging its {} vis: this chunk's aura can never hold"
                            + " more than {}",
                    worldPosition,
                    inventory.getItem(TARGET_SLOT),
                    price,
                    vis.auraCapacity());
        }

        IStorageService storage = grid.getService(IStorageService.class);
        if (storage == null) {
            return TickRateModulation.IDLE;
        }

        // The product comes from the well: the recipe that made it may no longer be readable.
        ItemStack output = inventory.getItem(TARGET_SLOT).copy();
        AEItemKey outputKey = AEItemKey.of(output);
        if (outputKey == null) {
            finishCraft();
            return TickRateModulation.IDLE;
        }

        // Crystals vis cannot stand in for; checked before the result is inserted, never after.
        if (!hasCrystals(storage)) {
            noteStall(AssemblerStatus.wait(AssemblerStatus.WAIT_NO_CRYSTALS, "no crystals"));
            return TickRateModulation.SAME;
        }

        long insertable =
                storage.getInventory().insert(outputKey, output.getCount(), Actionable.SIMULATE, actionSource);
        if (insertable < output.getCount()) {
            noteStall(AssemblerStatus.wait(AssemblerStatus.WAIT_NO_ROOM, "no room for %s", output.getHoverName()));
            return TickRateModulation.SAME;
        }

        // Re-check after the simulate: the extraction below is the point of no return for the crystals.
        if (!hasCrystals(storage)) {
            noteStall(AssemblerStatus.wait(AssemblerStatus.WAIT_NO_CRYSTALS_RECHECK, "no crystals (recheck)"));
            return TickRateModulation.SAME;
        }
        takeCrystals(storage);
        storage.getInventory().insert(outputKey, output.getCount(), Actionable.MODULATE, actionSource);
        // What the craft still owed, and no more.
        vis.spendVis(price);
        finishCraft();
        return TickRateModulation.URGENT;
    }

    private boolean hasCrystals(IStorageService storage) {
        for (ItemStack stack : craft.craftCrystals()) {
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) {
                return false;
            }
            long available = storage.getInventory()
                    .extract(key, stack.getCount(), Actionable.SIMULATE, actionSource);
            if (available < stack.getCount()) {
                return false;
            }
        }
        return true;
    }

    private void takeCrystals(IStorageService storage) {
        for (ItemStack stack : craft.craftCrystals()) {
            AEItemKey key = AEItemKey.of(stack);
            if (key != null) {
                storage.getInventory()
                        .extract(key, stack.getCount(), Actionable.MODULATE, actionSource);
            }
        }
    }

    /** The crystals a pattern's craft must be handed, as items the network will be asked for. */
    private static List<ItemStack> crystalStacksOf(ThEArcanePattern pattern) {
        List<ItemStack> stacks = new ArrayList<>();
        for (AspectInstance crystal : pattern.crystalItems().entries()) {
            ItemStack stack = TcRegistry.crystalFor(crystal.aspect(), crystal.amount());
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return List.copyOf(stacks);
    }

    /** The vis charged for {@code pattern}, after the gear discount and floored by Thaumaturge's own
     * {@code MIN_CONSUMPTION_MODIFIER}, so an equipped assembler still pays something. */
    public int craftCost(ThEArcanePattern pattern) {
        float modifier = Math.max(1.0F - upgrades.gearDiscount() / 100.0F, MIN_CONSUMPTION_MODIFIER);
        return Math.max(1, (int) Math.ceil(pattern.chargedVis() * modifier));
    }

    private void returnHeldInputs() {
        if (craft.heldInputs().isEmpty()) {
            return;
        }
        IStorageService storage = null;
        IGrid grid = gridOrNull();
        if (grid != null) {
            storage = grid.getService(IStorageService.class);
        }
        for (ItemStack stack : craft.heldInputs()) {
            if (stack.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(stack);
            long left = stack.getCount();
            if (storage != null && key != null) {
                long inserted = storage.getInventory()
                        .insert(key, stack.getCount(), Actionable.MODULATE, actionSource);
                left -= inserted;
            }
            if (left > 0) {
                Containers.dropItemStack(
                        level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                        stack.copyWithCount((int) left));
            }
        }
        craft.heldInputs().clear();
    }

    private appeng.api.networking.@Nullable IGrid gridOrNull() {
        return mainNode == null ? null : mainNode.getGrid();
    }

    private void finishCraft() {
        craft.reset();
        displaySync.clearDisplay(false);
        setChanged();
        displaySync.markForUpdate();
        // Nothing left to run, so the grid may stop ticking this machine.
        updateSleepiness();
    }

    /** Whether the aura can pay for {@code pattern}. An unpayable job is refused, not held: the CPU
     * skips a busy provider, so holding it stalls the plan. A low aura waits - its base can rise. */
    private boolean canEverPay(ThEArcanePattern pattern) {
        int capacity = vis.auraCapacity();
        // Zero means the chunk is not initialised yet; a relay counts too, its vis living in a node.
        return capacity <= 0 || vis.relayNetworkInReach() || vis.interfaceInReach() || craftCost(pattern) <= capacity;
    }

    private boolean awakeForCraft;

    /** Wakes the grid's tick while a craft is held, and lets it sleep when not: a craft restored from a
     * save never runs otherwise, as AE2 ticks idle devices at the idle rate. */
    private void updateSleepiness() {
        if (level == null || level.isClientSide() || awakeForCraft == craft.isCrafting()) {
            return;
        }
        IGrid grid = gridOrNull();
        IGridNode node = mainNode.getNode();
        if (grid == null || node == null) {
            return;
        }
        awakeForCraft = craft.isCrafting();
        if (craft.isCrafting()) {
            grid.getTickManager().wakeDevice(node);
        } else {
            grid.getTickManager().sleepDevice(node);
        }
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
        if (!acceptsPlans() || !mainNode.isActive()) {
            noteRefusal(acceptsPlans()
                    ? AssemblerStatus.refuse(AssemblerStatus.REFUSE_NODE_INACTIVE, "its grid node is not active")
                    : AssemblerStatus.refuse(AssemblerStatus.REFUSE_BUSY, "it is already holding a craft"));
            return false;
        }
        if (!(patternDetails instanceof ArcanePatternDetails details)) {
            noteRefusal(AssemblerStatus.refuse(AssemblerStatus.REFUSE_NOT_ARCANE, "the pattern is not an arcane pattern this machine can read"));
            return false;
        }
        if (!canEverPay(details.pattern())) {
            noteRefusal(cannotPay(craftCost(details.pattern())));
            return false;
        }
        // What AE2 just extracted. This machine pays in vis and crystals, so keep these only to give back.
        craft.heldInputs().clear();
        for (KeyCounter counter : inputHolder) {
            for (var entry : counter) {
                if (entry.getKey() instanceof AEItemKey itemKey && entry.getLongValue() > 0) {
                    craft.heldInputs()
                            .add(itemKey.toStack((int) Math.min(Integer.MAX_VALUE, entry.getLongValue())));
                }
            }
        }
        return beginCraft(details.pattern());
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
        if (!acceptsPlans() || !mainNode.isActive()) {
            noteRefusal(acceptsPlans()
                    ? AssemblerStatus.refuse(AssemblerStatus.REFUSE_NODE_INACTIVE, "its grid node is not active")
                    : AssemblerStatus.refuse(AssemblerStatus.REFUSE_BUSY, "it is already holding a craft"));
            return false;
        }
        if (patternDetails instanceof ArcanePatternDetails details) {
            if (!canEverPay(details.pattern())) {
                noteRefusal(cannotPay(craftCost(details.pattern())));
                return false;
            }
            return beginCraft(details.pattern());
        }
        ThEArcanePattern resolved = resolveExternal(patternDetails);
        if (resolved == null) {
            noteRefusal(AssemblerStatus.refuse(AssemblerStatus.REFUSE_UNRESOLVED, "the pattern does not resolve to an arcane recipe"));
            return false;
        }
        if (!canEverPay(resolved)) {
            noteRefusal(cannotPay(craftCost(resolved)));
            return false;
        }
        return beginCraft(resolved);
    }

    private void noteRefusal(Component why) {
        if (why.equals(craft.lastRefusal())) {
            return;
        }
        craft.setLastRefusal(why);
        // getString() resolves against the server's language; every key carries an English fallback.
        ThELog.LOG.info("[assembler] at {} turned a job away: {}", worldPosition, why.getString());
    }

    private Component cannotPay(int price) {
        return AssemblerStatus.tooExpensive(price, vis.auraCapacity());
    }

    private @Nullable ThEArcanePattern resolveExternal(IPatternDetails details) {
        if (level == null) {
            return null;
        }
        List<GenericStack> outputs = details.getOutputs();
        if (outputs.size() != 1 || !(outputs.getFirst().what() instanceof AEItemKey outputKey)) {
            return null;
        }
        List<ItemStack> inputs = new ArrayList<>();
        for (IPatternDetails.IInput input : details.getInputs()) {
            GenericStack[] possible = input.getPossibleInputs();
            if (possible.length == 0 || !(possible[0].what() instanceof AEItemKey itemKey)) {
                return null;
            }
            inputs.add(itemKey.toStack((int) Math.min(Integer.MAX_VALUE, possible[0].amount())));
        }
        return ThEArcanePattern.fromEncoded(level, inputs, outputKey.getReadOnlyStack());
    }

    private @Nullable ThEArcanePattern patternForResult(ItemStack result) {
        if (result.isEmpty()) {
            return null;
        }
        if (patternsDirty) {
            // Rebuild without spending the dirty flag: no level yields an empty list, making it final.
            rebuildPatterns();
        }
        for (IPatternDetails details : cachedPatterns) {
            if (details instanceof ArcanePatternDetails arcane
                    && ItemStack.isSameItemSameComponents(arcane.pattern().result(), result)) {
                return arcane.pattern();
            }
        }
        return null;
    }

    private boolean beginCraft(ThEArcanePattern pattern) {
        // Fixed now, not recomputed at completion, so a craft survives a save without the core: the
        // price and the crystals are handed over with the pattern.
        craft.begin(pattern, craftCost(pattern), crystalStacksOf(pattern));
        suppressNotify = true;
        try {
            inventory.setItem(TARGET_SLOT, pattern.result().copy());
            // The grid the GUI draws: written here, since the running craft exists only on the server.
            for (int i = 0; i < PREVIEW_SLOT_COUNT; i++) {
                ItemStack cell = i < pattern.grid().size() ? pattern.grid().get(i) : ItemStack.EMPTY;
                inventory.setItem(PREVIEW_SLOT_START + i, cell.isEmpty() ? ItemStack.EMPTY : cell.copy());
            }
        } finally {
            suppressNotify = false;
        }
        setChanged();
        displaySync.markForUpdate();
        // Wake the grid: measured, a craft pushed while asleep ticked once a second rather than twenty.
        ICraftingProvider.requestUpdate(mainNode);
        updateSleepiness();
        return true;
    }

    // ------------------------------------------------------------------
    // Pattern cache
    // ------------------------------------------------------------------

    /** Rebuilds the advertised set from the core, not the live recipe manager: a core needs registry
     * access, so clear {@code patternsDirty} only when this returns {@code true}.
     * @return {@code true} when the core was readable and the cache is complete */
    boolean rebuildPatterns() {
        cachedPatterns = List.of();
        HandlerKnowledgeCore core = knowledgeCore();
        if (core == null) {
            // No core, or - the case that matters - no level to read one with; report failure until it is.
            return level != null;
        }
        List<IPatternDetails> details = new ArrayList<>();
        List<ThEArcanePattern> stored = core.patterns();
        for (ThEArcanePattern pattern : stored) {
            ArcanePatternDetails detail =
                    ArcanePatternDetails.of(pattern, level.registryAccess(), why -> ThELog.LOG.warn(
                            "[assembler] at {} is not offering the stored pattern for {}: {}",
                            getBlockPos(),
                            pattern.result(),
                            why));
            if (detail != null) {
                details.add(detail);
            }
        }
        if (details.size() < stored.size()) {
            // Otherwise invisible: the machine just offers fewer recipes than the core holds.
            ThELog.LOG.warn(
                    "[assembler] at {} offers {} of the {} patterns in its knowledge core",
                    getBlockPos(),
                    details.size(),
                    stored.size());
        }
        if (core.unreadableCount() > 0) {
            // Entries this build cannot read: kept in the item, not offered; the core would read as empty.
            ThELog.LOG.warn(
                    "[assembler] at {} cannot read {} entr(ies) in its knowledge core; they are kept in the"
                            + " item and {} pattern(s) are offered",
                    getBlockPos(),
                    core.unreadableCount(),
                    details.size());
        }
        cachedPatterns = List.copyOf(details);
        return true;
    }

    private @Nullable HandlerKnowledgeCore knowledgeCore() {
        if (level == null) {
            return null;
        }
        return HandlerKnowledgeCore.of(inventory.getItem(CORE_SLOT), level.registryAccess());
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

    /** Finishes recovering a craft that a save interrupted, once there is a level to read the core with:
     * not in {@code loadAdditional}, which runs before the block entity has a level. */
    private void recoverInterruptedCraft() {
        if (level == null || level.isClientSide()) {
            return;
        }
        // Driven by the well: only finishCraft empties it, so a product there means a craft did not finish.
        ItemStack waiting = inventory.getItem(TARGET_SLOT);
        if (!waiting.isEmpty()) {
            craft.setCrafting(true);
            // Recovered only for the preview grid: the price and crystals were saved with the craft.
            ThEArcanePattern recovered = patternForResult(waiting);
            craft.setCurrentPattern(recovered);
            if (recovered != null) {
                // A readable pattern restates both numbers; the saved ones are the fallback.
                craft.setCraftPrice(craftCost(recovered));
                craft.setCraftCrystals(crystalStacksOf(recovered));
            }
            ThELog.LOG.info(
                    "[assembler] at {} resumed the craft a save interrupted: {} for {} vis{}",
                    worldPosition,
                    waiting.getHoverName().getString(),
                    craft.craftPrice(),
                    recovered == null ? " (the knowledge core no longer has its pattern)" : "");
            // Deliver on the first tick: the crafting time was served before the save.
            craft.setCraftTicks(upgrades.ticksPerCraft());
            craft.clearStall();
        } else {
            craft.setCrafting(false);
            craft.setCraftTicks(0);
            craft.setCraftPrice(0);
            craft.setCraftCrystals(List.of());
            displaySync.clearDisplay(true);
        }
        // Ask to be ticked rather than assuming a later grid event: with no grid yet this is a no-op.
        updateSleepiness();
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
