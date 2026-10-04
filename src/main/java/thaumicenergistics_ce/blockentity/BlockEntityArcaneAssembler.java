package thaumicenergistics_ce.blockentity;

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
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.util.Platform;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TCAspects;
import com.leclowndu93150.thaumaturge.api.items.IVisDiscountGear;
import com.leclowndu93150.thaumaturge.content.aura.relay.BlockEntityVisRelay;
import com.leclowndu93150.thaumaturge.content.aura.relay.VisRelayNetwork;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.inventory.GearSlots;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;
import thaumicenergistics_ce.menu.MenuArcaneAssembler;
import thaumicenergistics_ce.part.PartVisInterface;
import thaumicenergistics_ce.part.VisReservation;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;

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
    /** Three rows of seven, matching the measured GUI layout. */
    public static final int PATTERN_SLOT_COUNT = 21;
    public static final int PATTERN_SLOT_END = PATTERN_SLOT_START + PATTERN_SLOT_COUNT - 1;
    /** Output preview of the craft currently running. Read-only. */
    public static final int TARGET_SLOT = PATTERN_SLOT_END + 1;
    /** Worn gear whose vis discount applies to this assembler's crafts. */
    public static final int GEAR_SLOT_START = TARGET_SLOT + 1;
    public static final int GEAR_SLOT_COUNT = 4;
    /** Display-only mirror of the running craft's 3x3 ingredients; only the server can derive it. */
    // Appended after the gear, never inserted: saved slot indices would move old gear into the preview band.
    public static final int PREVIEW_SLOT_START = GEAR_SLOT_START + GEAR_SLOT_COUNT;
    public static final int PREVIEW_SLOT_COUNT = 9;
    public static final int UPGRADE_SLOT_COUNT = 4;
    public static final int SLOT_COUNT = PREVIEW_SLOT_START + PREVIEW_SLOT_COUNT;

    // ---- Tuning -----------------------------------------------------------
    private static final int BASE_TICKS_PER_CRAFT = 20;
    private static final int TICKS_PER_SPEED_UPGRADE = 4;
    private static final int MIN_TICKS_PER_CRAFT = 4;

    /** Ticks between display updates while a craft runs. See {@link #markDisplayForUpdate}. */
    private static final int DISPLAY_UPDATE_INTERVAL = 4;
    private static final int MAX_SPEED_UPGRADES = 4;

    /** Vis reach in chunks, 3x3: one chunk is never enough, Thaumaturge caps an aura's base at 500 vis
     * while the priciest recipe costs 1728. */
    private static final int VIS_SOURCE_RADIUS = 1;

    /** Centivis in one vis: the relay network answers in hundredths of a vis, the aura in whole vis. */
    private static final int CENTIVIS_PER_VIS = 100;

    /** Ticks between relay-network polls: each lookup rescans blocks and walks the relay chain. */
    private static final int RELAY_POLL_INTERVAL = 20;

    private static final double ACTIVE_POWER = 1.5;
    /** Vanilla minimum consumption modifier; a craft can never be free. */
    private static final float MIN_CONSUMPTION_MODIFIER = 0.1F;
    /** How long a craft may sit unable to pay before the stall is logged: five seconds. */
    private static final int STALLED_CRAFT_REPORT_TICKS = 100;

    /** Ticks of unbroken stalling after which the craft is finished anyway: a minute. AE2 has no
     * cancellation callback on a provider, and a craft waiting for ever keeps the machine busy. */
    private static final int STALL_RELEASE_TICKS = 1200;

    /** The primal aspects, in the fixed order the six vis columns are drawn in. */
    public static final List<ResourceKey<IAspect>> PRIMALS = TCAspects.PRIMALS;

    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
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
                    // Trace-only: a grid coming up changes state repeatedly.
                    if (STATE_TRACE) {
                        ThaumicEnergistics.LOG.info(
                                "[assembler] at {}: grid {} changed, now active={} powered={} booted={}{}",
                                owner.worldPosition,
                                state,
                                owner.mainNode.isActive(),
                                owner.mainNode.isPowered(),
                                owner.mainNode.hasGridBooted(),
                                owner.craft.isCrafting() ? ", and it is holding a craft" : "");
                    }
                    owner.markForUpdate();
                    // loadAdditional runs before the node exists; this wake has to cover a resumed craft.
                    owner.updateSleepiness();
                }
            };

    private boolean active;

    /** The running craft: what it makes, what it still owes, and why it is waiting. */
    private final AssemblerCraftState craft = new AssemblerCraftState();

    /** Game time of the last display update, which is what throttles a running craft's packets. */
    private long lastDisplayUpdate;

    /** Renderer-only copy of the running craft's product, written from the update tag: the real one is
     * in {@link #TARGET_SLOT}. */
    private ItemStack previewStack = ItemStack.EMPTY;

    /** Logs a loaded machine's whole state; its own switch, since the self-test's would clobber one. */
    private static final boolean STATE_TRACE =
            "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_ASSEMBLER_STATE"));

    /** State dumps still to make from the tick, and ticks since the last one. */
    private int stateDumpsLeft = 5;
    private int stateDumpTicks;

    /** Logs everything that decides whether this machine can do what a crafting CPU is waiting for. */
    private void dumpState(String when) {
        List<IPatternDetails> offered = getAvailablePatterns();
        StringBuilder products = new StringBuilder();
        for (int i = 0; i < Math.min(4, offered.size()); i++) {
            if (offered.get(i) instanceof ArcanePatternDetails arcane) {
                products.append(BuiltInRegistries.ITEM.getKey(arcane.pattern().result().getItem()))
                        .append(' ');
            }
        }
        ThaumicEnergistics.LOG.info(
                "[asmstate] {} at {} crafting={} tick={}/{} vis={} want={} crystals={} pattern={} active={}"
                        + " powered={} booted={} onGrid={} held={} offers={}{} aura={} auraBase={}"
                        + " relay={} relayCarry={} aspects={}",
                when,
                worldPosition,
                craft.isCrafting(),
                craft.craftTicks(),
                ticksPerCraft(),
                visPool.bufferedVis(),
                visPool.visTarget(craft.isCrafting(), craft.craftPrice()),
                craft.craftCrystals().size(),
                craft.currentPattern() == null ? "none" : craft.currentPattern().result(),
                mainNode.isActive(),
                mainNode.isPowered(),
                mainNode.hasGridBooted(),
                mainNode.getGrid() != null,
                craft.heldInputs().size(),
                offered.size(),
                offered.isEmpty() ? "" : " [" + products.toString().trim()
                        + (offered.size() > 4 ? " ..." : "") + "]",
                // The pool the craft is actually paid out of: see auraAround.
                auraAround(),
                auraCapacity(),
                relayNetworkInReach(),
                relayCarryTotal(),
                visPool.aspectVisTrace());
        IGrid grid = gridOrNull();
        ICraftingService craftingService = grid == null ? null : grid.getService(ICraftingService.class);
        if (craftingService != null) {
            for (ICraftingCPU cpu : craftingService.getCpus()) {
                CraftingJobStatus status = cpu.getJobStatus();
                if (status != null) {
                    // getName() is null for an unnamed CPU; a throw here kills the server mid-craft.
                    Component cpuName = cpu.getName();
                    ThaumicEnergistics.LOG.info(
                            "[asmstate] cpu {}: crafting {} {}/{} for {} s",
                            cpuName == null ? "(unnamed)" : cpuName.getString(),
                            status.crafting().what(),
                            status.progress(),
                            status.totalItems(),
                            status.elapsedTimeNanos() / 1_000_000_000L);
                }
            }
        }
    }

    private int speedUpgrades;
    /** The vis this machine banks, and the six bars that break it down. */
    private final AssemblerVisPool visPool = new AssemblerVisPool(PRIMALS.size());
    /** Game time at which the relay network may be polled again. See {@link #RELAY_POLL_INTERVAL}. */
    private long nextRelayPoll;

    /** Centivis below a whole vis, per aspect: a whole vis goes to the aspect that supplied it. */
    private final int[] aspectCentivis = new int[PRIMALS.size()];
    /** When {@link #relayReach} was last measured. See {@link #relayNetworkInReach}. */
    private long nextRelayReachCheck;
    /** Whether a usable relay chain was in reach at {@link #nextRelayReachCheck}, else null. */
    private @Nullable Boolean relayReach;

    /** How far the machine looks for one of this mod's vis interfaces: the relay's own reach. */
    private static final int INTERFACE_RANGE = 8;

    /** How long a fruitless interface scan waits. The cube is 4,913 block entity lookups. */
    private static final int INTERFACE_MISS_MAX = 200;

    /** The vis interface last found beside the machine, and when to look again. */
    private @Nullable PartVisInterface nearbyInterface;
    private long nextInterfaceLookup;

    /** How long the next fruitless interface scan waits. Doubles per miss, resets when one is found. */
    private int interfaceMissBackoff = RELAY_POLL_INTERVAL;
    /** When the interface may next be asked. Kept apart from the lookup: different cadences. */
    private long nextInterfacePoll;

    /** Whether to log where this machine's vis came from, once a second. Off unless
     * {@code THAUMICENERGISTICS_VIS_TRACE=true}. */
    private static final boolean VIS_TRACE =
            "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_VIS_TRACE"));

    /** Ticks between {@code [asmvis]} lines. See {@link #VIS_TRACE}. */
    private static final int VIS_TRACE_INTERVAL = 20;

    /** Aura vis taken but not yet a whole vis: the aura is a float, the pool is whole vis. */
    private float auraRemainder;

    /** Totals for the current {@code [asmvis]} window. See {@link #VIS_TRACE}. */
    private int traceRelays;
    private int traceInterfaces;
    private int traceAura;
    /** What the aura path would have banked with each call's fraction dropped, for the same window. */
    private int traceAuraDropped;

    /** Game time at which the next {@code [asmvis]} line may be written. */
    private long nextVisTrace;
    /** Cached vis discount in whole percent, recomputed when the gear slots change. */
    private int gearDiscount;
    private boolean patternsDirty = true;
    /** True while a client update tag or a mirror write is in flight, to suppress cascades. */
    private boolean suppressNotify;

    private List<IPatternDetails> cachedPatterns = List.of();

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

    /** Drops what the player owns - the core and the gear - and nothing else: the mirror, target and
     * preview bands hold copies the machine made, so dropping them would hand out unpaid items. */
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
                        || slot >= PREVIEW_SLOT_START;
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
        return visPool.bufferedVis();
    }

    /** Banked vis of one primal, for the six vis bars. {@code index} is a {@link #PRIMALS} index. */
    public int getAspectVis(int index) {
        return visPool.aspectVis(index);
    }

    public String aspectVisTrace() {
        return visPool.aspectVisTrace();
    }

    /** The vis in the chunks this machine reaches, for anything that reports it to the player. */
    public float getAuraAround() {
        return auraAround();
    }

    /** The ceiling the machine's 3x3 can hold, as reported to the player. See {@link #auraCapacity}. */
    public int getAuraCapacity() {
        return auraCapacity();
    }

    public int getSpeedUpgrades() {
        return speedUpgrades;
    }

    public int getGearDiscount() {
        return gearDiscount;
    }

    /** Raw crafting ticks so far, which the progress column interpolates between. */
    public int getCraftTicks() {
        return craft.craftTicks();
    }

    public int getTicksPerCraft() {
        return ticksPerCraft();
    }

    public float getCraftProgress() {
        int total = ticksPerCraft();
        return craft.isCrafting() && total > 0 ? Math.min(1.0F, (float) craft.craftTicks() / total)
                : 0.0F;
    }

    /** The product of the running craft, or nothing. Empty on the server: the well is the truth. */
    public ItemStack previewStack() {
        return previewStack;
    }

    /** Forces the craft state, for the assembler's self-test. Never called from the mod's own code. */
    public void forceCraftForTest(boolean crafting, int craftTicks) {
        craft.setCrafting(crafting);
        craft.setCraftTicks(craftTicks);
        markForUpdate();
    }

    /** Holds a real recipe as if pushed, and reports what the machine would bank for it. */
    public void forcePatternForTest(ThEArcanePattern pattern) {
        craft.setCurrentPattern(pattern);
        craft.setCrafting(true);
        craft.setCraftPrice(craftCost(pattern));
        craft.setCraftCrystals(crystalStacksOf(pattern));
        // Exactly as beginCraft puts it in; the round-trip check is worthless without it.
        this.inventory.setItem(TARGET_SLOT, pattern.result().copy());
        ThaumicEnergistics.LOG.info(
                "[asmtest] vis target for {} ({} vis) is {}, with {} in the buffer",
                pattern.result(),
                craftCost(pattern),
                visPool.visTarget(craft.isCrafting(), craft.craftPrice()),
                visPool.bufferedVis());
    }

    /** What a load recovered of a running craft, for the assembler's self-test. */
    public String resumeReportForTest() {
        return "crafting=" + craft.isCrafting() + " price=" + craft.craftPrice() + " crystals="
                + craft.craftCrystals().size() + " output=" + inventory.getItem(TARGET_SLOT);
    }

    /** Runs post-load craft recovery against a level, for the self-test: test block entities never reach
     * a level, so {@code onLoad} never fires. Creates no grid node, deliberately. */
    public void recoverForTest(Level level) {
        setLevel(level);
        recoverInterruptedCraft();
    }

    /** Puts a stack in one of this machine's slots for the self-test, via the real inventory. */
    public void setItemForTest(int slot, ItemStack stack) {
        suppressNotify = true;
        try {
            inventory.setItem(slot, stack);
        } finally {
            suppressNotify = false;
        }
    }

    /** Exposed for the assembler's self-test. */
    public static int coreSlotForTest() {
        return CORE_SLOT;
    }

    public void setSpeedUpgrades(int count) {
        this.speedUpgrades = Math.clamp(count, 0, MAX_SPEED_UPGRADES);
        setChanged();
    }

    /** Whether {@code stack} belongs in a gear slot at all; shift-click routing uses this, while the
     * per-slot check additionally requires the right equipment type. */
    public static boolean isGearItem(ItemStack stack) {
        return GearSlots.isGear(stack);
    }

    /** Recomputes the cached discount from the gear slots. Server side only. */
    private void recalculateGearDiscount() {
        int percent = 0;
        for (int i = 0; i < GEAR_SLOT_COUNT; i++) {
            ItemStack stack = inventory.getItem(GEAR_SLOT_START + i);
            if (!stack.isEmpty() && stack.getItem() instanceof IVisDiscountGear gear) {
                percent += gear.getVisDiscount(stack);
            }
        }
        gearDiscount = Math.max(0, percent);
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
        // A second dump: mainNode.create runs in onLoad, so the load-time dump reads as inactive.
        if (STATE_TRACE && stateDumpsLeft > 0 && ++stateDumpTicks >= 20) {
            stateDumpTicks = 0;
            stateDumpsLeft--;
            dumpState("ticked");
        }
        if (patternsDirty) {
            // Settled only on a successful read, so a rebuild with no level yet retries. See rebuildPatterns.
            patternsDirty = !rebuildPatterns();
            ICraftingProvider.requestUpdate(mainNode);
        }
        if (!mainNode.isActive()) {
            return TickRateModulation.IDLE;
        }
        if (visPool.bufferedVis() < visPool.visTarget(craft.isCrafting(), craft.craftPrice())) {
            replenishVis();
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
        if (craft.craftTicks() >= ticksPerCraft()) {
            return completeCraft(grid);
        }

        IEnergyService energy = grid.getService(IEnergyService.class);
        if (energy != null) {
            double needed = ACTIVE_POWER * ticksSinceLast;
            double extracted = energy.extractAEPower(needed, Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (extracted < needed * 0.9) {
                noteStall(wait(WAIT_NO_POWER, "no power"));
                return TickRateModulation.SAME;
            }
        }
        craft.clearStall();
        craft.addCraftTicks(ticksSinceLast);
        // URGENT, not SAME: at the idle rate a busy craft runs twenty times too slow.
        markDisplayForUpdate();
        return TickRateModulation.URGENT;
    }

    /** Reports once that a craft is waiting, then keeps waiting: AE2 already extracted the ingredients.
     * @return always {@code false}: a craft is never abandoned for waiting */
    private boolean noteStall(Component reason) {
        craft.noteStall(reason);
        if (craft.stalledTicks() == STALLED_CRAFT_REPORT_TICKS) {
            ThaumicEnergistics.LOG.info(
                    "[assembler] at {} a craft is waiting for {} ({} ticks so far); it will finish when it"
                            + " can",
                    worldPosition,
                    reason.getString(),
                    craft.stalledTicks());
        }
        return false;
    }

    /** What the running craft is waiting for, or {@code null}. For the tooltip. */
    public @Nullable Component waitReason() {
        return craft.isCrafting() ? craft.lastWait() : null;
    }

    /** Why the last job was turned away, or {@code null} if none was. For the tooltip. */
    public @Nullable Component refusalReason() {
        return craft.lastRefusal();
    }

    private TickRateModulation completeCraft(IGrid grid) {
        int price = craft.craftPrice();
        // Waiting for this price is forever unless a relay or interface reaches vis the aura cannot hold.
        boolean unpayableForever = price > 0
                && auraCapacity() > 0
                && price > auraCapacity()
                && !relayNetworkInReach()
                && !interfaceInReach();
        // A relay that exists but never pays is not a promise either - see STALL_RELEASE_TICKS.
        boolean stalledOut = !unpayableForever && craft.stalledTicks() >= STALL_RELEASE_TICKS;
        if (visPool.bufferedVis() < price && !unpayableForever && !stalledOut) {
            // Waiting on vis; the tick handler keeps refilling the buffer.
            noteStall(wait(WAIT_NO_VIS, "no vis (%s banked of %s needed, target %s)",
                    visPool.bufferedVis(), price, visPool.visTarget(craft.isCrafting(), craft.craftPrice())));
            return TickRateModulation.SAME;
        }
        if (visPool.bufferedVis() < price && stalledOut) {
            ThaumicEnergistics.LOG.warn(
                    "[assembler] at {} delivers {} after {} ticks of waiting for {} vis: a machine that waits"
                            + " for ever refuses every later job",
                    worldPosition, inventory.getItem(TARGET_SLOT), craft.stalledTicks(), price);
        }
        if (visPool.bufferedVis() < price) {
            // Delivered anyway, the lesser evil: AE2 already took the ingredients and waits with no timeout.
            ThaumicEnergistics.LOG.info(
                    "[assembler] at {} delivers {} without charging its {} vis: this chunk's aura can never hold"
                            + " more than {}",
                    worldPosition,
                    inventory.getItem(TARGET_SLOT),
                    price,
                    auraCapacity());
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
            noteStall(wait(WAIT_NO_CRYSTALS, "no crystals"));
            return TickRateModulation.SAME;
        }

        long insertable =
                storage.getInventory().insert(outputKey, output.getCount(), Actionable.SIMULATE, actionSource);
        if (insertable < output.getCount()) {
            noteStall(wait(WAIT_NO_ROOM, "no room for %s", output.getHoverName()));
            return TickRateModulation.SAME;
        }

        // Re-check after the simulate: the extraction below is the point of no return for the crystals.
        if (!hasCrystals(storage)) {
            noteStall(wait(WAIT_NO_CRYSTALS_RECHECK, "no crystals (recheck)"));
            return TickRateModulation.SAME;
        }
        takeCrystals(storage);
        storage.getInventory().insert(outputKey, output.getCount(), Actionable.MODULATE, actionSource);
        // What the craft still owed, and no more.
        visPool.spendVis(price);
        finishCraft();
        return TickRateModulation.URGENT;
    }

    /** Whether the network holds every crystal this craft still needs. */
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

    /** Removes this craft's crystals from the network. */
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
        float modifier = Math.max(1.0F - gearDiscount / 100.0F, MIN_CONSUMPTION_MODIFIER);
        return Math.max(1, (int) Math.ceil(pattern.chargedVis() * modifier));
    }

    /** Hands the craft's ingredients back, dropping in the world whatever the network will not take. */
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

    /** The grid this block is on, or {@code null}. */
    private appeng.api.networking.@Nullable IGrid gridOrNull() {
        return mainNode == null ? null : mainNode.getGrid();
    }

    private void finishCraft() {
        if (craft.isCrafting() && STATE_TRACE) {
            // Paired with the push line: a push with no matching finish is a craft that never completed.
            ThaumicEnergistics.LOG.info("[assembler] craft finished at {}", worldPosition);
        }
        craft.reset();
        clearDisplay(false);
        setChanged();
        markForUpdate();
        // Nothing left to run, so the grid may stop ticking this machine.
        updateSleepiness();
    }

    private int ticksPerCraft() {
        return Math.max(MIN_TICKS_PER_CRAFT, BASE_TICKS_PER_CRAFT - TICKS_PER_SPEED_UPGRADE * speedUpgrades);
    }

    /** The vis in the chunks the machine reaches, the pool a craft is paid out of.
     * @return the total vis around it, or {@code -1} when there is no level to read it from */
    private float auraAround() {
        if (level == null) {
            return -1.0F;
        }
        float total = 0;
        for (int dx = -VIS_SOURCE_RADIUS; dx <= VIS_SOURCE_RADIUS; dx++) {
            for (int dz = -VIS_SOURCE_RADIUS; dz <= VIS_SOURCE_RADIUS; dz++) {
                total += TcAura.vis(level, worldPosition.offset(dx * 16, 0, dz * 16));
            }
        }
        return total;
    }

    /** The most vis the aura can hold, summed over the 3x3: Thaumaturge's base, since remaining
     * capacity is {@code base - totalAura}. */
    private int auraCapacity() {
        if (level == null) {
            return 0;
        }
        int total = 0;
        for (int dx = -VIS_SOURCE_RADIUS; dx <= VIS_SOURCE_RADIUS; dx++) {
            for (int dz = -VIS_SOURCE_RADIUS; dz <= VIS_SOURCE_RADIUS; dz++) {
                total += TcAura.auraBase(level, worldPosition.offset(dx * 16, 0, dz * 16));
            }
        }
        return total;
    }

    /** Whether the aura can pay for {@code pattern}. An unpayable job is refused, not held: the CPU
     * skips a busy provider, so holding it stalls the plan. A low aura waits - its base can rise. */
    private boolean canEverPay(ThEArcanePattern pattern) {
        int capacity = auraCapacity();
        // Zero means the chunk is not initialised yet; a relay counts too, its vis living in a node.
        return capacity <= 0 || relayNetworkInReach() || interfaceInReach() || craftCost(pattern) <= capacity;
    }

    /** Centivis waiting to become a whole vis, across every aspect. See {@link #aspectCentivis}. */
    private int relayCarryTotal() {
        int total = 0;
        for (int value : aspectCentivis) {
            total += value;
        }
        return total;
    }

    /** Tops the vis buffer up from the surrounding aura, from the grid tick rather than the craft loop:
     * aura access is server-thread only. */
    private void replenishVis() {
        int target = visPool.visTarget(craft.isCrafting(), craft.craftPrice());
        if (level == null || level.isClientSide() || visPool.bufferedVis() >= target) {
            return;
        }
        // Relays first, then the aura: a node's vis lives in the node, so the aura alone reads as starved.
        int before = visPool.bufferedVis();
        int fromRelays = drainVisFromRelays(target - visPool.bufferedVis());
        int fromInterfaces = 0;
        if (visPool.bufferedVis() < target) {
            // Asked directly: a relay picks its own parent, preferring a node over an addon source (relink).
            fromInterfaces = drainVisFromInterfaces(target - visPool.bufferedVis());
        }
        int fromAura = 0;
        if (visPool.bufferedVis() < target) {
            // Aura vis has no aspect, so it lands evenly (bankVisEvenly); a drain returns a float.
            int remaining = target - visPool.bufferedVis();
            float thisCall = drainVisAround(remaining);
            float drained = thisCall + auraRemainder;
            int whole = Math.min((int) Math.floor(drained), remaining);
            auraRemainder = drained - whole;
            fromAura = whole;
            // What the same drain would have banked before the fraction was carried. See VIS_TRACE.
            traceAuraDropped += Math.min((int) thisCall, remaining);
            visPool.bankVisEvenly(fromAura);
        }
        if (visPool.bufferedVis() > before) {
            markDisplayForUpdate();
        }
        // Summed over the window: relays are polled one call in twenty, so per-call numbers read zero.
        traceRelays += fromRelays;
        traceInterfaces += fromInterfaces;
        traceAura += fromAura;
        if (VIS_TRACE && level instanceof ServerLevel server && server.getGameTime() >= nextVisTrace) {
            nextVisTrace = server.getGameTime() + VIS_TRACE_INTERVAL;
            ThaumicEnergistics.LOG.info(
                    "[asmvis] at {} relay={} interface={} (inReach={}) aura={} ({} before the carry) over {}"
                            + " ticks -> buffer={}/{}",
                    worldPosition, traceRelays, traceInterfaces, interfaceInReach(), traceAura,
                    traceAuraDropped, VIS_TRACE_INTERVAL, visPool.bufferedVis(), target);
            traceRelays = 0;
            traceInterfaces = 0;
            traceAura = 0;
            traceAuraDropped = 0;
        }
    }

    /** Tops the buffer up from Thaumaturge's relay network, as its workbench does: {@code drainCentivis}
     * finds a linked relay and walks its chain; every primal is asked an equal share.
     * @return whole vis obtained; a short answer means "ask the aura as well" */
    private int drainVisFromRelays(int wantVis) {
        if (wantVis <= 0 || !(level instanceof ServerLevel server)) {
            return 0;
        }
        // Ask the cheap cached question first: each drainCentivis scans for a relay.
        if (!relayNetworkInReach()) {
            return 0;
        }
        long now = server.getGameTime();
        if (now < nextRelayPoll) {
            return 0;
        }
        nextRelayPoll = now + RELAY_POLL_INTERVAL;
        int wantCentivis = wantVis * CENTIVIS_PER_VIS - relayCarryTotal();
        if (wantCentivis <= 0) {
            return 0;
        }
        int share = (wantCentivis + PRIMALS.size() - 1) / PRIMALS.size();
        int taken = 0;
        for (int i = 0; i < PRIMALS.size(); i++) {
            if (taken >= wantCentivis) {
                break;
            }
            int ask = Math.min(share, wantCentivis - taken);
            int got = TcAura.drainCentivis(server, worldPosition, PRIMALS.get(i), ask, false);
            taken += got;
            // Banked per aspect, whole vis only; the remainder stays with the aspect that earned it.
            int carried = aspectCentivis[i] + got;
            visPool.bankVis(carried / CENTIVIS_PER_VIS, i);
            aspectCentivis[i] = carried % CENTIVIS_PER_VIS;
        }
        return taken / CENTIVIS_PER_VIS;
    }

    /** Tops the vis buffer up from a vis interface next to it: a part is not a block entity, so it is
     * found by scanning the cable buses around it, cached for a second. */
    private int drainVisFromInterfaces(int wantVis) {
        if (wantVis <= 0 || !(level instanceof ServerLevel server)) {
            return 0;
        }
        PartVisInterface source = nearbyInterface(server);
        if (source == null) {
            return 0;
        }
        long now = server.getGameTime();
        if (now < nextInterfacePoll) {
            return 0;
        }
        nextInterfacePoll = now + RELAY_POLL_INTERVAL;
        int wantCentivis = wantVis * CENTIVIS_PER_VIS - relayCarryTotal();
        if (wantCentivis <= 0) {
            return 0;
        }
        int share = (wantCentivis + PRIMALS.size() - 1) / PRIMALS.size();
        int taken = 0;
        for (int i = 0; i < PRIMALS.size() && taken < wantCentivis; i++) {
            int ask = Math.min(share, wantCentivis - taken);
            int got = reserveFrom(source, PRIMALS.get(i), ask);
            taken += got;
            int carried = aspectCentivis[i] + got;
            visPool.bankVis(carried / CENTIVIS_PER_VIS, i);
            aspectCentivis[i] = carried % CENTIVIS_PER_VIS;
        }
        return taken / CENTIVIS_PER_VIS;
    }

    /** One reservation against the interface, committed. Zero when it offers nothing for the aspect. */
    private static int reserveFrom(PartVisInterface source, ResourceKey<IAspect> aspect, int centivis) {
        VisReservation reservation = source.reserve(aspect, centivis);
        if (reservation == null) {
            return 0;
        }
        try {
            return reservation.commit();
        } finally {
            // Closing is bookkeeping only: nothing moves on reserve.
            reservation.close();
        }
    }

    /** The nearest active vis interface within reach, looked for at most once a second; with none in
     * sight the wait doubles up to {@link #INTERFACE_MISS_MAX}, so a barren machine stops scanning. */
    private @Nullable PartVisInterface nearbyInterface(ServerLevel server) {
        long now = server.getGameTime();
        if (now < nextInterfaceLookup) {
            return nearbyInterface;
        }
        nextInterfaceLookup = now + interfaceMissBackoff;
        nearbyInterface = findInterface(server);
        interfaceMissBackoff = nearbyInterface == null
                ? Math.min(INTERFACE_MISS_MAX, interfaceMissBackoff * 2)
                : RELAY_POLL_INTERVAL;
        return nearbyInterface;
    }

    /** The closest active Vis Interface part in {@link #INTERFACE_RANGE}. Scanned, not registered. */
    private @Nullable PartVisInterface findInterface(ServerLevel server) {
        PartVisInterface best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -INTERFACE_RANGE; x <= INTERFACE_RANGE; x++) {
            for (int y = -INTERFACE_RANGE; y <= INTERFACE_RANGE; y++) {
                for (int z = -INTERFACE_RANGE; z <= INTERFACE_RANGE; z++) {
                    cursor.setWithOffset(worldPosition, x, y, z);
                    if (!(server.getBlockEntity(cursor) instanceof IPartHost host)) {
                        continue;
                    }
                    for (Direction side : Platform.DIRECTIONS_WITH_NULL) {
                        if (host.getPart(side) instanceof PartVisInterface part && part.isActive()) {
                            double distance = cursor.distSqr(worldPosition);
                            if (distance < bestDistance) {
                                bestDistance = distance;
                                best = part;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Whether a relay chain that can answer is in reach. Asked only when deciding whether a craft is
     * payable: a relay resolving to nothing would accept the job and starve. */
    private boolean relayNetworkInReach() {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        // Cached: the caller runs this every tick while a craft is stalled.
        long now = server.getGameTime();
        if (relayReach == null || now >= nextRelayReachCheck) {
            nextRelayReachCheck = now + RELAY_POLL_INTERVAL;
            BlockEntityVisRelay relay = VisRelayNetwork.findRelayNear(server, worldPosition);
            // Resolving is not paying: one simulated centivis settles whether an empty node can pay.
            // Where the chain ends is not asked separately to a relay any more: a chain has one end,
            // and a source that is not a node - another addon's, or a vis interface - sells its own.
            relayReach = relay != null && relay.resolveSource(server) != null && relayCanSupply(server);
        }
        return relayReach;
    }

    /** Whether the relay chain can give one centivis of any primal: asking only the first primal would
     * refuse a job the chain could pay for out of another. */
    private boolean relayCanSupply(ServerLevel server) {
        for (int i = 0; i < PRIMALS.size(); i++) {
            if (TcAura.drainCentivis(server, worldPosition, PRIMALS.get(i), 1, true) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Whether a vis interface beside the machine can sell it anything: presence is not enough, so a
     * one-centivis reservation asks. Cached like {@link #relayNetworkInReach}. */
    private boolean interfaceInReach() {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        PartVisInterface source = nearbyInterface(server);
        if (source == null) {
            return false;
        }
        // Any primal will do: an interface sells what its node holds, and a node holds one list, not six.
        for (int i = 0; i < PRIMALS.size(); i++) {
            VisReservation probe = source.reserve(PRIMALS.get(i), 1);
            if (probe != null) {
                probe.close();
                return true;
            }
        }
        return false;
    }

    /** What the grid's tick manager was last told: whether the machine has a craft to be ticked for. */
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

    /** Draws up to {@code amount} vis from the chunks the machine reaches: an even share each, then a
     * second pass for the shortfall, so no chunk is emptied while its neighbours are full. */
    private float drainVisAround(int amount) {
        int span = VIS_SOURCE_RADIUS * 2 + 1;
        float share = (float) amount / (span * span);
        float drained = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int dx = -VIS_SOURCE_RADIUS; dx <= VIS_SOURCE_RADIUS; dx++) {
                for (int dz = -VIS_SOURCE_RADIUS; dz <= VIS_SOURCE_RADIUS; dz++) {
                    float want = pass == 0 ? share : amount - drained;
                    if (want <= 0.05F) {
                        continue;
                    }
                    drained += TcAura.drainVis(
                            level, worldPosition.offset(dx * 16, 0, dz * 16), want, false);
                }
            }
        }
        return drained;
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
                    ? refuse(REFUSE_NODE_INACTIVE, "its grid node is not active")
                    : refuse(REFUSE_BUSY, "it is already holding a craft"));
            return false;
        }
        if (!(patternDetails instanceof ArcanePatternDetails details)) {
            noteRefusal(refuse(REFUSE_NOT_ARCANE, "the pattern is not an arcane pattern this machine can read"));
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
        // Traced, not announced: on a busy base this would print two lines a second for the whole order.
        if (STATE_TRACE) {
            ThaumicEnergistics.LOG.info(
                    "[assembler] job pushed at {}: {} (vis {}, crystals {})",
                    worldPosition,
                    details.pattern().result(),
                    details.pattern().baseVis(),
                    details.pattern().crystals().totalAmount());
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
                    ? refuse(REFUSE_NODE_INACTIVE, "its grid node is not active")
                    : refuse(REFUSE_BUSY, "it is already holding a craft"));
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
            noteRefusal(refuse(REFUSE_UNRESOLVED, "the pattern does not resolve to an arcane recipe"));
            return false;
        }
        if (!canEverPay(resolved)) {
            noteRefusal(cannotPay(craftCost(resolved)));
            return false;
        }
        return beginCraft(resolved);
    }

    /** Records why this machine last turned a job away, and says so once per change of reason: a silent
     * refusal is indistinguishable from a machine that was never asked. */
    private void noteRefusal(Component why) {
        if (why.equals(craft.lastRefusal())) {
            return;
        }
        craft.setLastRefusal(why);
        // getString() resolves against the server's language; every key carries an English fallback.
        ThaumicEnergistics.LOG.info("[assembler] at {} turned a job away: {}", worldPosition, why.getString());
    }

    /** Builds one of this machine's tooltip reasons. The key and its English fallback go together so
     * they cannot drift: a missing translation would show the player a raw key. */
    private static Component wait(String key, String english, Object... args) {
        return Component.translatableWithFallback(
                "jade.thaumicenergistics_ce.arcane_assembler.wait_reason." + key, english, args);
    }

    /** A refusal reason. See {@link #wait}. */
    private static Component refuse(String key, String english, Object... args) {
        return Component.translatableWithFallback(
                "jade.thaumicenergistics_ce.arcane_assembler.refuse_reason." + key, english, args);
    }

    // ---- The reason keys, named once --------------------------------------
    // Constants, not literals: the self-test enumerates them to catch a reason key with no translation.

    private static final String WAIT_NO_POWER = "no_power";
    private static final String WAIT_NO_VIS = "no_vis";
    private static final String WAIT_NO_CRYSTALS = "no_crystals";
    private static final String WAIT_NO_CRYSTALS_RECHECK = "no_crystals_recheck";
    private static final String WAIT_NO_ROOM = "no_room";
    private static final String REFUSE_NODE_INACTIVE = "node_inactive";
    private static final String REFUSE_BUSY = "busy";
    private static final String REFUSE_NOT_ARCANE = "not_arcane";
    private static final String REFUSE_UNRESOLVED = "unresolved";
    private static final String REFUSE_TOO_EXPENSIVE = "too_expensive";

    /** Every reason key this machine can put in a tooltip, fully qualified. For the self-test. */
    public static List<String> tooltipReasonKeys() {
        List<String> keys = new ArrayList<>();
        for (String key : List.of(
                WAIT_NO_POWER, WAIT_NO_VIS, WAIT_NO_CRYSTALS, WAIT_NO_CRYSTALS_RECHECK, WAIT_NO_ROOM)) {
            keys.add("jade.thaumicenergistics_ce.arcane_assembler.wait_reason." + key);
        }
        for (String key : List.of(
                REFUSE_NODE_INACTIVE,
                REFUSE_BUSY,
                REFUSE_NOT_ARCANE,
                REFUSE_UNRESOLVED,
                REFUSE_TOO_EXPENSIVE)) {
            keys.add("jade.thaumicenergistics_ce.arcane_assembler.refuse_reason." + key);
        }
        return keys;
    }

    /** The refusal for a recipe whose price this machine's aura can never reach. See {@link #wait}. */
    private Component cannotPay(int price) {
        return refuse(
                REFUSE_TOO_EXPENSIVE,
                "the recipe costs %s vis and this chunk's aura can never hold more than %s (aura nodes would"
                        + " raise it)",
                price,
                auraCapacity());
    }

    /** @return the pattern, or {@code null} when it does not encode an arcane recipe */
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

    /** The advertised pattern that produces {@code result}, or {@code null}. Used after a reload. */
    private @Nullable ThEArcanePattern patternForResult(ItemStack result) {
        if (result.isEmpty()) {
            return null;
        }
        if (patternsDirty) {
            // Rebuild without spending the dirty flag: one with no level yields an empty list, making it final.
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
        markForUpdate();
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
    private boolean rebuildPatterns() {
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
                    ArcanePatternDetails.of(pattern, level.registryAccess(), why -> ThaumicEnergistics.LOG.warn(
                            "[assembler] at {} is not offering the stored pattern for {}: {}",
                            getBlockPos(),
                            pattern.result(),
                            why));
            if (detail != null) {
                details.add(detail);
            }
        }
        if (details.size() < stored.size()) {
            // The shortfall is otherwise invisible: the machine just offers fewer recipes than the core holds.
            ThaumicEnergistics.LOG.warn(
                    "[assembler] at {} offers {} of the {} patterns in its knowledge core",
                    getBlockPos(),
                    details.size(),
                    stored.size());
        }
        if (core.unreadableCount() > 0) {
            // Entries this build cannot read: kept in the item, not offered; otherwise the core reads as empty.
            ThaumicEnergistics.LOG.warn(
                    "[assembler] at {} cannot read {} entr(ies) in its knowledge core; they are kept in the"
                            + " item and {} pattern(s) are offered",
                    getBlockPos(),
                    core.unreadableCount(),
                    details.size());
        }
        cachedPatterns = List.copyOf(details);
        return true;
    }

    /** The knowledge core in the core slot, or {@code null} when there is none. */
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
        recalculateGearDiscount();
        setChanged();
        if (level != null && !level.isClientSide() && mainNode.getGrid() != null) {
            patternsDirty = !rebuildPatterns();
            ICraftingProvider.requestUpdate(mainNode);
        }
        if (level != null && !level.isClientSide()) {
            refreshPatternSlots();
        }
    }

    /** Mirrors what this machine can make into the read-only display slots. */
    private void refreshPatternSlots() {
        if (level == null) {
            return;
        }
        if (patternsDirty) {
            patternsDirty = !rebuildPatterns();
        }
        suppressNotify = true;
        try {
            for (int i = 0; i < PATTERN_SLOT_COUNT; i++) {
                ItemStack stack = ItemStack.EMPTY;
                if (i < cachedPatterns.size()) {
                    List<GenericStack> outputs = cachedPatterns.get(i).getOutputs();
                    if (!outputs.isEmpty() && outputs.getFirst().what() instanceof AEItemKey key) {
                        stack = key.getReadOnlyStack();
                    }
                }
                inventory.setItem(PATTERN_SLOT_START + i, stack);
            }
        } finally {
            suppressNotify = false;
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
        speedUpgrades = Math.clamp(tag.getInt("SpeedUpgrades"), 0, MAX_SPEED_UPGRADES);
        visPool.readNbt(tag);
        suppressNotify = true;
        try {
            ContainerHelper.loadAllItems(tag, inventory.getItems(), registries);
        } finally {
            suppressNotify = false;
        }
        recalculateGearDiscount();
        patternsDirty = true;
        // Only the world-free halves here; onDataPacket defaults to this method, so recovery goes to onLoad.
        if (STATE_TRACE) {
            // Everything about the node in this dump is a lie: the node is created after this method runs.
            dumpState("loaded");
        }
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
            ThaumicEnergistics.LOG.info(
                    "[assembler] at {} resumed the craft a save interrupted: {} for {} vis{}",
                    worldPosition,
                    waiting.getHoverName().getString(),
                    craft.craftPrice(),
                    recovered == null ? " (the knowledge core no longer has its pattern)" : "");
            // Deliver on the first tick: the crafting time was served before the save.
            craft.setCraftTicks(ticksPerCraft());
            craft.clearStall();
        } else {
            craft.setCrafting(false);
            craft.setCraftTicks(0);
            craft.setCraftPrice(0);
            craft.setCraftCrystals(List.of());
            clearDisplay(true);
        }
        // Ask to be ticked rather than assuming a later grid event: with no grid yet this is a no-op.
        updateSleepiness();
    }

    /** Empties the running craft's display - product well and ingredient grid - and nothing else:
     * the core, cards and gear are the player's, and the pattern mirror is derived from the core.
     * @param report whether a clear is worth a line: from the load path, a stale craft is the bug */
    private void clearDisplay(boolean report) {
        boolean hadAnything = !inventory.getItem(TARGET_SLOT).isEmpty();
        suppressNotify = true;
        try {
            inventory.setItem(TARGET_SLOT, ItemStack.EMPTY);
            for (int i = 0; i < PREVIEW_SLOT_COUNT; i++) {
                if (!inventory.getItem(PREVIEW_SLOT_START + i).isEmpty()) {
                    hadAnything = true;
                }
                inventory.setItem(PREVIEW_SLOT_START + i, ItemStack.EMPTY);
            }
        } finally {
            suppressNotify = false;
        }
        if (report && hadAnything) {
            ThaumicEnergistics.LOG.info(
                    "[assembler] at {} cleared a leftover craft display: nothing is crafting", worldPosition);
        }
    }

    /** How much vis this machine banks. Exposed so the tooltip and the machine cannot disagree. */
    public static int visBufferTarget() {
        return AssemblerVisPool.IDLE_TARGET;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        tag.putInt("SpeedUpgrades", speedUpgrades);
        visPool.writeNbt(tag);
        // Saved with the craft, so finishing it after a reload needs nothing but this tag and the well.
        craft.writeNbt(tag, registries);
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
    }

    /** Client sync payload: craft state, vis pool and split, a running craft's product, never the rest
     * of the inventory; it goes at a craft's ends and once a second, not per tick. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        craft.writeSync(tag);
        visPool.writeNbt(tag);
        tag.putInt("GearDiscount", gearDiscount);
        if (!craft.isCrafting() || craft.craftTicks() == 0 || craft.craftTicks() % 100 == 0) {
            tag.put("Preview", inventory.getItem(TARGET_SLOT).saveOptional(registries));
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        applySyncedState(tag, registries);
    }

    /** Applies an update tag on the client, the route a per-tick update takes. A packet lands here, its
     * default implementation ending in {@code loadAdditional}, which wiped the craft state. */
    @Override
    public void onDataPacket(
            Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            applySyncedState(tag, registries);
        }
    }

    /** The fields the client's copy carries, and the only ones allowed to be set from a tag. */
    private void applySyncedState(CompoundTag tag, HolderLookup.Provider registries) {
        suppressNotify = true;
        try {
            craft.readSync(tag);
            visPool.readSync(tag);
            gearDiscount = tag.getInt("GearDiscount");
            // A display: an absent key means "unchanged", the product going out on a slower clock.
            if (tag.contains("Preview")) {
                previewStack = ItemStack.parseOptional(registries, tag.getCompound("Preview"));
            }
        } finally {
            suppressNotify = false;
        }
    }

    /** Sends the display half of the tag, at most every {@link #DISPLAY_UPDATE_INTERVAL} ticks: a
     * running craft ticks at the minimum rate, so it would otherwise go per tick to every watcher. */
    private void markDisplayForUpdate() {
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        if (now - lastDisplayUpdate < DISPLAY_UPDATE_INTERVAL) {
            return;
        }
        lastDisplayUpdate = now;
        markForUpdate();
    }

    /** Sends this block entity's update tag to everyone watching it. Deliberately not
     * {@code level.sendBlockUpdated}, which on 1.21 sends no block entity packet. */
    private void markForUpdate() {
        if (level == null) {
            return;
        }
        setChanged();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        // Only the players watching this chunk, and one packet built once for all of them.
        ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(this);
        for (ServerPlayer player :
                server.getChunkSource().chunkMap.getPlayers(new ChunkPos(worldPosition), false)) {
            player.connection.send(packet);
        }
    }

    // ------------------------------------------------------------------
    // Menu
    // ------------------------------------------------------------------

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        refreshPatternSlots();
        recalculateGearDiscount();
        return new MenuArcaneAssembler(containerId, playerInventory, this);
    }

    /** Server ticker, registered by the block. Grid work is driven by AE2's tick manager. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, BlockEntityArcaneAssembler be) {
        // Intentionally empty: the AE2 grid tick is the machine's only clock.
    }
}
