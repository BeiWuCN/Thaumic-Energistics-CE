package thaumicenergistics_ce.arcane;

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
 * The Arcane Crafting Terminal's grid, presented to Thaumaturge as a workbench's input.
 *
 * <ul>
 *   <li><b>The grid stays nine cells, empty ones included.</b> Vanilla's {@code CraftingInput.of} shrinks
 *       the list to the rectangle the non-empty cells occupy, so a grid holding one item in its middle
 *       arrives as a one-element list. Thaumaturge reads nine cells whatever is in it - its shapeless
 *       matcher walks {@code items().subList(0, 9)}, its pattern matcher compares {@code width()} and
 *       {@code height()} with the recipe's - so a shrunk grid throws there, out of a menu constructor
 *       while a right-click is handled, and the terminal does not open.</li>
 *   <li>Adds what a plain grid lacks: the crafting player and the machine's own contributions, the wand
 *       it charges and the crystals it supplies - an arcane recipe is not satisfied by items alone.</li>
 *   <li>The grid is rebuilt from the part's inventory each construction, not held: a terminal's grid
 *       changes constantly and a cached view would be stale the moment anything moved.</li>
 * </ul>
 */
public final class TerminalArcaneCraftingInput implements IArcaneCraftingInput {

    /** The workbench grid this stands for: three cells a side, filled or not. */
    private static final int GRID_WIDTH = 3;
    private static final int GRID_HEIGHT = 3;

    /** The nine cells in slot order, empties included - see the class note. */
    private final List<ItemStack> grid;

    /** Built here rather than read out of a {@code CraftingInput}, which would have shrunk the grid. */
    private final StackedContents stackedContents = new StackedContents();

    private final int ingredientCount;
    private final Player player;
    private final ItemStack wand;
    private final AspectList crystals;
    private final @Nullable PartArcaneCraftingTerminal part;

    /**
     * Collects the crystal payment from the terminal's own crystal slots, never from the grid: a crystal in
     * the grid also counts towards {@code ingredientCount}, which makes every recipe with a crystal cost
     * unmatchable. The grid is the recipe's shape, the crystals are payment for it.
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
        this.grid = List.copyOf(grid);
        this.player = player;
        this.wand = wand == null ? ItemStack.EMPTY : wand;
        this.crystals = crystalsIn(crystalSlots);
        this.part = part;

        // All nine cells, not just the occupied ones: the count is what a recipe's ingredient list is
        // compared against, and the contents are what its ingredient matching reads.
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
     * The part this input came from, or {@code null} when built for something else.
     * The vis source needs it: Thaumaturge hands it this input alone, and a virtual workbench context has no
     * position to look the aura up by.
     */
    public @Nullable PartArcaneCraftingTerminal part() {
        return part;
    }

    // ---- IArcaneCraftingInput -------------------------------------------------

    @Override
    public ItemStack getItem(int column, int row) {
        // x + y * width, which is how Thaumaturge's pattern matcher indexes a grid.
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
