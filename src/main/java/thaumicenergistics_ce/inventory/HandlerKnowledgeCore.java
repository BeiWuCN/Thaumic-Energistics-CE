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
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.util.ThELog;

/**
 * 存在一个知识核心里的奥术样板：核心物品堆之上的值对象。
 * 它读写物品堆自己的 {@link CustomData}，所以核心始终是一件可携带的物品，没有外挂物品栏，
 * 数据也不会进入物品的组件注册表。
 */
public final class HandlerKnowledgeCore {

    private static final String NBT_PATTERNS = "Patterns";

    /** 一个核心存多少样板。与组装机 GUI 的 7x3 只读网格一致。 */
    public static final int MAXIMUM_STORED_PATTERNS = 21;

    private final ItemStack core;
    private final HolderLookup.Provider registries;
    private final List<ThEArcanePattern> patterns = new ArrayList<>(MAXIMUM_STORED_PATTERNS);

    /**
     * 本版本读不了的条目，按原样保留并原样写回。
     * [save] 会重写整个列表，所以在这里丢掉一个条目，下一次存储时它就没了。
     */
    private final List<CompoundTag> unreadable = new ArrayList<>();

    public HandlerKnowledgeCore(ItemStack core, HolderLookup.Provider registries) {
        this.core = core;
        this.registries = registries;
        load();
    }

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

    public @Nullable ThEArcanePattern patternFor(ItemStack result) {
        for (ThEArcanePattern pattern : patterns) {
            if (ItemStack.isSameItemSameComponents(pattern.result(), result)) {
                return pattern;
            }
        }
        return null;
    }

    /**
     * 存储一个样板，替换同一结果的已有条目。
     * @return 核心已满且没有该结果的条目时为 {@code false}
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

    public boolean removeByResult(ItemStack result) {
        ThEArcanePattern stored = patternFor(result);
        if (stored == null) {
            return false;
        }
        return remove(stored);
    }

    public List<ItemStack> storedOutputs() {
        List<ItemStack> outputs = new ArrayList<>(patterns.size());
        for (ThEArcanePattern pattern : patterns) {
            outputs.add(pattern.result().copy());
        }
        return outputs;
    }

    public int totalVis() {
        int total = 0;
        for (ThEArcanePattern pattern : patterns) {
            total += pattern.chargedVis();
        }
        return total;
    }

    // ------------------------------------------------------------------
    // 持久化，存放在物品堆的自定义数据里
    // ------------------------------------------------------------------

    private void load() {
        patterns.clear();
        unreadable.clear();
        CompoundTag tag = core.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag list = tag.getList(NBT_PATTERNS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            // 取副本而不是引用：save 可能会写回这个条目，而标签属于物品堆。
            CompoundTag entry = list.getCompound(i).copy();
            ThEArcanePattern pattern =
                    patterns.size() < MAXIMUM_STORED_PATTERNS ? ThEArcanePattern.load(registries, entry) : null;
            if (pattern != null) {
                patterns.add(pattern);
            } else {
                // 既超出上限又读不了：这个条目是玩家的，不该由我们丢弃。
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
        // 把本版本读不了的内容原样放回：这次写入替换整个列表，
        // 漏掉它们就等于删掉它们。
        for (CompoundTag entry : unreadable) {
            list.add(entry.copy());
        }
        if (!unreadable.isEmpty()) {
            ThELog.LOG.warn(
                    "[core] writing {} pattern(s) and keeping {} entr(ies) this build cannot read; they would"
                            + " otherwise be deleted by this save",
                    patterns.size(),
                    unreadable.size());
        }
        tag.put(NBT_PATTERNS, list);
        core.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

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
     * 一条警告，列出本版本读不了的条目；全部可读时什么都不显示。
     * 之所以显示，是因为这些条目仍留在核心里，没有它，玩家的列表只是看起来短了。
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
     * 应用一种文字颜色。包一层而不是就地调用 [withStyle]：那个方法是
     * 可变参数的，在这套工具链上针对 {@code ChatFormatting} 的重载解析定不下来。
     */
    private static MutableComponent styled(Component text, ChatFormatting colour) {
        MutableComponent mutable = text.copy();
        mutable.setStyle(Style.EMPTY.applyFormat(colour));
        return mutable;
    }

    private static Component aspectName(AspectInstance entry) {
        var id = entry.aspect().getKey().location();
        return Component.translatable("aspect." + id.getNamespace() + "." + id.getPath());
    }
}
