package thaumicenergistics_ce.blockentity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;

/**
 * The Distillation Encoder: writes "this item distils into that essentia" as an ME processing pattern.
 *
 * <p>A distillation pattern is not a recipe the network can look up - there is nothing in Thaumaturge that
 * says a bone yields two units of victus. It is a statement the player makes: feed the network this item,
 * and it should come back as this much of this aspect. This block is where that statement is written down,
 * by taking an item whose aspect composition the game already knows and choosing one of its aspects.
 *
 * <p>So the encoder reads the item's aspects and offers only those. That restriction is the whole value of
 * the block: a pattern claiming an item distils into an aspect it does not contain would be accepted by
 * AE2, would be crafted on demand, and would quietly invent essentia. Choosing from the item's own
 * composition means a distillation pattern can only ever describe a conversion that is already true.
 *
 * <p>The amount written into the pattern is the amount of that aspect the item actually holds, for the same
 * reason - it is read rather than typed.
 *
 * <p>The pattern is a normal AE processing pattern, so it is crafted by whatever machine handles patterns.
 * It is tagged with the research it belongs to, because distilling is meant to be learned before it can be
 * automated.
 */
public class BlockEntityDistillationEncoder extends ThEBaseBlockEntity {

    /** The item being distilled. A ghost slot on screen: the stack is a template, not a cost. */
    public static final int SLOT_SOURCE = 0;

    /** Blank AE patterns to write into. */
    public static final int SLOT_BLANK = 1;

    /** Where the finished pattern appears. Read-only to the player. */
    public static final int SLOT_ENCODED = 2;

    public static final int SLOT_COUNT = 3;

    /** How many of the item's aspects can be offered at once. Six fits the wells in the screen's art. */
    public static final int MAX_ASPECTS = 6;

    /** The research that gates using a distillation pattern. Matches the 1.12.2 build's key. */
    public static final String REQUIRED_RESEARCH = "DISTILESSENTIA";

