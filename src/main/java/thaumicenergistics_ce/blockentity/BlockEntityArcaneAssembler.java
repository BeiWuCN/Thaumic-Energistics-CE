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
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.CraftingJobStatus;
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
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.aura.IVisRelaySource;
import com.leclowndu93150.thaumaturge.api.aura.VisRelayHelper;
import com.leclowndu93150.thaumaturge.api.items.IVisDiscountGear;
import com.leclowndu93150.thaumaturge.api.items.IWarpingGear;
import com.leclowndu93150.thaumaturge.content.aura.relay.BlockEntityVisRelay;
import com.leclowndu93150.thaumaturge.content.aura.relay.VisRelayNetwork;
import com.leclowndu93150.thaumaturge.content.taint.item.EssentiaCrystalFactory;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.inventory.GearSlots;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;
import thaumicenergistics_ce.menu.MenuArcaneAssembler;
import thaumicenergistics_ce.part.PartVisInterface;

/**
 * The Arcane Assembler: an AE2 crafting machine that runs Thaumaturge arcane recipes on demand, paying
 * their price in ambient vis rather than with a wand.
 *
 * <p>It is its own {@link ICraftingProvider} (no adjacent pattern provider needed) and also an
 * {@link ICraftingMachine}, so an ordinary AE2 pattern provider can drive it. It offers the patterns in
 * its knowledge core, priced as Thaumaturge's workbench does: base vis plus the primal crystals' vis
 * value, surcharged, then reduced by installed discount gear.
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
    /**
     * Display-only mirror of the running craft's 3x3 ingredients: nine read-only slots the server syncs to
     * the client, which has nothing else to derive them from.
     *
     * <p><b>Appended after the gear, never inserted before it:</b> slot indices are saved, so inserting them
     * would move existing worlds' gear into a band that refuses items.
     */
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
    /**
     * How much vis the machine banks when it has nothing in particular to save for. The idle ceiling, not
     * the crafting ceiling: {@link #visTarget()} raises the target to a running craft's price, and what
     * bounds a craft is {@link #auraCapacity()}.
     */
    private static final int VIS_BUFFER_TARGET = 512;

    /**
     * How far the machine reaches for vis, in chunks: its own and the eight around it. One chunk can never be
     * enough - Thaumaturge caps an aura's base at 500 vis while the priciest recipe costs 1728 - and the
     * 1.12.2 assembler's {@code getWorldVis} summed the same nine positions.
     */
    private static final int VIS_SOURCE_RADIUS = 1;

    /** Centivis in one vis: the relay network answers in hundredths of a vis, the aura in whole vis. */
    private static final int CENTIVIS_PER_VIS = 100;

    /**
     * Ticks between polls of the relay network. Rate limiting: every lookup rescans the blocks around the
     * machine and walks the relay chain, and a node meters vis out per tick anyway.
     */
    private static final int RELAY_POLL_INTERVAL = 20;

    /** Vis and crystals a running craft still owes, saved so it can finish after a reload. See loadAdditional. */
    private static final String TAG_CRAFT_PRICE = "CraftPrice";
    private static final String TAG_CRAFT_CRYSTALS = "CraftCrystals";
    private static final double ACTIVE_POWER = 1.5;
    /** Vanilla minimum consumption modifier; a craft can never be free. */
    private static final float MIN_CONSUMPTION_MODIFIER = 0.1F;
    /** How long a craft may sit unable to pay before the stall is logged: five seconds. */
    private static final int STALLED_CRAFT_REPORT_TICKS = 100;

    /**
     * Ticks of unbroken stalling after which the craft is finished without full payment.
     *
     * <p>A minute. AE2 declares no cancellation callback on a crafting provider, so a craft that waits for
     * ever leaves the machine busy for ever: it refuses every later job and the only way out is to break the
     * block. Delivering late is the lesser evil, and it is the same mercy the aura case below already gets.
     */
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
                    // Behind the trace switch, not on by default. It was written for a machine that drops off
                    // its grid and leaves no other trace, and a grid that is merely coming up changes state
                    // several times in a row - on a busy base that is a wall of lines nobody asked for. One
                    // line per grid-state change, still not per tick.
                    if (STATE_TRACE) {
                        ThaumicEnergistics.LOG.info(
                                "[assembler] at {}: grid {} changed, now active={} powered={} booted={}{}",
                                owner.worldPosition,
                                state,
                                owner.mainNode.isActive(),
                                owner.mainNode.isPowered(),
                                owner.mainNode.hasGridBooted(),
                                owner.crafting ? ", and it is holding a craft" : "");
                    }
                    owner.markForUpdate();
                    // loadAdditional runs before the node exists, so a craft resumed there cannot wake
                    // anything itself; this boot-time state change has to.
                    owner.updateSleepiness();
                }
            };

    private boolean active;
    private boolean crafting;
    private int craftTicks;

    /** Game time of the last display update, which is what throttles a running craft's packets. */
    private long lastDisplayUpdate;

    /**
     * A copy of the running craft's product, for the renderer only: written on the client out of the update
     * tag, never extracted, dropped or offered to a menu. The real product sits in {@link #TARGET_SLOT}.
     */
    private ItemStack previewStack = ItemStack.EMPTY;

    /**
     * Why this machine last turned a job away, so the same reason is not logged once per push attempt. A
     * translatable component, not a baked-in English sentence, so the tooltip can be localised.
     */
    private @Nullable Component lastRefusal;

    /** What the running craft is waiting for, or {@code null}. See {@link #waitReason}. */
    private @Nullable Component lastWait;

    /**
     * Whether to log a loaded machine's whole state. Its own switch rather than the self-test's, which drives
     * a machine of its own and would clobber a real craft.
     */
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
                crafting,
                craftTicks,
                ticksPerCraft(),
                bufferedVis,
                visTarget(),
                craftCrystals.size(),
                currentPattern == null ? "none" : currentPattern.result(),
                mainNode.isActive(),
                mainNode.isPowered(),
                mainNode.hasGridBooted(),
                mainNode.getGrid() != null,
                heldInputs.size(),
                offered.size(),
                offered.isEmpty() ? "" : " [" + products.toString().trim()
                        + (offered.size() > 4 ? " ..." : "") + "]",
                // The pool the craft is actually paid out of: see auraAround.
                auraAround(),
                auraCapacity(),
                relayNetworkInReach(),
                relayCarryTotal(),
                aspectVisTrace());
        IGrid grid = gridOrNull();
        ICraftingService crafting = grid == null ? null : grid.getService(ICraftingService.class);
        if (crafting != null) {
            for (ICraftingCPU cpu : crafting.getCpus()) {
                CraftingJobStatus status = cpu.getJobStatus();
                if (status != null) {
                    // AE2's ICraftingCPU.getName() is null for an unnamed CPU; a throw here used to take the
                    // server down mid-craft with "Ticking GridNode".
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
    /** Consecutive ticks this craft has been unable to proceed. See {@link #noteStall}. */
    private int stalledTicks;

    /**
     * The ingredients AE2 extracted from the network when it pushed the running craft. This machine makes its
     * product from vis and crystals, so they are kept only to hand back if the craft never finishes. See
     * {@link #returnHeldInputs}.
     */
    private final List<ItemStack> heldInputs = new ArrayList<>();
    private int speedUpgrades;
    /** Ambient vis pulled from the aura and the relay network, buffered for the next craft. */
    private int bufferedVis;
    /** Game time at which the relay network may be polled again. See {@link #RELAY_POLL_INTERVAL}. */
    private long nextRelayPoll;
    /**
     * The vis banked, split by primal and indexed as {@link #PRIMALS} is. A breakdown of
     * {@link #bufferedVis}, never a second source of truth: that field stays the number every price and
     * stall test reads, while this records where the vis came from. Relays are metered per aspect; the aura
     * is a scalar and is spread evenly.
     */
    private final int[] aspectVis = new int[PRIMALS.size()];

    /**
     * Centivis taken from the relay network that have not yet added up to a whole vis, per aspect, so a
     * whole vis goes to the aspect that supplied it. See {@link #drainVisFromRelays}.
     */
    private final int[] aspectCentivis = new int[PRIMALS.size()];
    /** When {@link #relayReach} was last measured. See {@link #relayNetworkInReach}. */
    private long nextRelayReachCheck;
    /** Whether a usable relay chain was in reach at {@link #nextRelayReachCheck}, or null if never asked. */
    private @Nullable Boolean relayReach;

    /** How far the machine looks for one of this mod's vis interfaces: the relay network's own reach. */
    private static final int INTERFACE_RANGE = 8;

    /** How long a fruitless interface scan waits. The cube is 4,913 block entity lookups. */
    private static final int INTERFACE_MISS_MAX = 200;

    /** The vis interface last found beside the machine, and when to look again. */
    private @Nullable PartVisInterface nearbyInterface;
    private long nextInterfaceLookup;

    /** How long the next fruitless interface scan waits. Doubles per miss, resets when one is found. */
    private int interfaceMissBackoff = RELAY_POLL_INTERVAL;
    /** When the interface may next be asked. Kept apart from the lookup: they are different cadences. */
    private long nextInterfacePoll;

    /**
     * Whether to log where this machine's vis came from, once a second. Off unless
     * {@code THAUMICENERGISTICS_VIS_TRACE=true}: the sources are otherwise indistinguishable from the
     * machine's own rising buffer.
     */
    private static final boolean VIS_TRACE =
            "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_VIS_TRACE"));

    /** Ticks between {@code [asmvis]} lines. See {@link #VIS_TRACE}. */
    private static final int VIS_TRACE_INTERVAL = 20;

    /** Aura vis taken but not yet a whole vis: the aura is a float, the pool is whole vis. See {@link #replenishVis}. */
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

    private @Nullable ThEArcanePattern currentPattern;

    /**
     * Vis the running craft agreed to pay, saved rather than derived: the pattern comes from the knowledge
     * core, which can be removed mid-craft, and AE2's crafting CPU waits for ever on a job it has already
     * pushed. See {@link #recoverInterruptedCraft}.
     */
    private int craftPrice;

    /**
     * Crystals the running craft has to be handed, because vis cannot stand in for them. Saved with the
     * craft for the same reason the price is. Empty when every crystal is primal.
     */
    private List<ItemStack> craftCrystals = List.of();
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
            // loadAdditional ran before setLevel, so there was no world to match a restored craft against
            // then. See recoverInterruptedCraft.
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

    /**
     * Drops everything the player owns - the core and the gear - and nothing else. The pattern mirror is
     * derived from the core and the target and preview bands hold copies the machine made, so dropping them
     * hands out items nobody paid for: a range-based skip did exactly that once the preview band was added.
     */
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
                    continue; // the machine's own display; none of it was ever the player's
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
        return crafting;
    }

    public boolean isActive() {
        return active;
    }

    public int getBufferedVis() {
        return bufferedVis;
    }

    /** How much of one primal is banked, for the six vis bars. {@code index} is a {@link #PRIMALS} index. */
    public int getAspectVis(int index) {
        return index >= 0 && index < aspectVis.length ? aspectVis[index] : 0;
    }

    public String aspectVisTrace() {
        StringBuilder text = new StringBuilder();
        for (int value : aspectVis) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(value);
        }
        return text.toString();
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
        return craftTicks;
    }

    public int getTicksPerCraft() {
        return ticksPerCraft();
    }

    public float getCraftProgress() {
        int total = ticksPerCraft();
        return crafting && total > 0 ? Math.min(1.0F, (float) craftTicks / total) : 0.0F;
    }

    /** The product of the running craft, or nothing. Empty on the server, where the well is the truth. */
    public ItemStack previewStack() {
        return previewStack;
    }

    /** Forces the craft state, for the assembler's self-test. Never called from the mod's own code. */
    public void forceCraftForTest(boolean crafting, int craftTicks) {
        this.crafting = crafting;
        this.craftTicks = craftTicks;
        markForUpdate();
    }

    /**
     * Holds a real recipe as if it had been pushed, and reports what the machine would bank for it. The
     * recipe is real, so the price is the one a real craft would be asked for.
     */
    public void forcePatternForTest(ThEArcanePattern pattern) {
        this.currentPattern = pattern;
        this.crafting = true;
        this.craftPrice = craftCost(pattern);
        this.craftCrystals = crystalStacksOf(pattern);
        // Exactly as beginCraft puts it in; the round-trip check is worthless without it.
        this.inventory.setItem(TARGET_SLOT, pattern.result().copy());
        ThaumicEnergistics.LOG.info(
                "[asmtest] vis target for {} ({} vis) is {}, with {} in the buffer",
                pattern.result(),
                craftCost(pattern),
                visTarget(),
                bufferedVis);
    }

    /** What a load recovered of a running craft, for the assembler's self-test. */
    public String resumeReportForTest() {
        return "crafting=" + crafting + " price=" + craftPrice + " crystals=" + craftCrystals.size()
                + " output=" + inventory.getItem(TARGET_SLOT);
    }

    /**
     * Runs the post-load craft recovery against a level, for the self-test: its block entities are never
     * added to a level, so {@code onLoad} never fires for them. Deliberately does not create the grid node.
     */
    public void recoverForTest(net.minecraft.world.level.Level level) {
        setLevel(level);
        recoverInterruptedCraft();
    }

    /** Puts a stack in one of this machine's slots, for the self-test. Goes through the real inventory. */
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

    /**
     * Whether {@code stack} belongs in a gear slot at all. Used by shift-click routing; the per-slot check
     * additionally requires the right equipment type.
     */
    public static boolean isGearItem(ItemStack stack) {
        return GearSlots.isGear(stack);
    }

    /** A gear slot takes vis-discount gear, warping gear, or ordinary armour matching the slot. */

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
        // Never start asleep: a core can be inserted while the node is idle, and a sleeping node is
        // never woken to publish the new patterns.
        return new TickingRequest(1, 20, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        if (level == null || level.isClientSide()) {
            return TickRateModulation.SLEEP;
        }
        // A second opinion on the state, once a second: mainNode.create runs in onLoad, after
        // loadAdditional, so the dump taken while the tag was loading reports a connected machine as inactive.
        if (STATE_TRACE && stateDumpsLeft > 0 && ++stateDumpTicks >= 20) {
            stateDumpTicks = 0;
            stateDumpsLeft--;
            dumpState("ticked");
        }
        if (patternsDirty) {
            // Settled only if the read succeeded, so a rebuild before there was a level is retried rather
            // than taken as "the core holds nothing". See rebuildPatterns.
            patternsDirty = !rebuildPatterns();
            ICraftingProvider.requestUpdate(mainNode);
        }
        if (!mainNode.isActive()) {
            return TickRateModulation.IDLE;
        }
        if (bufferedVis < visTarget()) {
            replenishVis();
        }
        if (!crafting) {
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
        // No test for a missing pattern here: a craft is defined by what it produces and what it owes, all
        // saved with it. Requiring the recipe dropped crafts whose core had been taken out, stranding the plan.
        if (craftTicks >= ticksPerCraft()) {
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
        stalledTicks = 0;
        craftTicks += ticksSinceLast;
        // URGENT, not SAME: an idle machine is ticked at the idle rate, so answering SAME keeps a busy craft
        // twenty times too slow. The block update is what moves the client's progress bar - a tick count that
        // exists only on the server cannot - but it goes out on a cadence, not on every tick.
        markDisplayForUpdate();
        return TickRateModulation.URGENT;
    }

    /**
     * Reports, once, that a craft is waiting for something, and then keeps waiting. It does not release the
     * job: a push is not free, since AE2 has already extracted the ingredients, and giving up threw a set
     * away every five seconds.
     *
     * @return always {@code false}: a craft is never abandoned for waiting
     */
    private boolean noteStall(Component reason) {
        stalledTicks++;
        // Held for the tooltip from the first stalled tick; only the log line is throttled.
        lastWait = reason;
        if (stalledTicks == STALLED_CRAFT_REPORT_TICKS) {
            ThaumicEnergistics.LOG.info(
                    "[assembler] at {} a craft is waiting for {} ({} ticks so far); it will finish when it"
                            + " can",
                    worldPosition,
                    reason.getString(),
                    stalledTicks);
        }
        return false;
    }

    /** What the running craft is waiting for, or {@code null}. For the tooltip. */
    public @Nullable Component waitReason() {
        return crafting ? lastWait : null;
    }

    /** Why the last job was turned away, or {@code null} if none was. For the tooltip. */
    public @Nullable Component refusalReason() {
        return lastRefusal;
    }

    private TickRateModulation completeCraft(IGrid grid) {
        int price = craftPrice;
        // Waiting for this price is waiting for ever - the chunk's aura cannot hold it - unless a relay or a
        // vis interface can reach vis the aura cannot hold, such as an energized node's.
        boolean unpayableForever = price > 0
                && auraCapacity() > 0
                && price > auraCapacity()
                && !relayNetworkInReach()
                && !interfaceInReach();
        // A relay that exists but never pays is not a promise either - see STALL_RELEASE_TICKS.
        boolean stalledOut = !unpayableForever && stalledTicks >= STALL_RELEASE_TICKS;
        if (bufferedVis < price && !unpayableForever && !stalledOut) {
            // Waiting on vis; the tick handler keeps refilling the buffer.
            noteStall(wait(WAIT_NO_VIS, "no vis (%s banked of %s needed, target %s)",
                    bufferedVis, price, visTarget()));
            return TickRateModulation.SAME;
        }
        if (bufferedVis < price && stalledOut) {
            ThaumicEnergistics.LOG.warn(
                    "[assembler] at {} delivers {} after {} ticks of waiting for {} vis: a machine that waits"
                            + " for ever refuses every later job",
                    worldPosition, inventory.getItem(TARGET_SLOT), stalledTicks, price);
        }
        if (bufferedVis < price) {
            // Delivered anyway, the lesser evil: AE2 has already taken the ingredients and waits for the
            // product with no timeout, and it skips a provider that reports itself busy.
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

        // The product from the well: exactly what the network's plan is waiting for, and the recipe that
        // made it may not be readable any more.
        ItemStack output = inventory.getItem(TARGET_SLOT).copy();
        AEItemKey outputKey = AEItemKey.of(output);
        if (outputKey == null) {
            finishCraft();
            return TickRateModulation.IDLE;
        }

        // The crystals vis cannot stand in for, rather than a local buffer. Checked before the result is
        // inserted, so a craft is never paid for with the output already delivered.
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
        spendVis(price);
        finishCraft();
        return TickRateModulation.URGENT;
    }

    /** Whether the network holds every crystal this craft still needs. */
    private boolean hasCrystals(IStorageService storage) {
        for (ItemStack stack : craftCrystals) {
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
        for (ItemStack stack : craftCrystals) {
            AEItemKey key = AEItemKey.of(stack);
            if (key != null) {
                storage.getInventory()
                        .extract(key, stack.getCount(), Actionable.MODULATE, actionSource);
            }
        }
    }

    /**
     * The non-primal crystals a pattern's craft has to be handed, as items the network will be asked for.
     */
    private static List<ItemStack> crystalStacksOf(ThEArcanePattern pattern) {
        List<ItemStack> stacks = new ArrayList<>();
        for (AspectInstance crystal : pattern.crystalItems().entries()) {
            ItemStack stack = EssentiaCrystalFactory.of(crystal.aspect(), crystal.amount());
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return List.copyOf(stacks);
    }

    /**
     * The vis actually charged for {@code pattern}, after the gear discount and floored by Thaumaturge's own
     * {@code MIN_CONSUMPTION_MODIFIER}, so an equipped assembler still pays something.
     */
    public int craftCost(ThEArcanePattern pattern) {
        float modifier = Math.max(1.0F - gearDiscount / 100.0F, MIN_CONSUMPTION_MODIFIER);
        return Math.max(1, (int) Math.ceil(pattern.chargedVis() * modifier));
    }

    /**
     * Hands the running craft's ingredients back: the network first, and anything it will not take is
     * dropped in the world rather than deleted.
     */
    private void returnHeldInputs() {
        if (heldInputs.isEmpty()) {
            return;
        }
        IStorageService storage = null;
        IGrid grid = gridOrNull();
        if (grid != null) {
            storage = grid.getService(IStorageService.class);
        }
        for (ItemStack stack : heldInputs) {
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
        heldInputs.clear();
    }

    /** The grid this block is on, or {@code null}. */
    private appeng.api.networking.@Nullable IGrid gridOrNull() {
        return mainNode == null ? null : mainNode.getGrid();
    }

    private void finishCraft() {
        if (crafting && STATE_TRACE) {
            // Paired with the push line: a push with no matching finish is a craft that never completed.
            ThaumicEnergistics.LOG.info("[assembler] craft finished at {}", worldPosition);
        }
        crafting = false;
        craftTicks = 0;
        currentPattern = null;
        craftPrice = 0;
        craftCrystals = List.of();
        lastWait = null;
        // The craft is over, so the ingredients it held really have been spent. See heldInputs.
        heldInputs.clear();
        clearDisplay(false);
        setChanged();
        markForUpdate();
        // Nothing left to run, so the grid may stop ticking this machine.
        updateSleepiness();
    }

    private int ticksPerCraft() {
        return Math.max(MIN_TICKS_PER_CRAFT, BASE_TICKS_PER_CRAFT - TICKS_PER_SPEED_UPGRADE * speedUpgrades);
    }

    /**
     * The vis sitting in the chunks the machine reaches - its own and the eight around it - and the pool a
     * craft is paid out of: one chunk alone understates it by up to eight ninths. Summed over the same
     * positions {@link #drainVisAround} draws from and {@link #auraCapacity} measures.
     *
     * @return the total vis around the machine, or {@code -1} when there is no level to read it from
     */
    private float auraAround() {
        if (level == null) {
            return -1.0F;
        }
        float total = 0;
        for (int dx = -VIS_SOURCE_RADIUS; dx <= VIS_SOURCE_RADIUS; dx++) {
            for (int dz = -VIS_SOURCE_RADIUS; dz <= VIS_SOURCE_RADIUS; dz++) {
                total += AuraHelper.getVis(level, worldPosition.offset(dx * 16, 0, dz * 16));
            }
        }
        return total;
    }

    /**
     * The most vis this chunk's aura can ever hold: its base, since Thaumaturge measures remaining capacity
     * as {@code base - totalAura}. Summed over the 3x3, so it can exceed a single chunk's 500.
     */
    private int auraCapacity() {
        if (level == null) {
            return 0;
        }
        int total = 0;
        for (int dx = -VIS_SOURCE_RADIUS; dx <= VIS_SOURCE_RADIUS; dx++) {
            for (int dz = -VIS_SOURCE_RADIUS; dz <= VIS_SOURCE_RADIUS; dz++) {
                total += AuraHelper.getAuraBase(level, worldPosition.offset(dx * 16, 0, dz * 16));
            }
        }
        return total;
    }

    /**
     * Whether this chunk's aura could ever pay for {@code pattern}. A job it can never pay is refused rather
     * than accepted and held: AE2's CPU skips a provider that reports {@code isBusy()}, so holding it stalls
     * the plan too. A merely low aura still waits, since its base can rise.
     */
    private boolean canEverPay(ThEArcanePattern pattern) {
        int capacity = auraCapacity();
        // A zero here means the chunk has not been initialised yet. A relay chain or vis interface that can
        // actually hand something over counts too: their vis is held in a node rather than in the chunk, so
        // the chunk's aura is the wrong question to ask about them. Both helpers probe rather than assume.
        return capacity <= 0 || relayNetworkInReach() || interfaceInReach() || craftCost(pattern) <= capacity;
    }

    /**
     * How much vis this machine wants to be holding: the idle buffer, raised to a running craft's price.
     * Several arcane recipes cost more than the buffer - the wand wants 1728 - and a machine that can only
     * bank 512 never pays, so it never finishes.
     */
    private int visTarget() {
        return crafting ? Math.max(VIS_BUFFER_TARGET, craftPrice) : VIS_BUFFER_TARGET;
    }

    /** Banks whole vis against one aspect and adds it to the pool; the only way vis should enter the buffer. */
    private void bankVis(int amount, int primalIndex) {
        if (amount <= 0 || primalIndex < 0 || primalIndex >= aspectVis.length) {
            return;
        }
        aspectVis[primalIndex] += amount;
        bufferedVis += amount;
    }

    /**
     * Banks vis that has no aspect of its own, spread evenly over the six: Thaumaturge's aura is one scalar
     * per chunk, so any other split would be inventing a distribution the game never had.
     */
    private void bankVisEvenly(int amount) {
        if (amount <= 0) {
            return;
        }
        // Added to what is already banked: the relay path may have put an uneven split there a moment ago.
        int base = amount / aspectVis.length;
        int remainder = amount % aspectVis.length;
        for (int i = 0; i < aspectVis.length; i++) {
            aspectVis[i] += base + (i < remainder ? 1 : 0);
        }
        bufferedVis += amount;
        reconcileAspectVis();
    }

    /** Fills the six aspects evenly, without touching the pool. Used by {@link #bankVisEvenly} and on load. */
    private void spreadEvenly(int amount) {
        java.util.Arrays.fill(aspectVis, 0);
        int base = amount / aspectVis.length;
        int remainder = amount % aspectVis.length;
        for (int i = 0; i < aspectVis.length; i++) {
            aspectVis[i] = base + (i < remainder ? 1 : 0);
        }
    }

    /**
     * Takes vis out of the pool and out of the aspects in proportion, so the six bars drain together: a craft
     * is priced as one lump, so there is no per-aspect part of the cost to charge.
     */
    private void spendVis(int amount) {
        int spent = Math.min(bufferedVis, Math.max(0, amount));
        if (spent <= 0) {
            return;
        }
        int total = bufferedVis;
        bufferedVis -= spent;
        int taken = 0;
        for (int i = 0; i < aspectVis.length; i++) {
            int share = (int) ((long) aspectVis[i] * spent / total);
            share = Math.min(share, aspectVis[i]);
            aspectVis[i] -= share;
            taken += share;
        }
        // Rounding can leave a few vis unaccounted for; the largest holding is the one that can absorb it.
        int leftover = spent - taken;
        if (leftover > 0) {
            int largest = 0;
            for (int i = 1; i < aspectVis.length; i++) {
                if (aspectVis[i] > aspectVis[largest]) {
                    largest = i;
                }
            }
            aspectVis[largest] = Math.max(0, aspectVis[largest] - leftover);
        }
        reconcileAspectVis();
    }

    /**
     * Forces the six aspects to add up to {@link #bufferedVis}: the pool is the real state and the breakdown
     * is only what the bars read. Never touches the pool itself.
     */
    private void reconcileAspectVis() {
        int sum = 0;
        for (int value : aspectVis) {
            sum += value;
        }
        int difference = bufferedVis - sum;
        if (difference == 0) {
            return;
        }
        if (difference > 0) {
            aspectVis[0] += difference;
            return;
        }
        int excess = -difference;
        while (excess > 0) {
            int largest = 0;
            for (int i = 1; i < aspectVis.length; i++) {
                if (aspectVis[i] > aspectVis[largest]) {
                    largest = i;
                }
            }
            int take = Math.min(excess, aspectVis[largest]);
            if (take <= 0) {
                break;
            }
            aspectVis[largest] -= take;
            excess -= take;
        }
    }

    /** Centivis waiting to become a whole vis, across every aspect. See {@link #aspectCentivis}. */
    private int relayCarryTotal() {
        int total = 0;
        for (int value : aspectCentivis) {
            total += value;
        }
        return total;
    }

    /**
     * Tops the vis buffer up from the surrounding aura. Runs from the grid tick rather than the craft loop,
     * so a craft waiting on vis still progresses. Aura access is server-thread only.
     */
    private void replenishVis() {
        int target = visTarget();
        if (level == null || level.isClientSide() || bufferedVis >= target) {
            return;
        }
        // The relay network first, then the aura for whatever it would not give. The order matters: a node's
        // vis is held in the node, so reading the aura alone reports a machine with a full node as starved.
        int before = bufferedVis;
        int fromRelays = drainVisFromRelays(target - bufferedVis);
        int fromInterfaces = 0;
        if (bufferedVis < target) {
            // Then a Vis Interface part, if one is beside the machine. Asked directly rather than through the
            // relay network, because a relay picks its own parent and Thaumaturge prefers a node over an
            // addon source (BlockEntityVisRelay.relink), so the interface would never be asked through it.
            fromInterfaces = drainVisFromInterfaces(target - bufferedVis);
        }
        int fromAura = 0;
        if (bufferedVis < target) {
            // The aura for whatever the relay network and the interface would not give. Its vis has no
            // aspect, so it lands evenly (see bankVisEvenly); drains return a float while the pool is whole.
            int remaining = target - bufferedVis;
            float thisCall = drainVisAround(remaining);
            float drained = thisCall + auraRemainder;
            int whole = Math.min((int) Math.floor(drained), remaining);
            auraRemainder = drained - whole;
            fromAura = whole;
            // What the same drain would have banked before the fraction was carried. See VIS_TRACE.
            traceAuraDropped += Math.min((int) thisCall, remaining);
            bankVisEvenly(fromAura);
        }
        if (bufferedVis > before) {
            markDisplayForUpdate();
        }
        // Summed over the window: the relay and interface are polled on one call in twenty, so a per-call
        // number would read zero nineteen times out of twenty.
        traceRelays += fromRelays;
        traceInterfaces += fromInterfaces;
        traceAura += fromAura;
        if (VIS_TRACE && level instanceof ServerLevel server && server.getGameTime() >= nextVisTrace) {
            nextVisTrace = server.getGameTime() + VIS_TRACE_INTERVAL;
            ThaumicEnergistics.LOG.info(
                    "[asmvis] at {} relay={} interface={} (inReach={}) aura={} ({} before the carry) over {}"
                            + " ticks -> buffer={}/{}",
                    worldPosition, traceRelays, traceInterfaces, interfaceInReach(), traceAura,
                    traceAuraDropped, VIS_TRACE_INTERVAL, bufferedVis, target);
            traceRelays = 0;
            traceInterfaces = 0;
            traceAura = 0;
            traceAuraDropped = 0;
        }
    }

    /**
     * Tops the vis buffer up from Thaumaturge's vis relay network, the call its arcane workbench makes:
     * {@code VisRelayHelper.drainCentivis} finds a linked relay within eight blocks and walks its chain to a
     * node or an addon source. Every primal is asked in turn for an equal share, banked against the aspect
     * that gave it - the node holds its vis as an {@code AspectList}, so the six bars can differ.
     *
     * @param wantVis whole vis still wanted for the buffer
     * @return whole vis obtained; callers treat a short answer as "ask the aura as well"
     */
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
            int got = VisRelayHelper.drainCentivis(server, worldPosition, PRIMALS.get(i), ask, false);
            taken += got;
            // Banked per aspect, whole vis only; the remainder stays with the aspect that earned it.
            int carried = aspectCentivis[i] + got;
            bankVis(carried / CENTIVIS_PER_VIS, i);
            aspectCentivis[i] = carried % CENTIVIS_PER_VIS;
        }
        return taken / CENTIVIS_PER_VIS;
    }

    /**
     * Tops the vis buffer up from a vis interface beside the machine. A part is not a block entity, so it is
     * found by asking the cable buses around it, and the answer is cached for a second.
     */
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
            bankVis(carried / CENTIVIS_PER_VIS, i);
            aspectCentivis[i] = carried % CENTIVIS_PER_VIS;
        }
        return taken / CENTIVIS_PER_VIS;
    }

    /** One reservation against the interface, committed. Zero when it offers nothing for this aspect. */
    private static int reserveFrom(PartVisInterface source, ResourceKey<IAspect> aspect, int centivis) {
        IVisRelaySource.Reservation reservation = source.reserve(aspect, centivis);
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

    /**
     * The nearest active vis interface within reach, looked for at most once a second - and, when there is
     * none, with the wait doubling up to {@link #INTERFACE_MISS_MAX}, since a machine with no interface
     * beside it would otherwise search the cube for ever.
     */
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

    /** The closest active Vis Interface part within {@link #INTERFACE_RANGE}. Scanned, not registered. */
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

    /**
     * Whether a vis relay chain that can actually answer is within reach. Only asked when deciding whether a
     * craft is payable at all; a relay that resolves to neither a node nor an addon source would accept the
     * job and then starve on it.
     */
    private boolean relayNetworkInReach() {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        // Cached: the caller runs this every tick while a craft is stalled.
        long now = server.getGameTime();
        if (relayReach == null || now >= nextRelayReachCheck) {
            nextRelayReachCheck = now + RELAY_POLL_INTERVAL;
            BlockEntityVisRelay relay = VisRelayNetwork.findRelayNear(server, worldPosition);
            // A relay that resolves is not yet a promise: it must also be able to hand something over. One
            // simulated centivis is the cheapest question that answers it, and a chain that resolves to a
            // node with nothing in it used to accept jobs it could never pay for.
            relayReach = relay != null
                    && (relay.resolveSource(server) != null || relay.resolveAddonSource(server) != null)
                    && relayCanSupply(server);
        }
        return relayReach;
    }

    /**
     * Whether the relay chain can hand over a single centivis of any primal.
     *
     * <p>Per aspect, because a node holds its vis as one: asking only the first primal would refuse a job the
     * chain could pay for out of another.
     */
    private boolean relayCanSupply(ServerLevel server) {
        for (int i = 0; i < PRIMALS.size(); i++) {
            if (VisRelayHelper.drainCentivis(server, worldPosition, PRIMALS.get(i), 1, true) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a vis interface beside the machine can actually sell it something, cached the way
     * {@link #relayNetworkInReach} is. Presence alone is not enough: an interface next to a node that holds
     * nothing would otherwise have jobs accepted that it could never pay for. A one-centivis reservation
     * asks the question, and a reservation moves nothing.
     */
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
            IVisRelaySource.Reservation probe = source.reserve(PRIMALS.get(i), 1);
            if (probe != null) {
                probe.close();
                return true;
            }
        }
        return false;
    }

    /** What the grid's tick manager was last told: whether this machine has a craft to be ticked for. */
    private boolean awakeForCraft;

    /**
     * Wakes the grid's tick while this machine holds a craft, and lets it sleep when it does not. Without
     * this, a craft that came back from a save never runs: AE2's tick manager drops a device to its idle
     * rate, so the product sits in the well and nothing hands it over.
     */
    private void updateSleepiness() {
        if (level == null || level.isClientSide() || awakeForCraft == crafting) {
            return;
        }
        IGrid grid = gridOrNull();
        IGridNode node = mainNode.getNode();
        if (grid == null || node == null) {
            return;
        }
        awakeForCraft = crafting;
        if (crafting) {
            grid.getTickManager().wakeDevice(node);
        } else {
            grid.getTickManager().sleepDevice(node);
        }
    }

    /**
     * Draws up to {@code amount} vis from the chunks the machine reaches: an even share each, then a second
     * pass for the shortfall, as the 1.12.2 assembler did, so no chunk is emptied while its neighbours are
     * full. Unloaded chunks contribute nothing.
     */
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
                    drained += AuraHelper.drainVis(
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
            // Only a rebuild that could actually read the core settles the flag; AE2 asks this while the
            // machine is loading, when there is no level yet. See rebuildPatterns.
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
        // What AE2 just took out of the network for this craft. This machine never consumes these - it pays
        // in vis and crystals instead - so they are only kept so a craft that never finishes can give them back.
        heldInputs.clear();
        for (KeyCounter counter : inputHolder) {
            for (var entry : counter) {
                if (entry.getKey() instanceof AEItemKey itemKey && entry.getLongValue() > 0) {
                    heldInputs.add(itemKey.toStack((int) Math.min(Integer.MAX_VALUE, entry.getLongValue())));
                }
            }
        }
        // A pushed job is the one event that says the CPU found this machine and handed it work. Traced, not
        // announced: a machine that is being fed a large order prints this once per craft, which on a base
        // running a stack a second is two lines a second for as long as the order lasts.
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
        return crafting;
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
        return !crafting;
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

    /**
     * Records why this machine last turned a job away, and says so once per change of reason. A refusal used
     * to be silent, and silence is indistinguishable from a machine that was never asked.
     */
    private void noteRefusal(Component why) {
        if (why.equals(lastRefusal)) {
            return;
        }
        lastRefusal = why;
        // getString() resolves against the server's language; every key carries an English fallback.
        ThaumicEnergistics.LOG.info("[assembler] at {} turned a job away: {}", worldPosition, why.getString());
    }

    /**
     * Builds one of this machine's tooltip reasons, so the key and the English fallback cannot drift: a
     * missing translation without the fallback would show a player a raw key.
     */
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
    // Constants rather than literals because the self-test enumerates them: the bug these exist for is a
    // reason whose key has no translation, which shows a player a raw key.

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

    /**
     * @return the pattern, or {@code null} when the pattern does not encode an arcane recipe
     */
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
            // Rebuild without spending the dirty flag: a rebuild asks the knowledge core, which cannot be read
            // without a level, and one that ran too early produced an empty list that clearing the flag made
            // final. Left for the callers that know the list is real to settle.
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
        crafting = true;
        lastRefusal = null;
        lastWait = null;
        craftTicks = 0;
        // A fresh job starts with a clean slate, or it would inherit the previous one's stall count.
        stalledTicks = 0;
        currentPattern = pattern;
        // Fixed here rather than worked out again at completion: this is what lets the craft finish after
        // a save even if the core that named the pattern is gone.
        craftPrice = craftCost(pattern);
        craftCrystals = crystalStacksOf(pattern);
        suppressNotify = true;
        try {
            inventory.setItem(TARGET_SLOT, pattern.result().copy());
            // And the grid it is made from, which the GUI draws: written here rather than derived on the
            // client, because the running craft exists only on this side.
            for (int i = 0; i < PREVIEW_SLOT_COUNT; i++) {
                ItemStack cell = i < pattern.grid().size() ? pattern.grid().get(i) : ItemStack.EMPTY;
                inventory.setItem(PREVIEW_SLOT_START + i, cell.isEmpty() ? ItemStack.EMPTY : cell.copy());
            }
        } finally {
            suppressNotify = false;
        }
        setChanged();
        markForUpdate();
        // Wake the grid, or this craft never runs. requestUpdate only tells AE2 the pattern list changed;
        // waking is the tick manager's job, and tickingRequest answers IDLE whenever there is nothing to do.
        // Measured: a craft pushed while asleep was ticked once a second and took twenty seconds, and the
        // next one never finished at all.
        ICraftingProvider.requestUpdate(mainNode);
        updateSleepiness();
        return true;
    }

    // ------------------------------------------------------------------
    // Pattern cache
    // ------------------------------------------------------------------

    /**
     * Rebuilds the advertised pattern set from the knowledge core in the core slot: what the network can craft
     * is what the player has encoded. Reading the live recipe manager instead made the core decorative.
     *
     * <p><b>Returns whether the core could actually be consulted, and callers must not clear
     * {@code patternsDirty} unless it could.</b> Reading a core needs {@code level.registryAccess()}, which a
     * block entity does not have while its tag is loading; a rebuild there produced an empty list that
     * clearing the flag made final.
     *
     * @return {@code true} when the core was readable and the cache is therefore complete
     */
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
            // The core holds entries this build cannot read. They are kept in the item - see
            // HandlerKnowledgeCore - but they are not patterns this machine can offer, and a "core that
            // reads as empty" is otherwise indistinguishable from a core whose patterns were deleted.
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
        crafting = tag.getBoolean("Crafting");
        craftTicks = tag.getInt("CraftTicks");
        speedUpgrades = Math.clamp(tag.getInt("SpeedUpgrades"), 0, MAX_SPEED_UPGRADES);
        bufferedVis = tag.getInt("BufferedVis");
        int[] savedAspects = tag.getIntArray("AspectVis");
        if (savedAspects.length == aspectVis.length) {
            System.arraycopy(savedAspects, 0, aspectVis, 0, aspectVis.length);
            // A hand-edited tag must not leave six bars disagreeing with the pool.
            reconcileAspectVis();
        } else {
            // Written before the bars were split, so there is no split to restore: spread the pool evenly.
            spreadEvenly(bufferedVis);
        }
        craftPrice = tag.getInt(TAG_CRAFT_PRICE);
        craftCrystals = readCrystalStacks(tag, registries);
        suppressNotify = true;
        try {
            ContainerHelper.loadAllItems(tag, inventory.getItems(), registries);
        } finally {
            suppressNotify = false;
        }
        recalculateGearDiscount();
        patternsDirty = true;
        // Only the halves that need no world happen here; the interrupted craft is recovered in onLoad.
        // Moving it there also closed a defect: onDataPacket's default implementation is this method, so the
        // recovery used to run on the client against a tag with no inventory and wipe the craft state.
        if (STATE_TRACE) {
            // Everything about the node in this dump is a lie: the node is created after this method runs.
            dumpState("loaded");
        }
    }

    /**
     * Finishes recovering a craft that a save interrupted, once there is a level to read the core with.
     *
     * <p><b>Not done in {@code loadAdditional}, which runs before the block entity has a level.</b> Minecraft
     * loads a block entity out of its tag and only then calls {@code setLevel}, so there the core cannot be
     * read and the pattern for the product in the well cannot be found.
     */
    private void recoverInterruptedCraft() {
        if (level == null || level.isClientSide()) {
            return;
        }
        // Driven by the well, not the saved flag: a product in the target well is a craft that did not finish,
        // since beginCraft puts it there and finishCraft is the only thing that takes it away.
        ItemStack waiting = inventory.getItem(TARGET_SLOT);
        if (!waiting.isEmpty()) {
            crafting = true;
            // The pattern is recovered because it is what the preview grid is drawn from. Finishing must not
            // depend on it: the price and crystals were saved with the craft and the product is in the well.
            currentPattern = patternForResult(waiting);
            if (currentPattern != null) {
                // A readable pattern restates both numbers; the saved ones are the fallback.
                craftPrice = craftCost(currentPattern);
                craftCrystals = crystalStacksOf(currentPattern);
            }
            ThaumicEnergistics.LOG.info(
                    "[assembler] at {} resumed the craft a save interrupted: {} for {} vis{}",
                    worldPosition,
                    waiting.getHoverName().getString(),
                    craftPrice,
                    currentPattern == null ? " (the knowledge core no longer has its pattern)" : "");
            // Deliver on the first tick: the crafting time was served before the save.
            craftTicks = ticksPerCraft();
            stalledTicks = 0;
        } else {
            crafting = false;
            craftTicks = 0;
            craftPrice = 0;
            craftCrystals = List.of();
            clearDisplay(true);
        }
        // Ask to be ticked, and do not assume a later grid event will. This is safe this early: it returns
        // without doing anything while there is no grid yet, and the state change does it then.
        updateSleepiness();
    }

    /**
     * Empties the running craft's display: its product well and its ingredient grid, and nothing else - the
     * core, cards and gear are the player's, and the pattern mirror is derived from the core.
     *
     * @param report whether finding something to clear is worth a line: from the load path, an idle machine
     *     still drawing a craft is the bug.
     */
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

    /** How much vis this machine banks. Exposed so the tooltip and the machine cannot disagree about it. */
    public static int visBufferTarget() {
        return VIS_BUFFER_TARGET;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        tag.putBoolean("Crafting", crafting);
        tag.putInt("CraftTicks", craftTicks);
        tag.putInt("SpeedUpgrades", speedUpgrades);
        tag.putInt("BufferedVis", bufferedVis);
        // The aspect split rides with the pool it breaks down, so a reloaded machine draws the same six bars.
        tag.putIntArray("AspectVis", aspectVis);
        // Saved with the craft, so finishing it after a reload needs nothing but this tag and the well.
        tag.putInt(TAG_CRAFT_PRICE, craftPrice);
        ListTag crystals = new ListTag();
        for (ItemStack stack : craftCrystals) {
            crystals.add(stack.save(registries));
        }
        tag.put(TAG_CRAFT_CRYSTALS, crystals);
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
    }

    /** Reads back what {@code saveAdditional} wrote for the crystals a running craft still owes. */
    private static List<ItemStack> readCrystalStacks(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag saved = tag.getList(TAG_CRAFT_CRYSTALS, Tag.TAG_COMPOUND);
        if (saved.isEmpty()) {
            return List.of();
        }
        List<ItemStack> stacks = new ArrayList<>(saved.size());
        for (int i = 0; i < saved.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, saved.getCompound(i));
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return List.copyOf(stacks);
    }

    /**
     * Client sync payload: the craft state, the vis pool and its aspect split, and the product of a running
     * craft - never the rest of the inventory, which the menu owns. The product travels as a serialised copy
     * of the well, so the client cannot receive the server's stack.
     *
     * <p>This tag is sent every tick while a craft runs, so the product is only written into the two ends of
     * a craft and once a second in between. Serialising an ItemStack into a per-tick tag, once per watching
     * player, is work thrown away; the heartbeat is what lets a player who arrives mid-craft see the item.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("Crafting", crafting);
        tag.putInt("CraftTicks", craftTicks);
        tag.putInt("BufferedVis", bufferedVis);
        tag.putIntArray("AspectVis", aspectVis);
        tag.putInt("GearDiscount", gearDiscount);
        if (!crafting || craftTicks == 0 || craftTicks % 100 == 0) {
            tag.put("Preview", inventory.getItem(TARGET_SLOT).saveOptional(registries));
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        applySyncedState(tag, registries);
    }

    /**
     * Applies an update tag on the client, which is the route a per-tick update takes. {@code handleUpdateTag}
     * is the chunk-load packet; a {@code ClientboundBlockEntityDataPacket} lands here, and its default
     * implementation ends in {@code loadAdditional}, which used to wipe the craft state on arrival.
     */
    @Override
    public void onDataPacket(
            Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            applySyncedState(tag, registries);
        }
    }

    /** The fields the client's copy carries, and the only ones that are allowed to be set from a tag. */
    private void applySyncedState(CompoundTag tag, HolderLookup.Provider registries) {
        suppressNotify = true;
        try {
            crafting = tag.getBoolean("Crafting");
            craftTicks = tag.getInt("CraftTicks");
            bufferedVis = tag.getInt("BufferedVis");
            int[] syncedAspects = tag.getIntArray("AspectVis");
            if (syncedAspects.length == aspectVis.length) {
                System.arraycopy(syncedAspects, 0, aspectVis, 0, aspectVis.length);
            }
            gearDiscount = tag.getInt("GearDiscount");
            // A display, not an item: putting it into the inventory here would give the client a second
            // stack of what the well already holds. Applied only when the tag carries one, since the product
            // is sent on a slower clock than the rest of this state - an absent key means "unchanged".
            if (tag.contains("Preview")) {
                previewStack = ItemStack.parseOptional(registries, tag.getCompound("Preview"));
            }
        } finally {
            suppressNotify = false;
        }
    }

    /**
     * Sends the display half of the update tag, at most every {@link #DISPLAY_UPDATE_INTERVAL} ticks.
     *
     * <p>A running craft is ticked at the minimum rate and used to send this tag, to every watching player,
     * on every one of those ticks - with a {@code setChanged()} that also keeps the chunk flagged for the
     * next autosave. Four ticks is five updates a second, which the progress column reads as smooth.
     */
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

    /**
     * Sends this block entity's update tag to everyone watching it.
     *
     * <p><b>Deliberately not {@code level.sendBlockUpdated}:</b> on 1.21 that calls
     * {@code ServerChunkCache.blockChanged} and sends no block entity packet at all.
     */
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
