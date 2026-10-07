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
 * 把「物品蒸馏成源质」写成 ME 处理样板，蒸馏样板不在 Thaumaturge 的配方表里。
 * 只列源物品真正持有的要素；样板带上所属研究，终端据此拒掉没学蒸馏的玩家。
 */
public class BlockEntityDistillationEncoder extends ThEBaseBlockEntity {

    /** 源物品所在的幽灵槽；槽里只是模板，不消耗。 */
    public static final int SLOT_SOURCE = 0;

    public static final int SLOT_BLANK = 1;

    public static final int SLOT_ENCODED = 2;

    public static final int SLOT_COUNT = 3;

    public static final int MAX_ASPECTS = 6;

    /** 样板要求的研究；键名与 1.12.2 版一致。 */
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

    // 源物品持有的要素，按 availableAspects 给出的顺序；物品变化时刷新。
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
        // 按 id 排序；菜单对副本用同样排序，选中项是位置索引，两边顺序不一致就选错。
        found.sort(Comparator.comparing(BlockEntityDistillationEncoder::aspectId));
        return List.copyOf(found);
    }

    /** 要素 id 字符串；排序用它，不依赖遍历顺序。 */
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

    /** 源物品带多少该要素；样板按这个数输出。 */
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
     * 写出样板。前置条件全部先检查，失败不消耗空白样板。
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

        // 给样板打上研究标签；样板本身不带这类信息。
        CompoundTag research = new CompoundTag();
        research.putString(NBT_RESEARCH, REQUIRED_RESEARCH);
        pattern.set(DataComponents.CUSTOM_DATA, CustomData.of(research));

        blank.shrink(1);
        inventory.setItem(SLOT_ENCODED, pattern);
        setChanged();
        return true;
    }

    /** 样板带着的研究；不是本 mod 的样板返回 {@code null}。 */
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

    /** 设置幽灵模板；不消耗玩家物品。 */
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
        // 用 ContainerHelper 存盘，它记槽位号；裸列表会把空隙丢掉。
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
        tag.putInt("SelectedAspect", selectedAspect);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(ContainerHelper.TAG_ITEMS, Tag.TAG_LIST)) {
            // 每个条目带着自己的槽位号，空槽读回来还是空。
            ContainerHelper.loadAllItems(tag, inventory.getItems(), registries);
        } else {
            // 旧存档的键：那时只存裸列表，见 loadLegacyInventory。
            loadLegacyInventory(tag.getList("Inventory", Tag.TAG_COMPOUND), registries);
        }
        selectedAspect = tag.getInt("SelectedAspect");
    }

    /**
     * 读旧格式的库存：紧凑的非空物品堆列表，不记槽位号。
     * 模板本身就是样板，这类世界加载后会多出一份样板。
     */
    private void loadLegacyInventory(ListTag list, HolderLookup.Provider registries) {
        // 条目数不超过槽位数，末尾总留有空槽。
        int kept = Math.min(list.size(), SLOT_COUNT);
        List<Integer> placed = new ArrayList<>(kept);
        for (int entry = 0; entry < kept; entry++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(entry));
            if (stack.isEmpty()) {
                continue;
            }
            int slot = legacySlotFor(stack);
            if (slot < 0) {
                // 按上面的规模走不到这里；只记日志，不覆盖别的条目。
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
     * 旧格式的条目该进哪个槽：先找同类空槽，没有就取离源物品槽最远的空槽。
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

    /** 物品按类别落槽；两种样板之外的都归源物品槽。 */
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
            // 源物品槽里只是一个名字，JEI 写这个名字时不取走东西，掉落它等于凭空造一份。
            // 合成格同理，也不进掉落表。
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
