package thaumicenergistics_ce.blockentity;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.leclowndu93150.thaumaturge.api.aspect.AspectCapabilities;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspectSource;
import com.leclowndu93150.thaumaturge.content.infusion.BlockEntityInfusionMatrix;
import com.leclowndu93150.thaumaturge.content.infusion.BlockEntityPedestal;
import com.leclowndu93150.thaumaturge.content.infusion.InfusionRecipe;
import com.leclowndu93150.thaumaturge.content.infusion.InfusionStabilitySurvey;
import com.leclowndu93150.thaumaturge.registry.TCRecipeTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.block.BlockInfusionMonitor;
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.infusion.InfusionRisk;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;

/**
 * The Infusion Monitor: watches an Infusion Altar and reports what the ritual will do to the room.
 * <ul>
 *   <li>{@link InfusionStabilitySurvey} names the blocks that break the altar's symmetry.
 *   <li>A Thaumonomicon must be in the book slot, or {@link #canReport()} stays false.
 * </ul>
 */
public class BlockEntityInfusionMonitor extends AENetworkedBlockEntity implements IGridTickable {

    public static final int BOOK_SLOT = 0;

    /** Idle draw of the watch, whether or not an altar is in range: 256 AE per tick. */
    private static final double IDLE_POWER = 256.0;

    /** Altar search radius: twelve covers the altar's footprint but not the next altar. */
    private static final int ALTAR_SCAN_RANGE = 12;

    private static final int SCAN_INTERVAL = 10;

    /** Retry delay after a fruitless search: the cube below is 15,625 block entity lookups. */
    private static final int ALTAR_MISS_INTERVAL = 100;

    /** Ticks between stability surveys; blocks out of place change only when a player builds. */
    private static final int SURVEY_INTERVAL = 40;

