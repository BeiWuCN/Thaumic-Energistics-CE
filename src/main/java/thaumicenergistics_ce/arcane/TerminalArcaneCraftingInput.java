package thaumicenergistics_ce.arcane;

import appeng.api.networking.energy.IEnergySource;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneCraftingInput;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 奥术合成终端的网格，以工作台输入的形式交给 Thaumaturge。网格
 * 保持九个格位、包含空格位，因为原版的 {@code CraftingInput.of} 会收缩成
 * 非空矩形，而那会让 Thaumaturge 的菜单构造函数抛异常。它还补上了
 * 普通网格缺少的东西：合成的玩家，以及机器提供的法杖与晶体。
 */
public final class TerminalArcaneCraftingInput implements IArcaneCraftingInput {

    private static final int GRID_WIDTH = 3;
    private static final int GRID_HEIGHT = 3;

    /** 按槽位顺序的九个格位，包含空格位——见类注释。 */
    private final List<ItemStack> grid;

    /** 在此处构建，而不是从 {@code CraftingInput} 读出，后者会把网格收缩掉。 */
    private final StackedContents stackedContents = new StackedContents();

    private final int ingredientCount;
    private final Player player;
    private final ItemStack wand;
    private final AspectList crystals;
    private final @Nullable PartArcaneCraftingTerminal part;
    private final @Nullable IEnergySource payer;

    /**
     * 在这里冻结，因为 Thaumaturge 每次合成会两次询问灵气源，两次必须一致；
     * 若在供给调用里再读一次卡片，就可能在一次合成的中途给出不同的答案。
     */
    private final boolean visConnection;

    /**
     * 只从终端自己的晶体槽收集晶体支付，绝不从网格取：网格里的晶体
     * 也会计入 {@code ingredientCount}，从而使这类配方无法匹配。
     */
    private static AspectList crystalsIn(List<ItemStack> slots) {
        AspectList found = AspectList.EMPTY;
        for (ItemStack stack : slots) {
            if (stack.isEmpty()) {
                continue;
            }
            Holder<IAspect> aspect = EssentiaCrystals.aspectOf(stack);
            if (aspect != null) {
                found = found.add(aspect, stack.getCount());
            }
        }
        return found;
    }

    public TerminalArcaneCraftingInput(
            List<ItemStack> grid,
            Player player,
            ItemStack wand,
            List<ItemStack> crystalSlots,
            @Nullable PartArcaneCraftingTerminal part) {
        this(grid, player, wand, crystalSlots, part, null, false);
    }

    /**
     * 有支付者意味着这是无线终端的合成：此时 vis 来自该玩家周围的灵气，
     * 而不是线缆周围的灵气。见 {@link #payer()}。
     */
    public TerminalArcaneCraftingInput(
            List<ItemStack> grid,
            Player player,
            ItemStack wand,
            List<ItemStack> crystalSlots,
            @Nullable PartArcaneCraftingTerminal part,
            @Nullable IEnergySource payer) {
        this(grid, player, wand, crystalSlots, part, payer, false);
    }

    /**
     * 完整形式：{@code visConnection} 表示构建此输入时终端是否装有 vis 连接卡，
     * 这样同一场合成的两次灵气询问会给出相同的数值。
     */
    public TerminalArcaneCraftingInput(
            List<ItemStack> grid,
            Player player,
            ItemStack wand,
            List<ItemStack> crystalSlots,
            @Nullable PartArcaneCraftingTerminal part,
            @Nullable IEnergySource payer,
            boolean visConnection) {
        this.grid = List.copyOf(grid);
        this.player = player;
        this.wand = wand == null ? ItemStack.EMPTY : wand;
        this.crystals = crystalsIn(crystalSlots);
        this.part = part;
        this.payer = payer;
        this.visConnection = visConnection;

        // 全部九个格位，而不只是被占用的那些：数量是配方材料列表的比较对象，
        // 内容则是材料匹配所读取的对象。
        int count = 0;
        for (ItemStack stack : this.grid) {
            if (!stack.isEmpty()) {
                count++;
                this.stackedContents.accountStack(stack, 1);
            }
        }
        this.ingredientCount = count;
    }

    /**
     * 该输入来源的部件；若是为其它对象构建则返回 {@code null}。vis 源需要它：
     * Thaumaturge 只把这个输入交给它，而虚拟工作台没有位置可供查找。
     */
    public @Nullable PartArcaneCraftingTerminal part() {
        return part;
    }

    /**
     * 合成来自手持物品时由谁支付 vis：它自身的电池，此时灵气取
     * 玩家周围的那一份。已放置的部件为 {@code null}，它由其网络支付。
     */
    public @Nullable IEnergySource payer() {
        return payer;
    }

    /**
     * 该合成所用终端是否装有 vis 连接卡：{@code true} 把无属性 vis 转到
     * 玩家周围的灵气上，{@code false} 则继续用网络电力购买它。
     */
    public boolean visConnection() {
        return visConnection;
    }

    // ---- IArcaneCraftingInput -------------------------------------------------

    @Override
    public ItemStack getItem(int column, int row) {
        return grid.get(column + row * GRID_WIDTH);
    }

    @Override
    public int width() {
        return GRID_WIDTH;
    }

    @Override
    public int height() {
        return GRID_HEIGHT;
    }

    @Override
    public Player player() {
        return player;
    }

    @Override
    public int ingredientCount() {
        return ingredientCount;
    }

    @Override
    public List<ItemStack> items() {
        return grid;
    }

    @Override
    public StackedContents stackedContents() {
        return stackedContents;
    }

    // ---- RecipeInput ----------------------------------------------------------

    @Override
    public ItemStack getItem(int index) {
        return grid.get(index);
    }

    @Override
    public int size() {
        return grid.size();
    }

    @Override
    public boolean isEmpty() {
        return ingredientCount == 0;
    }

    // ---- IArcaneWorkbench -----------------------------------------------------

    @Override
    public AspectList availableCrystals() {
        return crystals;
    }

    @Override
    public ItemStack wandStack() {
        return wand;
    }

}
