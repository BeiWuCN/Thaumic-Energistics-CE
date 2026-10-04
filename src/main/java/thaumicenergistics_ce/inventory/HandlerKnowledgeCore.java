package thaumicenergistics_ce.inventory;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.init.ModItems;

/**
 * The arcane patterns stored inside one knowledge core: a value object over the core's item stack.
 *
 * <ul><li>Reads and writes the stack's own {@link CustomData}, so a core stays one portable item
 * with no side inventory.</li><li>Data stays out of the item's component registry.</li></ul>
 */
public final class HandlerKnowledgeCore {

    /** Tag key holding the pattern list inside the stack's custom data. */
    private static final String NBT_PATTERNS = "Patterns";

    /** Patterns one core holds. Matches the assembler GUI's 7x3 read-only grid. */
    public static final int MAXIMUM_STORED_PATTERNS = 21;

    private final ItemStack core;
    private final HolderLookup.Provider registries;
    private final List<ThEArcanePattern> patterns = new ArrayList<>(MAXIMUM_STORED_PATTERNS);

    /**
     * Entries this build could not read, kept exactly as found and written back verbatim.
     * {@link #save} rewrites the whole list, so dropping one here deletes it on the next store.
     */
    private final List<CompoundTag> unreadable = new ArrayList<>();

    public HandlerKnowledgeCore(ItemStack core, HolderLookup.Provider registries) {
        this.core = core;
        this.registries = registries;
        load();
    }

