package thaumicenergistics_ce.blockentity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.ArrayList;
import java.util.Comparator;
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
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
 * <ul>
 *   <li>A distillation pattern is not a recipe Thaumaturge can look up, so this block is where the
 *       player's statement gets written down.
 *   <li>Only aspects the source item actually holds, in the amounts it holds them, are offered: a
 *       pattern claiming otherwise would be accepted by AE2 and would quietly invent essentia.
 *   <li>The pattern is tagged with the research it belongs to, so a machine or a terminal can refuse
 *       it for a player who has not learned distillation.
 * </ul>
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

    // The aspects the source item holds, in the order they are offered; see availableAspects, which
    // refreshes this when the item changes.
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
     * Refreshed when the source item changes. Empty when the slot is empty or the item has no indexed
     * composition.
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
        // Sorted by id: the menu sorts its copy the same way, and the picked index is a position.
        found.sort(Comparator.comparing(BlockEntityDistillationEncoder::aspectId));
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
     * Writes one pattern, if there is something valid to write. Every precondition is checked first, so
     * a failed attempt never costs a blank pattern.
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

        // Tag the research the pattern belongs to; the pattern itself carries no such knowledge.
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
    public AbstractContainerMenu createMenu(
            int containerId, Inventory playerInventory,
            Player player) {
        return new thaumicenergistics_ce.menu.MenuDistillationEncoder(containerId, playerInventory, this);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // ContainerHelper, not createTag: a bare list has no slot index, so gaps are lost.
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
        tag.putInt("SelectedAspect", selectedAspect);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(ContainerHelper.TAG_ITEMS, Tag.TAG_LIST)) {
            // Every entry names its slot, so an empty source well stays empty.
            ContainerHelper.loadAllItems(tag, inventory.getItems(), registries);
        } else {
            // Pre-fix tag: the bare list was all there was; see loadLegacyInventory.
            loadLegacyInventory(tag.getList("Inventory", Tag.TAG_COMPOUND), registries);
        }
        selectedAspect = tag.getInt("SelectedAspect");
    }

    /**
     * Reads the pre-fix form: a compact list of non-empty stacks with no slot recorded, so the well is
     * worked out from the entry. A template that was itself a pattern looks identical to a deposit in
     * that form, so such a world hands back one more pattern than was handed over.
     */
    private void loadLegacyInventory(ListTag list, HolderLookup.Provider registries) {
        // Only non-empty entries take a slot and entries <= slots, so a free slot always exists.
        int kept = Math.min(list.size(), SLOT_COUNT);
        List<Integer> placed = new ArrayList<>(kept);
        for (int entry = 0; entry < kept; entry++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(entry));
            if (stack.isEmpty()) {
                continue;
            }
            int slot = legacySlotFor(stack);
            if (slot < 0) {
                // Not reachable with the sizes above; logged rather than written over another entry.
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
     * The well a pre-fix entry belongs in: its own kind of well when free, otherwise the free slot furthest
     * from the source well - the one the player can still reach into.
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
            Containers.dropItemStack(
                    level,
                    worldPosition.getX() + 0.5,
                    worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5,
                    stack);
            inventory.setItem(slot, ItemStack.EMPTY);
        }
    }
}
