package thaumicenergistics.blockentity;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.content.infusion.BlockEntityInfusionMatrix;
import com.leclowndu93150.thaumaturge.content.infusion.BlockEntityPedestal;
import com.leclowndu93150.thaumaturge.content.infusion.InfusionRecipe;
import com.leclowndu93150.thaumaturge.content.infusion.InfusionStabilitySurvey;
import com.leclowndu93150.thaumaturge.registry.TCItems;
import com.leclowndu93150.thaumaturge.registry.TCRecipeTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics.block.BlockInfusionMonitor;
import thaumicenergistics.block.ThEBaseBlockEntity;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.infusion.InfusionRisk;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;

/**
 * The Infusion Monitor: watches an Infusion Altar and reports what the ritual will do to the room.
 *
 * <p>It scans for the altar, reads what the altar holds and still needs, and asks Thaumaturge what is
 * wrong - {@link InfusionStabilitySurvey} names the blocks breaking the altar's symmetry, which is a
 * specific, fixable problem rather than a number to worry about.
 *
 * <p>The Thaumonomicon is the key: without it the monitor still connects and watches but reports nothing.
 */
public class BlockEntityInfusionMonitor extends AENetworkedBlockEntity implements IGridTickable {

    public static final int BOOK_SLOT = 0;

    /** The network cost of watching. High, because watching an altar is the whole function. */
    private static final double IDLE_POWER = 40.0;

    /**
     * How far the monitor looks for an altar, in blocks. Twelve reaches the matrix from anywhere in the
     * altar's footprint, and is small enough not to find the neighbouring altar in a room with two.
     */
    private static final int ALTAR_SCAN_RANGE = 12;

    private static final int SCAN_INTERVAL = 10;

    /** Ticks before a fruitless altar search is repeated: the cube below is 15,625 block entity lookups. */
    private static final int ALTAR_MISS_INTERVAL = 100;

    /** Ticks between stability surveys. Blocks out of place change only when a player builds something. */
    private static final int SURVEY_INTERVAL = 40;

    /** How long a catalyst's recipe is remembered. Short, so a datapack reload cannot leave it stale. */
    private static final int RECIPE_CACHE_TICKS = 40;



