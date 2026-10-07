package thaumicenergistics_ce.arcane;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneCraftingInput;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 供机器使用、以及脱离工作台校验配方的最小 {@link IArcaneCraftingInput}。
 * 槽位顺序与 {@code InventoryArcaneWorkbench} 一致：先九个网格槽，再六个晶体槽，
 * 最后是法杖槽——这里始终为空，因为机器用 vis 支付，不用法杖。
 */
public final class SyntheticArcaneInput implements IArcaneCraftingInput {
    public static final int GRID_SLOTS = 9;
    public static final int CRYSTAL_SLOTS = 6;
    public static final int WAND_SLOT = GRID_SLOTS + CRYSTAL_SLOTS;
    public static final int SIZE = WAND_SLOT + 1;

    private final int width;
    private final int height;
    private final List<ItemStack> items;
    private final StackedContents stackedContents = new StackedContents();
    private final int ingredientCount;
    private @Nullable Player player;

    private SyntheticArcaneInput(int width, int height, List<ItemStack> items) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.items = List.copyOf(items);

        int count = 0;
        for (int i = 0; i < Math.min(GRID_SLOTS, this.items.size()); i++) {
            ItemStack stack = this.items.get(i);
            if (!stack.isEmpty()) {
                count++;
                this.stackedContents.accountStack(stack, 1);
            }
        }
        this.ingredientCount = count;
    }

    public static SyntheticArcaneInput of(int width, int height, List<ItemStack> items) {
        List<ItemStack> padded = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            padded.add(i < items.size() ? items.get(i) : ItemStack.EMPTY);
        }
        return new SyntheticArcaneInput(width, height, padded);
    }

    /**
     * 由样板的网格加上要在晶体槽中暴露的晶体物品堆构建输入。
     *
     * @param crystals 最多六个物品堆，多出的条目会被忽略
     */
    public static SyntheticArcaneInput of(ThEArcanePattern pattern, List<ItemStack> crystals) {
        List<ItemStack> items = new ArrayList<>(SIZE);
        for (int i = 0; i < GRID_SLOTS; i++) {
            items.add(i < pattern.grid().size() ? pattern.grid().get(i) : ItemStack.EMPTY);
        }
        for (int i = 0; i < CRYSTAL_SLOTS; i++) {
            items.add(i < crystals.size() ? crystals.get(i) : ItemStack.EMPTY);
        }
        items.add(ItemStack.EMPTY);
        return new SyntheticArcaneInput(pattern.gridWidth(), pattern.gridHeight(), items);
    }

    public SyntheticArcaneInput withPlayer(@Nullable Player player) {
        this.player = player;
        return this;
    }

    @Override
    public ItemStack getItem(int index) {
        return index >= 0 && index < items.size() ? items.get(index) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack getItem(int x, int y) {
        return getItem(x + y * width);
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return ingredientCount == 0;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public @Nullable Player player() {
        return player;
    }

    @Override
    public int ingredientCount() {
        return ingredientCount;
    }

    @Override
    public List<ItemStack> items() {
        return items;
    }

    @Override
    public StackedContents stackedContents() {
        return stackedContents;
    }

    @Override
    public AspectList availableCrystals() {
        AspectList crystals = AspectList.EMPTY;
        for (int i = GRID_SLOTS; i < WAND_SLOT; i++) {
            ItemStack stack = getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            Holder<IAspect> aspect = EssentiaCrystals.aspectOf(stack);
            if (aspect != null) {
                crystals = crystals.add(aspect, stack.getCount());
            }
        }
        return crystals;
    }

    @Override
    public ItemStack wandStack() {
        return ItemStack.EMPTY;
    }

    public List<AspectInstance> crystalEntries() {
        return availableCrystals().entries();
    }
}