    /**
     * Wraps a stack if it is a knowledge core.
     * @return the handler, or {@code null} when the stack is not a core
     */
    public static @Nullable HandlerKnowledgeCore of(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty() || !stack.is(ModItems.KNOWLEDGE_CORE.get())) {
            return null;
        }
        return new HandlerKnowledgeCore(stack, registries);
    }

    public boolean hasCore() {
        return !core.isEmpty();
    }

    public List<ThEArcanePattern> patterns() {
        return List.copyOf(patterns);
    }

    public boolean hasRoom() {
        return patterns.size() < MAXIMUM_STORED_PATTERNS;
    }

    public int size() {
        return patterns.size();
    }

    /** The stored pattern producing {@code result}, or {@code null} when the core has none. */
    public @Nullable ThEArcanePattern patternFor(ItemStack result) {
        for (ThEArcanePattern pattern : patterns) {
            if (ItemStack.isSameItemSameComponents(pattern.result(), result)) {
                return pattern;
            }
        }
        return null;
    }

    /**
     * Stores a pattern, replacing any existing entry for the same result.
     * @return {@code false} when the core is full and holds no entry for that result
     */
    public boolean store(ThEArcanePattern pattern) {
        boolean replacing = patternFor(pattern.result()) != null;
        if (!replacing && !hasRoom()) {
            return false;
        }
        patterns.removeIf(existing -> ItemStack.isSameItemSameComponents(existing.result(), pattern.result()));
        patterns.add(pattern);
        save();
        return true;
    }

    public boolean remove(ThEArcanePattern pattern) {
        boolean removed = patterns.remove(pattern);
        if (removed) {
            save();
        }
        return removed;
    }

    /**
     * Removes the entry whose result is {@code result}: a core holds at most one entry per result.
     * @return {@code false} when the core holds no entry for that result
     */
    public boolean removeByResult(ItemStack result) {
        ThEArcanePattern stored = patternFor(result);
        if (stored == null) {
            return false;
        }
        return remove(stored);
    }

    /** One result stack per stored pattern, in storage order. */
    public List<ItemStack> storedOutputs() {
        List<ItemStack> outputs = new ArrayList<>(patterns.size());
        for (ThEArcanePattern pattern : patterns) {
            outputs.add(pattern.result().copy());
        }
        return outputs;
    }

    /** Total vis the stored patterns would cost, for the tooltip. */
    public int totalVis() {
        int total = 0;
        for (ThEArcanePattern pattern : patterns) {
            total += pattern.chargedVis();
        }
        return total;
    }

    // ------------------------------------------------------------------
    // Persistence, inside the stack's custom data
    // ------------------------------------------------------------------

    private void load() {
        patterns.clear();
        unreadable.clear();
        CompoundTag tag = core.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag list = tag.getList(NBT_PATTERNS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            // Copied, not referenced: save may write this entry back, and the tag belongs to the stack.
            CompoundTag entry = list.getCompound(i).copy();
            ThEArcanePattern pattern =
                    patterns.size() < MAXIMUM_STORED_PATTERNS ? ThEArcanePattern.load(registries, entry) : null;
            if (pattern != null) {
                patterns.add(pattern);
            } else {
                // Past the cap as well as unreadable: the entry is the player's, not ours to drop.
                unreadable.add(entry);
            }
        }
    }

    private void save() {
        CompoundTag tag = core.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag list = new ListTag();
        for (ThEArcanePattern pattern : patterns) {
            list.add(pattern.save(registries));
        }
        // Put back unchanged whatever this build could not read: this write replaces the whole list,
        // so leaving them out deletes them.
        for (CompoundTag entry : unreadable) {
            list.add(entry.copy());
        }
        if (!unreadable.isEmpty()) {
            ThaumicEnergistics.LOG.warn(
                    "[core] writing {} pattern(s) and keeping {} entr(ies) this build cannot read; they would"
                            + " otherwise be deleted by this save",
                    patterns.size(),
                    unreadable.size());
        }
        tag.put(NBT_PATTERNS, list);
        core.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Entries the core holds that this build cannot read: kept, never offered as patterns. */
    public int unreadableCount() {
        return unreadable.size();
    }

    // ------------------------------------------------------------------
    // Tooltip
    // ------------------------------------------------------------------

    public Component describeCapacity() {
        return Component.translatable(
                "item.thaumicenergistics_ce.knowledge_core.stored", patterns.size(), MAXIMUM_STORED_PATTERNS);
    }

    /**
     * A warning naming the entries this build cannot read, or nothing when it can read them all.
     * Shown because the entries stay in the core, so without it the player's list just looks shorter.
     */
    public List<Component> describeUnreadable() {
        if (unreadable.isEmpty()) {
            return List.of();
        }
        return List.of(styled(
                Component.translatable(
                        "item.thaumicenergistics_ce.knowledge_core.unreadable", unreadable.size()),
                ChatFormatting.RED));
    }

    public List<Component> describePatterns() {
        List<Component> lines = new ArrayList<>(patterns.size());
        for (ThEArcanePattern pattern : patterns) {
            lines.add(styled(
                    Component.translatable(
                            "item.thaumicenergistics_ce.knowledge_core.pattern",
                            pattern.result().getHoverName(),
                            pattern.chargedVis()),
                    ChatFormatting.GRAY));
            AspectList crystals = pattern.crystals();
            if (crystals.isEmpty()) {
                lines.add(styled(
                        Component.translatable("item.thaumicenergistics_ce.knowledge_core.no_crystals"),
                        ChatFormatting.DARK_GRAY));
                continue;
            }
            StringBuilder text = new StringBuilder();
            for (AspectInstance entry : crystals.entries()) {
                if (text.length() > 0) {
                    text.append(", ");
                }
                text.append(aspectName(entry).getString())
                        .append(' ')
                        .append(entry.amount());
            }
            lines.add(styled(Component.literal(text.toString()), ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    /**
     * Applies one text colour. Wrapped rather than calling {@code withStyle} inline: that method is
     * varargs, and overload resolution against {@code ChatFormatting} does not settle on this toolchain.
     */
    private static MutableComponent styled(Component text, ChatFormatting colour) {
        MutableComponent mutable = text.copy();
        mutable.setStyle(Style.EMPTY.applyFormat(colour));
        return mutable;
    }

    /** The aspect's display name, from Thaumaturge's own translation keys. */
    private static Component aspectName(AspectInstance entry) {
        var id = entry.aspect().getKey().location();
        return Component.translatable("aspect." + id.getNamespace() + "." + id.getPath());
    }
}
