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
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.init.MachineMenus;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.util.ThELog;

/**
 * 蒸馏编码器：把“该物品蒸馏成那种源质”写成 ME 处理
 * 样板，因为蒸馏样板不是 Thaumaturge 能查到的配方。只提供源物品
 * 真正持有的要素，并且样板会带上它所属的研究，这样终端可以
 * 替还没学会蒸馏的玩家拒掉它。
 */
public class BlockEntityDistillationEncoder extends ThEBaseBlockEntity {

    /** 被蒸馏的物品。屏幕上的幽灵槽：物品堆是模板，不是花费。 */
    public static final int SLOT_SOURCE = 0;

    public static final int SLOT_BLANK = 1;

    public static final int SLOT_ENCODED = 2;

    public static final int SLOT_COUNT = 3;

    public static final int MAX_ASPECTS = 6;

    /** 使用蒸馏样板所需的研究门槛。与 1.12.2 版本的键一致。 */
    public static final String REQUIRED_RESEARCH = "DISTILESSENTIA";

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

    // 源物品持有的要素，按提供时的顺序；见 availableAspects，它在
    // 物品变化时刷新本字段。
    private List<Holder<IAspect>> aspects = List.of();

    private ItemStack cachedSource = ItemStack.EMPTY;

    /** 玩家选中的要素，作为 {@link #aspects} 的索引；-1 表示未选。 */
    private int selectedAspect = -1;

    public BlockEntityDistillationEncoder(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISTILLATION_ENCODER.get(), pos, state);
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    // ------------------------------------------------------------------
    // ------------------------------------------------------------------

    public List<Holder<IAspect>> availableAspects() {
        ItemStack source = inventory.getItem(SLOT_SOURCE);
        if (!ItemStack.matches(source, cachedSource)) {
            cachedSource = source.copy();
            aspects = source.isEmpty() ? List.of() : readAspects(source);
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
        // 按 id 排序：菜单对自己的副本用同样方式排序，而选中的索引是一个位置。
        found.sort(Comparator.comparing(BlockEntityDistillationEncoder::aspectId));
        return List.copyOf(found);
    }

    /** 要素的 id 字符串，用于不依赖遍历顺序的稳定排序。 */
    private static String aspectId(Holder<IAspect> aspect) {
        return aspect.unwrapKey().map(k -> k.location().toString()).orElse("");
    }

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

    /** 源物品携带多少自身要素。这就是样板将输出的数量。 */
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
    // ------------------------------------------------------------------

    /**
     * 写出一份样板，前提是有可写的东西。所有前置条件都先检查，
     * 所以失败的尝试绝不会白费一份空白样板。
     * @return 是否写入了样板
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

        // 给样板打上它所属的研究标签；样板本身不携带这类信息。
        CompoundTag research = new CompoundTag();
        research.putString(NBT_RESEARCH, REQUIRED_RESEARCH);
        pattern.set(DataComponents.CUSTOM_DATA, CustomData.of(research));

        blank.shrink(1);
        inventory.setItem(SLOT_ENCODED, pattern);
        setChanged();
        return true;
    }

    /** 已写入的样板所声称的研究，不属于本 mod 的样板则为 {@code null}。 */
    public static @Nullable String researchOf(ItemStack pattern) {
        CustomData data = pattern.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(NBT_RESEARCH) ? tag.getString(NBT_RESEARCH) : null;
    }

    // ------------------------------------------------------------------
    // ------------------------------------------------------------------

    public ItemStack sourceTemplate() {
        return inventory.getItem(SLOT_SOURCE);
    }

    /** 设置幽灵模板，不从玩家那里消耗任何东西。 */
    public void setSourceTemplate(ItemStack stack) {
        inventory.setItem(SLOT_SOURCE, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        selectedAspect = -1;
        setChanged();
    }

    @Override
    public AbstractContainerMenu createMenu(
            int containerId, Inventory playerInventory,
            Player player) {
        return MachineMenus.distillationEncoder(containerId, playerInventory, this);
    }

    // ------------------------------------------------------------------
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // 用 ContainerHelper，不用 createTag：裸列表没有槽位索引，空隙会丢失。
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
        tag.putInt("SelectedAspect", selectedAspect);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(ContainerHelper.TAG_ITEMS, Tag.TAG_LIST)) {
            // 每个条目都写明自己的槽位，所以空的源物品槽能保持为空。
            ContainerHelper.loadAllItems(tag, inventory.getItems(), registries);
        } else {
            // 修复前的标签：当时只有裸列表；见 loadLegacyInventory。
            loadLegacyInventory(tag.getList("Inventory", Tag.TAG_COMPOUND), registries);
        }
        selectedAspect = tag.getInt("SelectedAspect");
    }

    /**
     * 读取修复前的形式：一份紧凑的非空物品堆列表，没有记录槽位。本身就是
     * 样板的模板看起来与存入的物品完全一样，所以这样的世界在加载时会多出一份样板。
     */
    private void loadLegacyInventory(ListTag list, HolderLookup.Provider registries) {
        // 只有非空条目占槽位，且条目数 <= 槽位数，所以空槽位总是存在。
        int kept = Math.min(list.size(), SLOT_COUNT);
        List<Integer> placed = new ArrayList<>(kept);
        for (int entry = 0; entry < kept; entry++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(entry));
            if (stack.isEmpty()) {
                continue;
            }
            int slot = legacySlotFor(stack);
            if (slot < 0) {
                // 按上面的规模不可达；只记录日志，不覆盖另一个条目。
                ThELog.LOG.error(
                        "[encoder] at {} cannot place {} from a pre-fix tag: every well is taken",
                        worldPosition, stack);
                continue;
            }
            inventory.setItem(slot, stack);
            placed.add(slot);
        }
        if (!placed.isEmpty()) {
            ThELog.LOG.info(
                    "[encoder] at {} read a tag saved before slot indices were written: {} entr{} placed at {}",
                    worldPosition, kept, kept == 1 ? "y" : "ies", placed);
        }
    }

    /**
     * 修复前的条目该进哪个槽：优先它同类的空槽，否则取离源物品槽最远
     * 的那个空槽——玩家还够得着的那个。
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

    /** 物品按类别该进的槽。既不是两种样板之一的东西都归源物品槽。 */
    private static int preferredWellFor(ItemStack stack) {
        if (AEItems.BLANK_PATTERN.is(stack)) {
            return SLOT_BLANK;
        }
        if (PatternDetailsHelper.isEncodedPattern(stack)) {
            return SLOT_ENCODED;
        }
        return SLOT_SOURCE;
    }

    public void dropContents() {
        if (level == null) {
            return;
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            // 源物品槽写的是一个物品名；JEI 写出那个名字时不取走任何东西，所以掉落它
            // 会凭空造出一份。铭刻机的合成格出于同样的理由被排除在掉落之外。
            if (slot == SLOT_SOURCE) {
                continue;
            }
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
