package thaumicenergistics_ce.integration.jei;

import appeng.core.network.serverbound.FillCraftingGridFromRecipePacket;
import appeng.menu.SlotSemantics;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * Lets JEI fill the Arcane Crafting Terminal's grid from an arcane workbench recipe.
 *
 * <p>Registered for Thaumaturge's own arcane category, so a player opens a recipe the way they always do
 * and presses the transfer button. As with the Knowledge Inscriber, the transfer reads the recipe rather
 * than mirroring JEI's drawn slots: an arcane recipe's view carries its crystal requirement as ingredient
 * slots of its own, and only the nine grid cells are filled here - the terminal's six crystal slots, three
 * down each side of the grid, are left to the player, which is the gap recorded in
 * docs/ARCANE-CRAFTING-TERMINAL.md.
 *
 * <p><b>Where the slots are found.</b> By {@link SlotSemantics}, not by counting. The terminal's menu is not
 * the Knowledge Inscriber's and never will be - it inherits a terminal's layout, a result slot and a wand
 * slot - so any constant worked out from one of them would be wrong for the other. Asking AE2 which slots
 * are the crafting grid cannot drift from how AE2 laid that grid out.
 *
 * <p><b>The filling is AE2's, not this class's.</b> {@link FillCraftingGridFromRecipePacket} takes nine
 * ingredient templates and puts them in the grid, taking what the player does not have out of the ME
 * network and charging the network for it. Writing that here would be a second implementation of exactly
 * what an ME crafting terminal does, and it would be worse: a hand-written version can only reach the
 * player's inventory, which is the one place a terminal's ingredients are not supposed to come from.
 *
 * <p>The packet is given no recipe id, deliberately. It resolves an id through the vanilla recipe manager,
 * and an arcane recipe is not in there; passing one would have the packet find nothing and fill the grid
 * with empty ingredients. With no id it falls back to the templates, which is the path an arcane recipe
 * needs - confirmed by reading the packet's own {@code getDesiredIngredients}.
 */
public class ArcaneCraftingRecipeTransfer
        implements IRecipeTransferInfo<MenuArcaneCraftingTerminal, RecipeHolder<?>>,
                IRecipeTransferHandler<MenuArcaneCraftingTerminal, RecipeHolder<?>> {

    private final IRecipeTransferHandlerHelper helper;

    public ArcaneCraftingRecipeTransfer(IRecipeTransferHandlerHelper helper) {
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
    public RecipeType<RecipeHolder<?>> getRecipeType() {
        return ArcaneRecipeTypes.arcane();
    }

    @Override
    public boolean canHandle(MenuArcaneCraftingTerminal menu, RecipeHolder<?> recipe) {
        return ArcaneRecipeTypes.fitsGrid(recipe);
    }

    /** The nine workbench cells, asked for by semantic. */
    @Override
    public List<Slot> getRecipeSlots(MenuArcaneCraftingTerminal menu, RecipeHolder<?> recipe) {
        List<Slot> grid = menu.getSlots(SlotSemantics.CRAFTING_GRID);
        return grid.size() == PartArcaneCraftingTerminal.GRID_SIZE ? grid : List.of();
    }

    @Override
    public List<Slot> getInventorySlots(MenuArcaneCraftingTerminal menu, RecipeHolder<?> recipe) {
        return menu.getSlots(SlotSemantics.PLAYER_INVENTORY);
    }

    // ---- IRecipeTransferHandler ----------------------------------------

    @Override
    public @Nullable IRecipeTransferError transferRecipe(
            MenuArcaneCraftingTerminal menu,
            RecipeHolder<?> holder,
            IRecipeSlotsView display,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        List<List<ItemStack>> cells = ArcaneRecipeTypes.cellsFor(holder);
        if (cells == null) {
            return helper.createInternalError();
        }
        if (getRecipeSlots(menu, holder).size() != PartArcaneCraftingTerminal.GRID_SIZE) {
            return helper.createInternalError();
        }

        // One template per cell, alongside which of them the player cannot supply. The packet decides what
        // to do about the missing ones; this only reports them, so the button can say why it will not work
        // rather than appearing to do nothing.
        NonNullList<ItemStack> templates = NonNullList.withSize(PartArcaneCraftingTerminal.GRID_SIZE, ItemStack.EMPTY);
        boolean missing = false;
        for (int cell = 0; cell < PartArcaneCraftingTerminal.GRID_SIZE; cell++) {
            List<ItemStack> variants = cell < cells.size() ? cells.get(cell) : List.of();
            for (ItemStack variant : variants) {
                if (!variant.isEmpty()) {
                    templates.set(cell, variant.copyWithCount(1));
                    break;
                }
            }
            if (templates.get(cell).isEmpty() && !variants.isEmpty()) {
                missing = true;
            }
        }

        // A recipe whose ingredients cannot be laid out is refused outright. The packet would fill what it
        // can, and a partly filled grid reads as "this terminal cannot craft that" rather than "you are
        // short of something" - a misleading thing to show for a fixable problem.
        if (missing) {
            return helper.createUserErrorWithTooltip(
                    Component.translatable("thaumicenergistics_ce.jei.transfer.missing_ingredients"));
        }
        if (!doTransfer) {
            return null;
        }

        // No recipe id: an arcane recipe is not in the vanilla manager, and the packet's template fallback
        // is the path that fits. See the class note.
        PacketDistributor.sendToServer(new FillCraftingGridFromRecipePacket(null, templates, false));
        return null;
    }
}