    /** The NBT key the pattern carries its research under. */
    private static final String NBT_RESEARCH = "research";

    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public void setChanged() {
            super.setChanged();
            BlockEntityDistillationEncoder.this.setChanged();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            if (slot == SLOT_SOURCE) {
                return true;
            }
            if (slot == SLOT_BLANK) {
                return AEItems.BLANK_PATTERN.is(stack);
            }
            return false;
        }
    };

    /**
     * The aspects of the item in the source slot, in the order they are offered.
     *
     * <p>Cached against the source stack because the screen asks for this every frame and the lookup walks
     * the aspect index. Recomputed only when the item actually changes, which is what
     * {@code sourceFingerprint} tracks.
     */
    private List<Holder<IAspect>> aspects = List.of();

    private ItemStack cachedSource = ItemStack.EMPTY;

    /** The aspect the player picked, as an index into {@link #aspects}; -1 for none. */
    private int selectedAspect = -1;

    public BlockEntityDistillationEncoder(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISTILLATION_ENCODER.get(), pos, state);
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    // ------------------------------------------------------------------
    // Aspects of the source item
    // ------------------------------------------------------------------

    /**
     * The aspects the source item holds, refreshed when the item changes.
     *
     * <p>Empty when the slot is empty or the item has no known composition - an item nobody has indexed
     * cannot be distilled, and offering no choice is the honest answer.
     */
    public List<Holder<IAspect>> availableAspects() {
        ItemStack source = inventory.getItem(SLOT_SOURCE);
        if (!ItemStack.matches(source, cachedSource)) {
            cachedSource = source.copy();
            aspects = source.isEmpty() ? List.of() : readAspects(source);
            // The previous pick referred to the old item's list and means nothing now.
            if (selectedAspect >= aspects.size()) {
                selectedAspect = -1;
            }
        }
        return aspects;
    }

    private static List<Holder<IAspect>> readAspects(ItemStack source) {
        AspectList composition = AspectIndexAccess.of(source);
        if (composition == null || composition.isEmpty()) {
            return List.of();
        }
        List<Holder<IAspect>> found = new ArrayList<>();
        for (var entry : composition.entries()) {
            if (entry.amount() > 0 && found.size() < MAX_ASPECTS) {
                found.add(entry.aspect());
            }
        }
        // Sorted by id, and the menu sorts its own copy the same way. The position of an aspect in this list
        // is what the picked index means - the menu sends an index and this reads one back - so the two
        // sides have to agree on the order even if a composition ever comes back in a different one.
        found.sort(java.util.Comparator.comparing(BlockEntityDistillationEncoder::aspectId));
        return List.copyOf(found);
    }

    /** An aspect's id as a string, for a stable ordering that does not depend on iteration order. */
    private static String aspectId(Holder<IAspect> aspect) {
        return aspect.unwrapKey().map(k -> k.location().toString()).orElse("");
    }

    /** The aspect the player picked, or {@code null}. */
    public @Nullable Holder<IAspect> selectedAspect() {
        List<Holder<IAspect>> list = availableAspects();
        return selectedAspect >= 0 && selectedAspect < list.size() ? list.get(selectedAspect) : null;
    }

    public int selectedAspectIndex() {
        return selectedAspect;
    }

    public void setSelectedAspect(int index) {
        if (index < -1 || index >= MAX_ASPECTS) {
            return;
        }
        if (index != selectedAspect) {
            selectedAspect = index;
            setChanged();
        }
    }

    /** How much of its own aspect the source item carries. This is the amount the pattern will output. */
    public long yieldFor(Holder<IAspect> aspect) {
        ItemStack source = inventory.getItem(SLOT_SOURCE);
        if (source.isEmpty() || aspect == null) {
            return 0;
        }
        AspectList composition = AspectIndexAccess.of(source);
        if (composition == null) {
            return 0;
        }
        return Math.max(1, composition.amountOf(aspect));
    }

    // ------------------------------------------------------------------
    // Encoding
    // ------------------------------------------------------------------

    /**
     * Writes one pattern, if there is something valid to write.
     *
     * <p>All four preconditions are checked before anything is consumed, so a failed attempt never costs a
     * blank pattern. That ordering matters because a blank pattern is an expensive item and the failure
     * modes - no source, nothing selected, output already full - are all things a player does by accident.
     *
     * @return whether a pattern was written
     */
    public boolean encode() {
        ItemStack blank = inventory.getItem(SLOT_BLANK);
        if (blank.isEmpty() || !AEItems.BLANK_PATTERN.is(blank)) {
            return false;
        }
        if (!inventory.getItem(SLOT_ENCODED).isEmpty()) {
            return false;
        }
        Holder<IAspect> aspect = selectedAspect();
        if (aspect == null) {
            return false;
        }
        ItemStack source = inventory.getItem(SLOT_SOURCE);
        if (source.isEmpty()) {
            return false;
        }
        ResourceLocation aspectId = aspect.unwrapKey().map(k -> k.location()).orElse(null);
        if (aspectId == null) {
            return false;
        }

        ItemStack pattern = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(source), 1)),
                List.of(new GenericStack(AEssentiaKey.of(aspectId), yieldFor(aspect))));

        // Tag the research the pattern belongs to, so a machine or a terminal can refuse it for a player who
        // has not learned distillation yet. The pattern itself does not carry that knowledge.
        CompoundTag research = new CompoundTag();
        research.putString(NBT_RESEARCH, REQUIRED_RESEARCH);
        pattern.set(DataComponents.CUSTOM_DATA, CustomData.of(research));

        blank.shrink(1);
        inventory.setItem(SLOT_ENCODED, pattern);
        setChanged();
        return true;
    }

    /** The research a written pattern claims, or {@code null} when it is not one of ours. */
    public static @Nullable String researchOf(ItemStack pattern) {
        CustomData data = pattern.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(NBT_RESEARCH) ? tag.getString(NBT_RESEARCH) : null;
    }

    // ------------------------------------------------------------------
    // Menu
    // ------------------------------------------------------------------

    /** The source item as the screen's ghost slot sees it. */
    public ItemStack sourceTemplate() {
        return inventory.getItem(SLOT_SOURCE);
    }

    /** Sets the ghost template without consuming anything from the player. */
    public void setSourceTemplate(ItemStack stack) {
        inventory.setItem(SLOT_SOURCE, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        selectedAspect = -1;
        setChanged();
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int containerId, net.minecraft.world.entity.player.Inventory playerInventory,
            net.minecraft.world.entity.player.Player player) {
        return new thaumicenergistics_ce.menu.MenuDistillationEncoder(containerId, playerInventory, this);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // ContainerHelper, not SimpleContainer.createTag, for the reason the Knowledge Inscriber already
        // records: createTag writes a bare list of the non-empty slots with no index on any entry, and this
        // container's first slot is the source template with the two pattern wells after it. With the source
        // well empty the list held one entry fewer than the slot the patterns came from, so a by-position read
        // moved every pattern down one well - the front one into the source well, which is a ghost slot the
        // player cannot take anything out of and whose next write discards what is in it. ContainerHelper
        // puts the index on each entry, so gaps survive.
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
        tag.putInt("SelectedAspect", selectedAspect);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(ContainerHelper.TAG_ITEMS, Tag.TAG_LIST)) {
            // Every entry names the slot it came from, so an empty source well stays empty and the patterns
            // stay in their own wells.
            ContainerHelper.loadAllItems(tag, inventory.getItems(), registries);
        } else {
            // Written before the fix, when the bare list above was all there was; see loadLegacyInventory.
            loadLegacyInventory(tag.getList("Inventory", Tag.TAG_COMPOUND), registries);
        }
        selectedAspect = tag.getInt("SelectedAspect");
    }

    /**
     * Reads the pre-fix form: a compact list of the non-empty stacks with no slot recorded on any entry.
     *
     * <p>Where an entry came from has to be worked out from what it is, because the wells do not overlap - the
     * blank well takes blank patterns, the written well takes written ones, and anything else is the source
     * template. A blank or a written pattern therefore goes back to the well it belongs in whichever slot it
     * was in when it was saved, which is what recovers a world the bug had already moved: there the list held
     * the pattern first, and reading it by position put it in the source well.
     *
     * <p>What the old form cannot say is whether a pattern was a deposit or a source <em>template</em>, since
     * the same item in the same entry looks identical either way. The deposit wins, deliberately: an entry
     * that is a pattern is far more often the deposit the player paid for than a template, and reading it into
     * the ghost well is the loss this change is here to stop. The one case that is not exact is a template
     * that was itself a pattern - that entry comes back as a deposit in the pattern's own well, where the
     * player can take it, so a world saved that way hands back one more pattern than was handed over. A plain
     * template is unaffected: it is not a pattern, so nothing competes for its slot.
     *
     * <p>Logged, because this is the one path that rearranges a world as it loads.
     */
    private void loadLegacyInventory(ListTag list, HolderLookup.Provider registries) {
        // At most SLOT_COUNT entries and SLOT_COUNT slots, and only non-empty entries take one, so a free slot
        // always exists and nothing here has to overwrite what an earlier entry put down.
        int kept = Math.min(list.size(), SLOT_COUNT);
        List<Integer> placed = new ArrayList<>(kept);
        for (int entry = 0; entry < kept; entry++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(entry));
            if (stack.isEmpty()) {
                continue;
            }
            int slot = legacySlotFor(stack);
            if (slot < 0) {
                // Not reachable with the sizes above; said out loud rather than written over another entry.
                ThaumicEnergistics.LOG.error(
                        "[encoder] at {} cannot place {} from a pre-fix tag: every well is taken",
                        worldPosition, stack);
                continue;
            }
            inventory.setItem(slot, stack);
            placed.add(slot);
        }
        if (!placed.isEmpty()) {
            ThaumicEnergistics.LOG.info(
                    "[encoder] at {} read a tag saved before slot indices were written: {} entr{} placed at {}",
                    worldPosition, kept, kept == 1 ? "y" : "ies", placed);
        }
    }

    /**
     * The well a pre-fix entry belongs in: the well that takes that kind of thing when it is free, and
     * otherwise the free slot furthest from the source well, which is the one the player can still reach into.
     */
    private int legacySlotFor(ItemStack stack) {
        int preferred = preferredWellFor(stack);
        if (inventory.getItem(preferred).isEmpty()) {
            return preferred;
        }
        for (int slot = SLOT_COUNT - 1; slot >= 0; slot--) {
            if (inventory.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** The well an item belongs in by kind. The source well takes whatever is neither kind of pattern. */
    private static int preferredWellFor(ItemStack stack) {
        if (AEItems.BLANK_PATTERN.is(stack)) {
            return SLOT_BLANK;
        }
        if (PatternDetailsHelper.isEncodedPattern(stack)) {
            return SLOT_ENCODED;
        }
        return SLOT_SOURCE;
    }

    /** Drops the contents when the block is broken. */
    public void dropContents() {
        if (level == null) {
            return;
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            net.minecraft.world.Containers.dropItemStack(
                    level,
                    worldPosition.getX() + 0.5,
                    worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5,
                    stack);
            inventory.setItem(slot, ItemStack.EMPTY);
        }
    }
}
