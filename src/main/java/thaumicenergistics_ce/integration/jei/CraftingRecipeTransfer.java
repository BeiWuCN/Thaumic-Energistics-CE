package thaumicenergistics_ce.integration.jei;

import appeng.core.network.serverbound.FillCraftingGridFromRecipePacket;
import appeng.menu.SlotSemantics;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * Lets JEI fill the Arcane Crafting Terminal's grid from an ordinary crafting recipe.
 * <ul>
 * <li>A second handler beside the arcane one: the grid is nine plain slots, so a missing JEI button reads as broken.</li>
 * <li>Unlike the arcane handler it passes the recipe id, which
 *     {@link FillCraftingGridFromRecipePacket} resolves in the vanilla manager.</li>
 * <li>Written out rather than left to JEI, which only knows the player's inventory; AE2's packet
 *     pulls from the network.</li>
 * </ul>
 */
public class CraftingRecipeTransfer
        implements IRecipeTransferInfo<MenuArcaneCraftingTerminal, RecipeHolder<CraftingRecipe>>,
                IRecipeTransferHandler<MenuArcaneCraftingTerminal, RecipeHolder<CraftingRecipe>> {

    /** The grid is 3x3, and a recipe that needs more than that cannot be laid out here at all. */
    private static final int GRID_WIDTH = 3;
    private static final int GRID_HEIGHT = 3;

    private final IRecipeTransferHandlerHelper helper;

    public CraftingRecipeTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    // ---- IRecipeTransferInfo -------------------------------------------

    @Override
    public Class<? extends MenuArcaneCraftingTerminal> getContainerClass() {
        return MenuArcaneCraftingTerminal.class;
    }

    @Override
    public Optional<MenuType<MenuArcaneCraftingTerminal>> getMenuType() {
        return Optional.of(ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get());
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    /**
     * Whether this recipe can be laid out in the grid. Asked of the recipe, not of the flat ingredient
     * list: a 3x3 check on the list would accept a recipe that is 4 wide.
     */
    @Override
    public boolean canHandle(MenuArcaneCraftingTerminal menu, RecipeHolder<CraftingRecipe> recipe) {
        return recipe.value().canCraftInDimensions(GRID_WIDTH, GRID_HEIGHT);
    }

    @Override
    public List<Slot> getRecipeSlots(MenuArcaneCraftingTerminal menu, RecipeHolder<CraftingRecipe> recipe) {
        List<Slot> grid = menu.getSlots(SlotSemantics.CRAFTING_GRID);
        return grid.size() == PartArcaneCraftingTerminal.GRID_SIZE ? grid : List.of();
    }

    @Override
    public List<Slot> getInventorySlots(MenuArcaneCraftingTerminal menu, RecipeHolder<CraftingRecipe> recipe) {
        return menu.getSlots(SlotSemantics.PLAYER_INVENTORY);
    }

    // ---- IRecipeTransferHandler ----------------------------------------

    // old 6-arg transferRecipe is the interface's only abstract method in JEI 19.57
    @Override
    @SuppressWarnings("removal")
    public @Nullable IRecipeTransferError transferRecipe(
            MenuArcaneCraftingTerminal menu,
            RecipeHolder<CraftingRecipe> holder,
            IRecipeSlotsView display,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        CraftingRecipe recipe = holder.value();
        if (!recipe.canCraftInDimensions(GRID_WIDTH, GRID_HEIGHT) || recipe.getIngredients().isEmpty()) {
            return helper.createInternalError();
        }
        if (getRecipeSlots(menu, holder).size() != PartArcaneCraftingTerminal.GRID_SIZE) {
            return helper.createInternalError();
        }
        if (!doTransfer) {
            return null;
        }

        // The templates travel empty: the packet resolves the recipe by id and reads its own ingredients,
        // and a non-resolving recipe was refused above. A second copy would answer a question never asked.
        NonNullList<ItemStack> templates =
                NonNullList.withSize(PartArcaneCraftingTerminal.GRID_SIZE, ItemStack.EMPTY);
        PacketDistributor.sendToServer(
                new FillCraftingGridFromRecipePacket(holder.id(), templates, false));
        return null;
    }
}