    /** Recipe cache lifetime; short so a datapack reload cannot leave it stale. */
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
            return TcRegistry.isThaumonomicon(stack);
        }
    };

    /** The altar being watched, or {@code null} when none was found. */
    private @Nullable BlockPos matrixPos;

    /** Game time before which the altar cube is not searched again. */
    private long nextCubeScan;
    /** Game time before which the survey is not re-run. */
    private long nextSurvey;

    /** Wait before the next search; doubles per miss, so a new altar is found within a second. */
    private int altarMissBackoff = SCAN_INTERVAL;

    /** Last survey's blocks out of place. */
    private List<BlockPos> surveyedProblems = List.of();
    /** The altar {@link #surveyedProblems} was taken at. */
    private @Nullable BlockPos surveyedAt;

    /** The last catalyst asked about, the recipe it starts, and when that was worked out. */
    private ItemStack cachedCatalyst = ItemStack.EMPTY;
    private @Nullable InfusionRecipe cachedRecipe;
    private long cachedRecipeAt;

    /** Everything read from the altar on the last scan; kept for the tooltip, which reads it. */
    private Report report = Report.NONE;

    /** Risk as of the last scan: what the recipe costs plus what the room costs. */
    private InfusionRisk risk = InfusionRisk.NONE;

    /** One log line a second when {@code THAUMICENERGISTICS_MONITOR_TRACE=true}, because the failure
     * modes - no grid, no power, no book, no altar - otherwise look alike. */
    private static final boolean TRACE =
            "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_MONITOR_TRACE"));

    private long nextTrace;

    // The bubble is drawn on the client, so its numbers travel in the update tag.

    private boolean bubbleReporting;
    private int bubbleTier = 1;
    private int bubbleInstability;
    /** The altar's stability at the last scan, times ten - the number the bubble leads with. */
    private int bubbleStabilityTimesTen = 250;
    private boolean bubbleCrafting;
    private ItemStack bubbleCraft = ItemStack.EMPTY;
    /** What the altar still wants and can reach, one entry per aspect. See {@link EssentiaLine}. */
    private final List<EssentiaLine> bubbleEssentia = new ArrayList<>();
    private final List<EssentiaLine> essentia = new ArrayList<>();

    /** Search radius around an altar: twelve, {@code EssentiaSources}' own container range. */
    private static final int SOURCE_RANGE = 12;

    /** Containers found around the altar; also when they were last looked for. See {@link #shortOf}. */
    private final List<BlockPos> sourceCache = new ArrayList<>();
    private long nextSourceScan;
    private ItemStack craftDisplay = ItemStack.EMPTY;

    /** What was last sent, so an unchanged reading does not send a packet every scan. */
    private boolean syncedReporting;
    private int syncedTier = -1;
    private int syncedInstability = -1;
    /** Everything else the bubble draws, as one string; any change in it means a packet to send. */
    private String syncedDetail = "";

    public BlockEntityInfusionMonitor(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFUSION_MONITOR.get(), pos, state);
        // REQUIRE_CHANNEL so the monitor shows up in channel readings, as every other machine does.
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
        return TcRegistry.isThaumonomicon(getBook());
    }

    /** Adds the book, or removes it only when the player sneaks - a plain right-click would disarm
     * the machine. See {@code BlockInfusionMonitor}. */
    public @Nullable ItemStack interact(ItemStack held, boolean sneaking) {
        if (hasBook()) {
            if (!sneaking || !held.isEmpty()) {
                return null;
            }
            ItemStack removed = getBook().copy();
            inventory.setItem(BOOK_SLOT, ItemStack.EMPTY);
            return removed;
        }
        if (held.isEmpty() || !TcRegistry.isThaumonomicon(held)) {
            return null;
        }
        ItemStack placed = held.copyWithCount(1);
        held.shrink(1);
        inventory.setItem(BOOK_SLOT, placed);
        return null;
    }

    /** Mirrors the book into the blockstate, which selects between the three models. */
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
        // Offline: skip work, but keep ticking (SAME) so the grid's return is noticed.
        if (!getMainNode().isActive()) {
            syncBubble();
            trace(node);
            return TickRateModulation.SAME;
        }
        scanAltar();
        syncBubble();
        trace(node);
        return TickRateModulation.SAME;
    }

    /** Finds and reads the altar; a cube scan, since the matrix's offset is arbitrary. */
    private void scanAltar() {
        if (level == null) {
            return;
        }
        // A found altar is re-checked directly, not by searching twelve blocks twice a second.
        if (matrixPos != null && level.getBlockEntity(matrixPos) instanceof BlockEntityInfusionMatrix matrix) {
            report = read(matrix, matrixPos);
            return;
        }
        matrixPos = null;

        // A miss backs off: the cube is 15,625 lookups and finding nothing changes nothing.
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

    /** Reads one altar. The survey runs even between rituals, since blocks out of place are what a
     * player fixes first; the instability read is the catalyst's, two blocks below the matrix. */
    private Report read(BlockEntityInfusionMatrix matrix, BlockPos pos) {
        boolean crafting = matrix.isCrafting();
        float stability = matrix.stability();
        AspectList remaining = matrix.remainingEssentia();

        // The survey describes the room, not the ritual: every two seconds, and at once on a new altar.
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
        // A shortage is essentia the altar cannot find, not essentia it has not finished with.
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

    /** Instability of the catalyst's recipe, or zero. Research is ignored on purpose: the player who
     * has not unlocked the recipe is the one who needs the warning. */
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

    /** Sends the bubble's numbers to the client, but only when one of them changed. */
    private void syncBubble() {
        boolean reporting = canReport();
        int tier = risk.tier();
        int instability = risk.instability();
        boolean crafting = report.crafting();
        // Stability moves during a craft, so it is part of the signature or the bubble would freeze.
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
        // Sent by hand: on 1.21 AE2's markForClientUpdate path sends no block entity packet.
        if (level instanceof ServerLevel server) {
            ClientboundBlockEntityDataPacket packet =
                    ClientboundBlockEntityDataPacket.create(this);
            for (ServerPlayer player : server.getChunkSource().chunkMap
                    .getPlayers(new ChunkPos(worldPosition), false)) {
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
        thaumicenergistics_ce.ThaumicEnergistics.LOG.info(
                "[mon] at {} node={} book={} network={} altar={} crafting={} problems={} base={} altarRisk={}"
                        + " tier={} stability={} stored={} energyOutput={}",
                worldPosition, describeNode(node), hasBook(), report.foundAltar(), matrixPos, report.crafting(),
                report.symmetryProblems(), risk.base(), risk.altar(), risk.tier(),
                String.format("%.1f", risk.stability()), report.remainingKinds(), risk.instability());
    }

    /** The node's state as one word: "not active" would cover four faults with four different fixes. */
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

    /** Client sync payload for the bubble. The book is not here - it travels as a blockstate. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("Reporting", canReport());
        tag.putInt("BubbleTier", risk.tier());
        tag.putInt("BubbleInstability", risk.instability());
        tag.putInt("BubbleStability", Math.round(risk.stability() * 10.0F));
        tag.putBoolean("BubbleCrafting", bubbleCrafting);
        tag.put("BubbleCraft", bubbleCraft.saveOptional(registries));
        ListTag lines = new ListTag();
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

    /** A live update arrives here; {@code handleUpdateTag} is the chunk-load route. Both end here,
     * not in {@code loadTag}, which would load the inventory. */
    @Override
    public void onDataPacket(
            Connection net,
            ClientboundBlockEntityDataPacket packet,
            HolderLookup.Provider registries) {
        super.onDataPacket(net, packet, registries);
        applyBubbleState(packet.getTag(), registries);
    }

    /** The bubble's numbers, taken off a tag; absent keys mean "nothing to say". */
    private void applyBubbleState(CompoundTag tag, HolderLookup.Provider registries) {
        if (level != null && level.isClientSide() && TRACE) {
            thaumicenergistics_ce.ThaumicEnergistics.LOG.info(
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
        ListTag lines = tag.getList("BubbleEssentia", Tag.TAG_COMPOUND);
        for (int i = 0; i < lines.size(); i++) {
            CompoundTag entry = lines.getCompound(i);
            bubbleEssentia.add(new EssentiaLine(
                    entry.getString("Aspect"), entry.getInt("Drawn"), entry.getInt("Total")));
        }
    }

    /** Whether the altar cannot reach what the ritual still wants, asked via
     * {@code IAspectSource.containerContains}; counting {@code getAspects} misses our provider. */
    private boolean shortOf(BlockPos matrixPos, @Nullable AspectList remaining) {
        if (remaining == null || remaining.isEmpty() || level == null) {
            return false;
        }
        // Resolved once here, not per (aspect, source): containerContains walks the ME network.
        List<IAspectSource> sources =
                new ArrayList<>(sourcesAround(matrixPos).size());
        for (BlockPos sourcePos : sourcesAround(matrixPos)) {
            if (level.getCapability(
                            AspectCapabilities.CONTAINER,
                            sourcePos,
                            null)
                    instanceof IAspectSource source
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
        // The empty result is cached too: "no containers in range" is the common case.
        if (now < nextSourceScan) {
            return sourceCache;
        }
        nextSourceScan = now + 20;
        sourceCache.clear();
        for (BlockPos pos : BlockPos.betweenClosed(
                matrixPos.offset(-SOURCE_RANGE, -SOURCE_RANGE, -SOURCE_RANGE),
                matrixPos.offset(SOURCE_RANGE, SOURCE_RANGE, SOURCE_RANGE))) {
            if (level.getCapability(
                            AspectCapabilities.CONTAINER, pos, null)
                    != null) {
                sourceCache.add(pos.immutable());
            }
        }
        return sourceCache;
    }

    /** What the running ritual is making: the recipe's result, else the catalyst. */
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

    /** How far along each ritual aspect is: both numbers come from the job, never a room scan (which
     * drains as the ritual runs). An unknown recipe reports 0 / n. */
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
                // Full id, not the path: the client resolves aspects by namespace too.
                essentia.add(new EssentiaLine(id.toString(), Math.max(0, wanted - left), wanted));
            }
        }
    }

    /** The recipe a catalyst starts, ignoring research. See {@link #readBaseInstability}. */
    private @Nullable InfusionRecipe recipeFor(ItemStack catalyst) {
        if (level == null || catalyst.isEmpty()) {
            return null;
        }
        // Walked linearly and asked twice per scan, so the answer is cached for a couple of seconds.
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

    /** One aspect of the running ritual: how much has gone in of the recipe's total. */
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
        // Online too: a bubble left on screen after the network went down would report a stale reading.
        return hasBook() && report.foundAltar() && getMainNode().isActive();
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
            Containers.dropItemStack(
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
