package thaumicenergistics_ce.arcane;

import appeng.api.networking.energy.IEnergySource;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneCraftingInput;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 把奥术合成终端的网格按工作台输入交给 Thaumaturge。
 * 网格保留九个格位、含空格位：原版 {@code CraftingInput.of} 会收缩成非空矩形，
 * 收缩会让 Thaumaturge 的菜单构造函数抛异常。
 * 普通网格缺的东西也补上：合成的玩家、机器给的法杖与晶体。
 */
public final class TerminalArcaneCraftingInput implements IArcaneCraftingInput {

    private static final int GRID_WIDTH = 3;
    private static final int GRID_HEIGHT = 3;

    /** 按槽位顺序的九个格位，含空格位。 */
    private final List<ItemStack> grid;

    /** 在这里建，不从 {@code CraftingInput} 里读——那会把网格收缩掉。 */
    private final StackedItemContents stackedContents = new StackedItemContents();

    private final int ingredientCount;
    private final Player player;
    private final ItemStack wand;
    private final AspectList crystals;
    private final @Nullable PartArcaneCraftingTerminal part;
    private final @Nullable IEnergySource payer;

    /**
     * 在这里冻结，Thaumaturge 一次合成问两次灵气源，两次答案要一致；
     * 供给调用里再读卡片，就可能中途换答案。
     */
    private final boolean visConnection;

    /**
     * 晶体支付只收终端自己的晶体槽，网格里的不收：
     * 网格里的晶体会计入 {@code ingredientCount}，这类配方就匹配不上。
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
     * 有支付者就是无线终端在合成：vis 取该玩家周围的灵气，不取线缆周围的。见 {@link #payer()}。
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
     * 完整形式：{@code visConnection} 记下建这个输入时终端装没装 vis 连接卡，
     * 同一场合成的两次灵气询问才会给同一个数。
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

        // 九个格位全算，不只算占用的：数量跟配方材料列表比，
        // 内容给材料匹配读。
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
     * 这个输入来自哪个部件；为别的对象构建时返回 {@code null}。
     * vis 源靠它，Thaumaturge 只把这个输入交给它，虚拟工作台没有位置可查。
     */
    public @Nullable PartArcaneCraftingTerminal part() {
        return part;
    }

    /**
     * 合成来自手持物品时谁付 vis：它自己的电池，灵气取玩家周围的。
     * 已放置的部件返回 {@code null}，由网络付费。
     */
    public @Nullable IEnergySource payer() {
        return payer;
    }

    /**
     * 这次合成用的终端装没装 vis 连接卡：{@code true} 把无属性 vis 转到玩家周围的灵气，
     * {@code false} 继续用网络电力买。
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
    public StackedItemContents stackedContents() {
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
