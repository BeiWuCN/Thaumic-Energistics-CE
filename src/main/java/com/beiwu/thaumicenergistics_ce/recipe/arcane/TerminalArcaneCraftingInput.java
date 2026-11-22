package com.beiwu.thaumicenergistics_ce.recipe.arcane;

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
import com.beiwu.thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 把奥术合成终端的网格按工作台输入交给 Thaumaturge。
 * 九个格位原样保存，但对外报的是非空格位的包围盒 —— 和 {@code InventoryArcaneWorkbench}
 * 用 {@code ArcaneCraftingInput.of} 造出来的输入一致。图案匹配要拿 {@code width()/height()}
 * 跟配方尺寸比，报固定的三格会让所有两格宽、两行高的配方永远对不上。
 * 普通网格缺的东西也补上：合成的玩家、机器给的法杖与晶体。
 */
public final class TerminalArcaneCraftingInput implements IArcaneCraftingInput {

    private static final int GRID_WIDTH = 3;
    private static final int GRID_HEIGHT = 3;

    private final List<ItemStack> grid;

    private final StackedItemContents stackedContents = new StackedItemContents();
    /**
     * 非空格位的包围盒，取值规则与 Thaumaturge 的 {@code ArcaneCraftingInput.ofPositioned} 相同。
     * 图案匹配要求 {@code width()/height()} 与配方尺寸一致，报固定的九格会让所有非三格宽的配方
     * 永远对不上：两格宽的配方卡在 {@code input.width() != pattern.width()} 这一步。
     */
    private final int left;
    private final int top;
    private final int boxWidth;
    private final int boxHeight;

    /**
     * 包围盒内按 {@code x + y * boxWidth} 打包的格位。
     * 无序配方读的是 {@code items().subList(0, width * height)}，只有打包过才是它以为的那几格。
     */
    private final List<ItemStack> packed;

    /** 在这里自己建，不从 {@code CraftingInput} 读，那边会把网格收缩掉。 */

    private final int ingredientCount;
    private final Player player;
    private final ItemStack wand;
    private final AspectList crystals;
    private final @Nullable PartArcaneCraftingTerminal part;
    private final @Nullable IEnergySource payer;

    private final boolean visConnection;
    private ItemStack visCard = ItemStack.EMPTY;

    public TerminalArcaneCraftingInput withVisCard(ItemStack card) {
        visCard = card.copy();
        return this;
    }

    public ItemStack visCard() {
        return visCard;
    }
    private final boolean crystalFirst;

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

    public TerminalArcaneCraftingInput(
            List<ItemStack> grid,
            Player player,
            ItemStack wand,
            List<ItemStack> crystalSlots,
            @Nullable PartArcaneCraftingTerminal part,
            @Nullable IEnergySource payer) {
        this(grid, player, wand, crystalSlots, part, payer, false);
    }

    public TerminalArcaneCraftingInput(
            List<ItemStack> grid,
            Player player,
            ItemStack wand,
            List<ItemStack> crystalSlots,
            @Nullable PartArcaneCraftingTerminal part,
            @Nullable IEnergySource payer,
            boolean visConnection) {
        this(grid, player, wand, crystalSlots, part, payer, visConnection, true);
    }

    /**
     * 完整形式：{@code visConnection} 记下建这个输入时终端装没装 vis 连接卡，
     * 同一场合成的两次灵气询问才会给同一个数；{@code crystalFirst} 冻结付费源模式，
     * 切换按钮和扣费事件读的是同一次合成的同一个值。
     */
    public TerminalArcaneCraftingInput(
            List<ItemStack> grid,
            Player player,
            ItemStack wand,
            List<ItemStack> crystalSlots,
            @Nullable PartArcaneCraftingTerminal part,
            @Nullable IEnergySource payer,
            boolean visConnection,
            boolean crystalFirst) {
        this.grid = List.copyOf(grid);
        this.player = player;
        this.wand = wand == null ? ItemStack.EMPTY : wand;
        this.crystals = crystalsIn(crystalSlots);
        this.part = part;
        this.payer = payer;
        this.visConnection = visConnection;
        this.crystalFirst = crystalFirst;

        // 包围盒先算：后面报尺寸、取格位、打包都靠它。
        int minX = GRID_WIDTH - 1;
        int minY = GRID_HEIGHT - 1;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < GRID_HEIGHT; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) {
                if (this.grid.get(y * GRID_WIDTH + x).isEmpty()) {
                    continue;
                }
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < 0) {
            this.left = 0;
            this.top = 0;
            this.boxWidth = 0;
            this.boxHeight = 0;
            this.packed = List.of();
        } else {
            this.left = minX;
            this.top = minY;
            this.boxWidth = maxX - minX + 1;
            this.boxHeight = maxY - minY + 1;
            List<ItemStack> box = new java.util.ArrayList<>(this.boxWidth * this.boxHeight);
            for (int y = 0; y < this.boxHeight; y++) {
                for (int x = 0; x < this.boxWidth; x++) {
                    box.add(this.grid.get((this.top + y) * GRID_WIDTH + (this.left + x)));
                }
            }
            this.packed = List.copyOf(box);
        }

        // 数量与内容都按九个格位算：数量跟配方材料列表比，内容给材料匹配读。
        int count = 0;
        for (ItemStack stack : this.grid) {
            if (!stack.isEmpty()) {
                count++;
                this.stackedContents.accountStack(stack, 1);
            }
        }
        this.ingredientCount = count;
    }

    /** 包围盒左边界在九格里的列号；空格网格为 0。 */
    public int left() {
        return left;
    }

    /** 包围盒上边界在九格里的行号；空格网格为 0。 */
    public int top() {
        return top;
    }

    /**
     * 这个输入来自哪个部件；为别的对象构建时返回 {@code null}。
     * vis 源靠它，Thaumaturge 只把这个输入交给它，虚拟工作台没有位置可查。
     */
    public @Nullable PartArcaneCraftingTerminal part() {
        return part;
    }

    public @Nullable IEnergySource payer() {
        return payer;
    }

    public boolean visConnection() {
        return visConnection;
    }

    public boolean crystalFirst() {
        return crystalFirst;
    }

    // ---- IArcaneCraftingInput -------------------------------------------------

    /** 包围盒内的坐标。Thaumaturge 的图案匹配按配方的宽高读，不按九格读。 */
    @Override
    public ItemStack getItem(int column, int row) {
        int index = column + row * boxWidth;
        return index >= 0 && index < packed.size() ? packed.get(index) : ItemStack.EMPTY;
    }

    @Override
    public int width() {
        return boxWidth;
    }

    @Override
    public int height() {
        return boxHeight;
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
        return packed;
    }

    @Override
    public StackedItemContents stackedContents() {
        return stackedContents;
    }


    /** 与 {@code items()} 同一份打包顺序：无序配方按下标读，越界不能抛。 */
    @Override
    public ItemStack getItem(int index) {
        return index >= 0 && index < packed.size() ? packed.get(index) : ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return packed.size();
    }

    @Override
    public boolean isEmpty() {
        return ingredientCount == 0;
    }


    @Override
    public AspectList availableCrystals() {
        return crystals;
    }

    @Override
    public ItemStack wandStack() {
        return wand;
    }

}
