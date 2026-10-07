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
 * 把样板按标签读写，并将其包装成 AE2 的合成 CPU 保存与解码所用的物品。
 * 知识核心与任务列表共用同一套 {@link #save}/{@link #load} 约定，因此
 * 样板在两者之间不会出现差异。
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

    /** 从 {@link #toItem} 产出的物品中读回样板，无法读取时返回 {@code null}。 */
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
                // 只保存显示物品堆：材料属于配方，由配方管理器查找。
                List.of(),
                // 已做钳制：宽高来自保存的样板，百万格位的网格会直接把游戏卡死。
                Math.clamp(width, 1, ThEArcanePattern.MAX_GRID),
                Math.clamp(height, 1, ThEArcanePattern.MAX_GRID),
                crystals,
                tag.getInt("BaseVis"),
                research,
                stage,
                // 标签会被保留下来，正是上面那个例外：标签无法从配方反推得到。
                cellTags);
    }
}
