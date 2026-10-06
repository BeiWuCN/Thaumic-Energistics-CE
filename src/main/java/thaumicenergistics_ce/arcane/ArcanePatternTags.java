package thaumicenergistics_ce.arcane;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

/**
 * Reads and writes a pattern as a tag, and wraps one into the item AE2's CPU saves and decodes.
 * The same {@link #save}/{@link #load} contract serves the knowledge core and a task list, so a
 * pattern cannot differ between the two.
 */
final class ArcanePatternTags {

    private ArcanePatternTags() {}

    static ItemStack toItem(ThEArcanePattern pattern, HolderLookup.Provider registries) {
        ItemStack stack = new ItemStack(thaumicenergistics_ce.init.ModItems.ARCANE_PATTERN.get());
        CompoundTag tag = new CompoundTag();
        tag.put("Pattern", save(pattern, registries));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** Reads a pattern back out of the item {@link #toItem} produced, or {@code null} if unreadable. */
    static @Nullable ThEArcanePattern ofItem(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("Pattern")) {
            return null;
        }
        return load(registries, tag.getCompound("Pattern"));
    }

    static CompoundTag save(ThEArcanePattern pattern, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Output", pattern.result().save(registries));

        ListTag gridTag = new ListTag();
        for (ItemStack stack : pattern.grid()) {
            gridTag.add(stack.isEmpty() ? new CompoundTag() : stack.save(registries));
        }
        tag.put("Grid", gridTag);
        tag.putInt("GridWidth", pattern.gridWidth());
        tag.putInt("GridHeight", pattern.gridHeight());

        ListTag tagTag = new ListTag();
        for (int cell = 0; cell < ThEArcanePattern.MAX_GRID; cell++) {
            TagKey<Item> cellTag = pattern.cellTag(cell);
            tagTag.add(StringTag.valueOf(cellTag == null ? "" : cellTag.location().toString()));
        }
        tag.put("CellTags", tagTag);

        ListTag aspectTag = new ListTag();
        for (AspectInstance entry : pattern.crystals().entries()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("Aspect", entry.aspect().getKey().location().toString());
            entryTag.putInt("Amount", entry.amount());
            aspectTag.add(entryTag);
        }
        tag.put("Crystals", aspectTag);

        tag.putInt("BaseVis", pattern.baseVis());
        if (pattern.research() != null) {
            tag.putString("Research", pattern.research().toString());
        }
        if (pattern.researchStage() != null) {
            tag.putInt("ResearchStage", pattern.researchStage());
        }
        return tag;
    }

    static @Nullable ThEArcanePattern load(HolderLookup.Provider registries, CompoundTag tag) {
        if (!tag.contains("Output")) {
            return null;
        }
        ItemStack output = ItemStack.parseOptional(registries, tag.getCompound("Output"));
        if (output.isEmpty()) {
            return null;
        }

        List<ItemStack> grid = new ArrayList<>();
        ListTag gridTag = tag.getList("Grid", Tag.TAG_COMPOUND);
        for (int i = 0; i < gridTag.size(); i++) {
            CompoundTag cell = gridTag.getCompound(i);
            grid.add(cell.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(registries, cell));
        }
        if (grid.isEmpty()) {
            return null;
        }

        AspectList crystals = AspectList.EMPTY;
        HolderLookup.RegistryLookup<IAspect> lookup =
                registries.lookup(IAspect.REGISTRY_KEY).orElse(null);
        if (lookup != null) {
            ListTag aspectTag = tag.getList("Crystals", Tag.TAG_COMPOUND);
            for (int i = 0; i < aspectTag.size(); i++) {
                CompoundTag entryTag = aspectTag.getCompound(i);
                ResourceLocation id = ResourceLocation.tryParse(entryTag.getString("Aspect"));
                if (id == null) {
                    continue;
                }
                Holder<IAspect> holder = lookup
                        .get(ResourceKey.create(IAspect.REGISTRY_KEY, id))
                        .orElse(null);
                if (holder != null) {
                    crystals = crystals.add(holder, Math.max(1, entryTag.getInt("Amount")));
                }
            }
        }

        ResourceLocation research =
                tag.contains("Research") ? ResourceLocation.tryParse(tag.getString("Research")) : null;
        Integer stage = tag.contains("ResearchStage") ? tag.getInt("ResearchStage") : null;
        int width = tag.contains("GridWidth")
                ? tag.getInt("GridWidth")
                : Math.min(ThEArcanePattern.MAX_GRID, grid.size());
        int height = tag.contains("GridHeight") ? tag.getInt("GridHeight") : 1;

        List<TagKey<Item>> cellTags = new ArrayList<>(ThEArcanePattern.MAX_GRID);
        ListTag tagTag = tag.getList("CellTags", Tag.TAG_STRING);
        for (int cell = 0; cell < ThEArcanePattern.MAX_GRID; cell++) {
            ResourceLocation id =
                    cell < tagTag.size() ? ResourceLocation.tryParse(tagTag.getString(cell)) : null;
            cellTags.add(id == null ? null : TagKey.create(Registries.ITEM, id));
        }

        return new ThEArcanePattern(
                output,
                grid,
                // Only the display stacks: the ingredients belong to the recipe, looked up from the manager.
                List.of(),
                // Clamped: the width and height come from a saved pattern, and a million-cell grid is a hang.
                Math.clamp(width, 1, ThEArcanePattern.MAX_GRID),
                Math.clamp(height, 1, ThEArcanePattern.MAX_GRID),
                crystals,
                tag.getInt("BaseVis"),
                research,
                stage,
                // The tags do survive, the exception above: a tag is not recoverable from the recipe.
                cellTags);
    }
}