    private final SimpleContainer inventory = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            BlockEntityInfusionMonitor.this.setChanged();
            BlockEntityInfusionMonitor.this.updateBookState();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.is(TCItems.THAUMONOMICON.get());
        }
    };

    /** The altar being watched, or {@code null} when none was found. */
    private @Nullable BlockPos matrixPos;

    /** Game time before which the altar cube is not searched again, and before which the survey is not re-run. */
    private long nextCubeScan;
    private long nextSurvey;

    /** How long the next fruitless search waits. Doubles per miss, so a new altar is found within a second. */
    private int altarMissBackoff = SCAN_INTERVAL;

    /** The last survey's blocks out of place, and the altar it was taken at. */
    private List<BlockPos> surveyedProblems = List.of();
    private @Nullable BlockPos surveyedAt;

    /** The last catalyst asked about, the recipe it starts, and when that was worked out. */
    private ItemStack cachedCatalyst = ItemStack.EMPTY;
    private @Nullable InfusionRecipe cachedRecipe;
    private long cachedRecipeAt;

    /** Everything read from the altar on the last scan. Kept for the tooltip, which reads it. */
    private Report report = Report.NONE;

    /** The altar's risk as of the last scan: what the recipe costs plus what the room costs. */
    private InfusionRisk risk = InfusionRisk.NONE;

    /**
     * Whether to report what the monitor sees, once a second; off unless
     * {@code THAUMICENERGISTICS_MONITOR_TRACE=true}. No grid, no power, no book and no altar otherwise look
     * alike.
     */
    private static final boolean TRACE =
            "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_MONITOR_TRACE"));

    private long nextTrace;

    // The bubble is drawn on the client, so the numbers it needs travel in the update tag: the tier, the
    // number behind it, and whether the monitor has anything to say at all.

    private boolean bubbleReporting;
    private int bubbleTier = 1;
    private int bubbleInstability;
    /** The altar's stability at the last scan, times ten - the number the bubble leads with. */
    private int bubbleStabilityTimesTen = 250;
    private boolean bubbleCrafting;
    private ItemStack bubbleCraft = ItemStack.EMPTY;
    /** What the altar still wants and what it can reach, one line per aspect. See {@link EssentiaLine}. */
    private final List<EssentiaLine> bubbleEssentia = new ArrayList<>();
    private final List<EssentiaLine> essentia = new ArrayList<>();

    /**
     * How far the monitor looks for the containers an altar can draw from; twelve blocks is
     * {@code EssentiaSources}' own range, so nothing counted is out of the ritual's reach.
     */
    private static final int SOURCE_RANGE = 12;

    /** The containers found around the altar, and when they were last looked for. See {@link #shortOf}. */
    private final List<BlockPos> sourceCache = new ArrayList<>();
    private long nextSourceScan;
    private ItemStack craftDisplay = ItemStack.EMPTY;

    /** What was last sent, so an unchanged reading does not send a packet every scan. */
    private boolean syncedReporting;
    private int syncedTier = -1;
    private int syncedInstability = -1;
    /** Everything else the bubble draws, as one string, so a change in any of it is a change to send. */
    private String syncedDetail = "";

    public BlockEntityInfusionMonitor(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFUSION_MONITOR.get(), pos, state);
        // One channel, like every other machine on the network: without this flag the monitor is on the
        // grid but invisible to channel readings, which is what "it is not in my ME network" means in
        // practice. The vibration chamber deliberately has none - a generator that needed a channel could
        // not wake a network with no channel to give it.
        getMainNode()
                .setIdlePowerUsage(IDLE_POWER)
                .addService(IGridTickable.class, this)
                .setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    // The book

    public SimpleContainer getInventory() {
        return inventory;
    }

    public ItemStack getBook() {
        return inventory.getItem(BOOK_SLOT);
    }

    /** Whether a Thaumonomicon is in the slot. Without one the monitor reports nothing. */
    public boolean hasBook() {
        return getBook().is(TCItems.THAUMONOMICON.get());
    }

    /**
     * Puts the book in, or - only when the player sneaks - takes it back out: placing is what a player does
     * with a Thaumonomicon in hand, while removing it disarms the machine. Before {@code sneaking} existed a
     * plain right-click removed the book. See {@code BlockInfusionMonitor}.
     */
    public @Nullable ItemStack interact(ItemStack held, boolean sneaking) {
        if (hasBook()) {
            if (!sneaking || !held.isEmpty()) {
                return null;
            }
            ItemStack removed = getBook().copy();
            inventory.setItem(BOOK_SLOT, ItemStack.EMPTY);
            return removed;
        }
        if (held.isEmpty() || !held.is(TCItems.THAUMONOMICON.get())) {
            return null;
        }
        ItemStack placed = held.copyWithCount(1);
        held.shrink(1);
        inventory.setItem(BOOK_SLOT, placed);
        return null;
    }

    /** Mirrors the book into the blockstate, which is what selects between the three models. */
    private void updateBookState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(BlockInfusionMonitor.BOOK)) {
            return;
        }
        boolean has = hasBook();
        if (state.getValue(BlockInfusionMonitor.BOOK) != has) {
            level.setBlock(worldPosition, state.setValue(BlockInfusionMonitor.BOOK, has), 3);
        }
    }

    /** Mirrors the grid connection into the blockstate, which is what lights the model up. */
    public void updateNetworkState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(BlockInfusionMonitor.NETWORK)) {
            return;
        }
        boolean online = getMainNode().isActive();
        if (state.getValue(BlockInfusionMonitor.NETWORK) != online) {
            level.setBlock(worldPosition, state.setValue(BlockInfusionMonitor.NETWORK, online), 3);
        }
    }

    // Watching the altar

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(SCAN_INTERVAL, SCAN_INTERVAL, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        if (level == null || level.isClientSide()) {
            return TickRateModulation.IDLE;
        }
        updateNetworkState();
        scanAltar();
        syncBubble();
        trace(node);
        return TickRateModulation.SAME;
    }

    /**
     * Finds the altar and reads it. The scan is a cube, because a matrix's position is not derivable from
     * the monitor's own - a player can put the monitor anywhere near the altar.
     */
    private void scanAltar() {
        if (level == null) {
            return;
        }
        // An altar already found is re-checked directly, rather than searching twelve blocks twice a second.
        if (matrixPos != null && level.getBlockEntity(matrixPos) instanceof BlockEntityInfusionMatrix matrix) {
            report = read(matrix, matrixPos);
            return;
        }
        matrixPos = null;

        // A fruitless search waits: with no altar in the cube there is nothing to find, and this used to
        // spend fifteen thousand lookups twice a second on saying so.
        long now = level.getGameTime();
        if (now < nextCubeScan) {
            report = Report.NONE;
            return;
        }

        for (BlockPos pos : BlockPos.betweenClosed(
                worldPosition.offset(-ALTAR_SCAN_RANGE, -ALTAR_SCAN_RANGE, -ALTAR_SCAN_RANGE),
                worldPosition.offset(ALTAR_SCAN_RANGE, ALTAR_SCAN_RANGE, ALTAR_SCAN_RANGE))) {
            if (level.getBlockEntity(pos) instanceof BlockEntityInfusionMatrix matrix) {
                matrixPos = pos.immutable();
                altarMissBackoff = SCAN_INTERVAL;
                report = read(matrix, matrixPos);
                return;
            }
        }
        nextCubeScan = now + altarMissBackoff;
        altarMissBackoff = Math.min(ALTAR_MISS_INTERVAL, altarMissBackoff * 2);
        report = Report.NONE;
    }

    /**
     * Reads one altar. The survey runs whether or not a ritual is under way, because the blocks out of place
     * are what a player can fix <em>before</em> starting one. The recipe's instability belongs to the
     * catalyst two blocks below the matrix, as in Thaumaturge's altars; the altar's is the survey's count.
     */
    private Report read(BlockEntityInfusionMatrix matrix, BlockPos pos) {
        boolean crafting = matrix.isCrafting();
        float stability = matrix.stability();
        AspectList remaining = matrix.remainingEssentia();

        // The survey reads a seventeen by eleven by seventeen volume for blocks out of place, which is a
        // property of the room rather than of the ritual, so it is re-run every two seconds - and at once
        // when the monitor is looking at a different altar.
        if (level != null) {
            long now = level.getGameTime();
            if (now >= nextSurvey || !pos.equals(surveyedAt)) {
                nextSurvey = now + SURVEY_INTERVAL;
                surveyedAt = pos.immutable();
                var survey = InfusionStabilitySurvey.survey(level, pos);
                if (survey != null) {
                    surveyedProblems = List.copyOf(survey.problemBlocks());
                }
            }
        }
        List<BlockPos> problems = surveyedProblems;
        // A shortage is essentia the altar cannot find, not essentia it has not finished with: asking
        // whether the ritual still wanted anything pinned the tier at four for every whole infusion.
        boolean shortages = crafting && shortOf(matrixPos, remaining);
        risk = new InfusionRisk(readBaseInstability(pos), problems.size(), shortages, stability);

        InfusionRecipe recipe = crafting ? recipeFor(pedestalItem(pos)) : null;
        craftDisplay = crafting ? craftName(pedestalItem(pos), recipe) : ItemStack.EMPTY;
        if (crafting) {
            readEssentia(remaining, recipe);
        } else {
            essentia.clear();
        }
        return new Report(true, crafting, stability, remaining, problems);
    }

    /**
     * The instability the recipe for whatever is on the central pedestal carries, or zero. Research is
     * deliberately ignored: a player looking at an altar stacked for something they have not unlocked yet is
     * exactly the player who needs the warning.
     */
    private int readBaseInstability(BlockPos matrixPos) {
        if (level == null || !(level.getBlockEntity(matrixPos.below(2)) instanceof BlockEntityPedestal pedestal)) {
            return 0;
        }
        ItemStack catalyst = pedestal.getItem();
        if (catalyst.isEmpty()) {
            return 0;
        }
        InfusionRecipe recipe = recipeFor(catalyst);
        return recipe == null ? 0 : recipe.instability();
    }

    public InfusionRisk risk() {
        return risk;
    }

    /**
     * Sends the bubble's numbers to the client, only when one of them changed - the alternative is a block
     * update twice a second for a machine that spends most of its life saying the same thing.
     */
    private void syncBubble() {
        boolean reporting = canReport();
        int tier = risk.tier();
        int instability = risk.instability();
        boolean crafting = report.crafting();
        // The stability moves in fractions of a point during a craft, so it is part of the signature; else
        // the bubble would sit on the reading it was given when the ritual started.
        String signature = crafting + "|" + craftDisplay.getItem() + "|" + essentia + "|"
                + Math.round(risk.stability() * 10.0F);
        if (reporting == syncedReporting
                && tier == syncedTier
                && instability == syncedInstability
                && signature.equals(syncedDetail)) {
            return;
        }
        syncedReporting = reporting;
        syncedTier = tier;
        syncedInstability = instability;
        syncedDetail = signature;
        bubbleCrafting = crafting;
        bubbleCraft = craftDisplay;
        bubbleEssentia.clear();
        bubbleEssentia.addAll(essentia);
        // Sent to the players watching this chunk by hand. AE2's markForClientUpdate goes through
        // level.sendBlockUpdated, which on 1.21 sends no block entity packet at all - so the live bubble
        // update reached nobody, and the numbers only ever arrived with a chunk send.
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet =
                    net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
            for (net.minecraft.server.level.ServerPlayer player : server.getChunkSource().chunkMap
                    .getPlayers(new net.minecraft.world.level.ChunkPos(worldPosition), false)) {
                player.connection.send(packet);
            }
        }
    }

    /** Whether the bubble should be drawn at all: the book is on and an altar was found. */
    public boolean bubbleReporting() {
        return bubbleReporting;
    }

    /** The risk tier the bubble shows, 1 to 5. */
    public int bubbleTier() {
        return bubbleTier;
    }

    public int bubbleInstability() {
        return bubbleInstability;
    }

    /** The altar's live stability, to one decimal, as the bubble shows it. */
    public String bubbleStability() {
        return String.format("%.1f", bubbleStabilityTimesTen / 10.0F);
    }

    public boolean bubbleCrafting() {
        return bubbleCrafting;
    }

    public ItemStack bubbleCraft() {
        return bubbleCraft;
    }

    public List<EssentiaLine> bubbleEssentia() {
        return List.copyOf(bubbleEssentia);
    }

    /** One diagnostic line a second. See {@link #TRACE}. */
    private void trace(IGridNode node) {
        if (!TRACE || level == null || level.isClientSide()) {
            return;
        }
        long now = level.getGameTime();
        if (now < nextTrace) {
            return;
        }
        nextTrace = now + 20;
        thaumicenergistics.ThaumicEnergistics.LOG.info(
                "[mon] at {} node={} book={} network={} altar={} crafting={} problems={} base={} altarRisk={}"
                        + " tier={} stability={} stored={} energyOutput={}",
                worldPosition, describeNode(node), hasBook(), report.foundAltar(), matrixPos, report.crafting(),
                report.symmetryProblems(), risk.base(), risk.altar(), risk.tier(),
                String.format("%.1f", risk.stability()), report.remainingKinds(), risk.instability());
    }

    /**
     * The node's state as one word, for the trace: "not active" covers four faults with four different fixes.
     */
    private String describeNode(IGridNode node) {
        if (node == null) {
            return "none";
        }
        if (node.getGrid() == null) {
            return "no-grid";
        }
        if (!node.isPowered()) {
            return "unpowered";
        }
        if (!node.hasGridBooted()) {
            return "booting";
        }
        if (!node.meetsChannelRequirements()) {
            return "no-channel";
        }
        return "active";
    }

    // The bubble's half of the sync

    /**
     * Client sync payload for the bubble: the numbers it draws, never the book, which travels as a blockstate.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("Reporting", canReport());
        tag.putInt("BubbleTier", risk.tier());
        tag.putInt("BubbleInstability", risk.instability());
        tag.putInt("BubbleStability", Math.round(risk.stability() * 10.0F));
        tag.putBoolean("BubbleCrafting", bubbleCrafting);
        tag.put("BubbleCraft", bubbleCraft.saveOptional(registries));
        net.minecraft.nbt.ListTag lines = new net.minecraft.nbt.ListTag();
        for (EssentiaLine line : bubbleEssentia) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Aspect", line.aspect());
            entry.putInt("Drawn", line.drawn());
            entry.putInt("Total", line.total());
            lines.add(entry);
        }
        tag.put("BubbleEssentia", lines);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        applyBubbleState(tag, registries);
    }

    /**
     * Applies an update tag on the client, which is the route a live update takes; {@code handleUpdateTag} is
     * the chunk-load route. Both end here rather than in the persistence path, which would load the inventory.
     */
    @Override
    public void onDataPacket(
            net.minecraft.network.Connection net,
            net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
            HolderLookup.Provider registries) {
        super.onDataPacket(net, packet, registries);
        applyBubbleState(packet.getTag(), registries);
    }

    /** The three numbers the bubble draws, taken off a tag. Absent keys mean "nothing to say". */
    private void applyBubbleState(CompoundTag tag, HolderLookup.Provider registries) {
        if (level != null && level.isClientSide() && TRACE) {
            thaumicenergistics.ThaumicEnergistics.LOG.info(
                    "[bubble] tag at {} reporting={} tier={} instability={}",
                    worldPosition, tag.getBoolean("Reporting"), tag.getInt("BubbleTier"),
                    tag.getInt("BubbleInstability"));
        }
        bubbleReporting = tag.getBoolean("Reporting");
        bubbleTier = Math.max(1, Math.min(InfusionRisk.MAX_TIER, tag.getInt("BubbleTier")));
        bubbleInstability = tag.getInt("BubbleInstability");
        bubbleStabilityTimesTen = tag.getInt("BubbleStability");
        bubbleCrafting = tag.getBoolean("BubbleCrafting");
        bubbleCraft = ItemStack.parseOptional(registries, tag.getCompound("BubbleCraft"));
        bubbleEssentia.clear();
        net.minecraft.nbt.ListTag lines = tag.getList("BubbleEssentia", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < lines.size(); i++) {
            CompoundTag entry = lines.getCompound(i);
            bubbleEssentia.add(new EssentiaLine(
                    entry.getString("Aspect"), entry.getInt("Drawn"), entry.getInt("Total")));
        }
    }

    /**
     * Whether the altar cannot reach what the ritual still wants, asked through
     * {@code IAspectSource.containerContains}; counting {@code getAspects} would miss this mod's provider,
     * which reports itself empty on purpose.
     */
    private boolean shortOf(BlockPos matrixPos, @Nullable AspectList remaining) {
        if (remaining == null || remaining.isEmpty() || level == null) {
            return false;
        }
        // Resolved once for the whole check, not once per (aspect, position) pair as it used to be: when the
        // source is this mod's Infusion Provider, every containerContains walks the ME network, so asking it
        // six times an aspect was six walks of the network twice a second.
        List<com.leclowndu93150.thaumaturge.api.aspect.IAspectSource> sources =
                new ArrayList<>(sourcesAround(matrixPos).size());
        for (BlockPos sourcePos : sourcesAround(matrixPos)) {
            if (level.getCapability(
                            com.leclowndu93150.thaumaturge.api.aspect.AspectCapabilities.CONTAINER,
                            sourcePos,
                            null)
                    instanceof com.leclowndu93150.thaumaturge.api.aspect.IAspectSource source
                    && !source.isBlocked()) {
                sources.add(source);
            }
        }
        for (AspectInstance entry : remaining.entries()) {
            if (entry.amount() <= 0) {
                continue;
            }
            int reachable = 0;
            for (var source : sources) {
                reachable += source.containerContains(entry.aspect());
                if (reachable >= entry.amount()) {
                    // Enough: the rest would be asked for nothing.
                    break;
                }
            }
            if (reachable < entry.amount()) {
                return true;
            }
        }
        return false;
    }

    /** The containers within the altar's own reach, rescanned at most once a second. */
    private List<BlockPos> sourcesAround(BlockPos matrixPos) {
        if (level == null) {
            return List.of();
        }
        long now = level.getGameTime();
        // The empty result is cached too: "no containers in range" is the common case, and it used to defeat
        // the cache and re-run the whole cube on every scan.
        if (now < nextSourceScan) {
            return sourceCache;
        }
        nextSourceScan = now + 20;
        sourceCache.clear();
        for (BlockPos pos : BlockPos.betweenClosed(
                matrixPos.offset(-SOURCE_RANGE, -SOURCE_RANGE, -SOURCE_RANGE),
                matrixPos.offset(SOURCE_RANGE, SOURCE_RANGE, SOURCE_RANGE))) {
            if (level.getCapability(
                            com.leclowndu93150.thaumaturge.api.aspect.AspectCapabilities.CONTAINER, pos, null)
                    != null) {
                sourceCache.add(pos.immutable());
            }
        }
        return sourceCache;
    }

    /**
     * What the running ritual is making, named from outside the matrix: the recipe's result, else the catalyst.
     */
    private ItemStack craftName(ItemStack catalyst, @Nullable InfusionRecipe recipe) {
        if (catalyst.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return recipe == null ? catalyst : recipe.resultItem();
    }

    private ItemStack pedestalItem(BlockPos matrixPos) {
        if (level == null || !(level.getBlockEntity(matrixPos.below(2)) instanceof BlockEntityPedestal pedestal)) {
            return ItemStack.EMPTY;
        }
        return pedestal.getItem();
    }

    /**
     * How far along each of the ritual's aspects is. Both numbers come from the job, not a scan of the room:
     * counting the surrounding containers falls as the altar drains them. An unknown recipe reports 0 / n.
     */
    private void readEssentia(AspectList remaining, @Nullable InfusionRecipe recipe) {
        essentia.clear();
        AspectList total = recipe == null ? remaining : recipe.aspects();
        if (total == null || total.isEmpty()) {
            return;
        }
        for (AspectInstance entry : total.entries()) {
            if (entry.amount() <= 0) {
                continue;
            }
            int wanted = entry.amount();
            int left = remaining == null ? 0 : remaining.amountOf(entry.aspect());
            ResourceLocation id = entry.aspect().unwrapKey().map(key -> key.location()).orElse(null);
            if (id != null) {
                // The whole id, not the path: the client has to be able to resolve it, and an aspect from
                // another namespace would come back as a different aspect - or as nothing at all.
                essentia.add(new EssentiaLine(id.toString(), Math.max(0, wanted - left), wanted));
            }
        }
    }

    /** The recipe a catalyst starts, ignoring research. See {@link #readBaseInstability}. */
    private @Nullable InfusionRecipe recipeFor(ItemStack catalyst) {
        if (level == null || catalyst.isEmpty()) {
            return null;
        }
        // Walked linearly, and asked twice per scan for the same catalyst, so the answer is remembered for
        // a couple of seconds - short enough that a datapack reload cannot leave it stale for long.
        long now = level.getGameTime();
        if (now - cachedRecipeAt <= RECIPE_CACHE_TICKS
                && ItemStack.isSameItemSameComponents(cachedCatalyst, catalyst)) {
            return cachedRecipe;
        }
        for (var holder : level.getRecipeManager().getAllRecipesFor(TCRecipeTypes.INFUSION.get())) {
            InfusionRecipe recipe = holder.value();
            if (recipe.catalyst().test(catalyst)) {
                cachedCatalyst = catalyst.copy();
                cachedRecipe = recipe;
                cachedRecipeAt = now;
                return recipe;
            }
        }
        cachedCatalyst = ItemStack.EMPTY;
        cachedRecipe = null;
        return null;
    }

    /** One aspect of the running ritual: how much has gone in, and how much the recipe wants in total. */
    public record EssentiaLine(String aspect, int drawn, int total) {
        @Override
        public String toString() {
            return aspect + "=" + drawn + "/" + total;
        }
    }

    public Report report() {
        return report;
    }

    public boolean canReport() {
        return hasBook() && report.foundAltar();
    }

    // Persistence

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Book", inventory.getItem(BOOK_SLOT).saveOptional(registries));
        if (matrixPos != null) {
            tag.putLong("MatrixPos", matrixPos.asLong());
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        inventory.setItem(BOOK_SLOT, ItemStack.parseOptional(registries, tag.getCompound("Book")));
        matrixPos = tag.contains("MatrixPos") ? BlockPos.of(tag.getLong("MatrixPos")) : null;
    }

    public void dropContents() {
        if (level == null) {
            return;
        }
        ItemStack book = inventory.getItem(BOOK_SLOT);
        if (!book.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(
                    level,
                    worldPosition.getX() + 0.5,
                    worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5,
                    book);
            inventory.setItem(BOOK_SLOT, ItemStack.EMPTY);
        }
    }

    /** One reading of an altar: found or not, crafting or not, and what the survey made of it. */
    public record Report(
            boolean foundAltar,
            boolean crafting,
            float stability,
            AspectList remaining,
            List<BlockPos> problemBlocks) {

        public static final Report NONE = new Report(false, false, 0.0F, AspectList.EMPTY, List.of());

        /** How many blocks are out of place. Zero while crafting is the good answer. */
        public int symmetryProblems() {
            return problemBlocks.size();
        }

        public int remainingKinds() {
            return remaining == null ? 0 : remaining.size();
        }
    }
}
