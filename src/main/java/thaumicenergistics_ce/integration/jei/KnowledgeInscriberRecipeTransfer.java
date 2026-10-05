package thaumicenergistics_ce.integration.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;

/**
 * Lets JEI's "transfer recipe" button fill the Knowledge Inscriber's grid from an arcane workbench recipe.
 * <ul>
 * <li>Registered for Thaumaturge's arcane workbench category, so no recipe is offered it cannot encode.
 * <li>Both halves: {@link IRecipeTransferInfo} locates the slots, the handler decides and moves them.
 * <li>They stay together because the mapping is not a straight copy - the crystal requirement is extra slots.
 * </ul>
 */
public class KnowledgeInscriberRecipeTransfer
        implements IRecipeTransferInfo<MenuKnowledgeInscriber, RecipeHolder<?>>,
                IRecipeTransferHandler<MenuKnowledgeInscriber, RecipeHolder<?>> {

    private static final int PLAYER_SLOTS = 36;

    private final IRecipeTransferHandlerHelper helper;

    public KnowledgeInscriberRecipeTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    // ---- IRecipeTransferInfo -------------------------------------------

    @Override
    public Class<? extends MenuKnowledgeInscriber> getContainerClass() {
        return MenuKnowledgeInscriber.class;
    }

    @Override
    public Optional<MenuType<MenuKnowledgeInscriber>> getMenuType() {
        return Optional.of(ModMenuTypes.KNOWLEDGE_INSCRIBER.get());
    }

    @Override
    public RecipeType<RecipeHolder<?>> getRecipeType() {
        return ArcaneJeiRecipeType.arcane();
    }

    @Override
    public boolean canHandle(MenuKnowledgeInscriber menu, RecipeHolder<?> recipe) {
        return ArcaneRecipeTypes.fitsGrid(recipe);
    }

    @Override
    public List<Slot> getRecipeSlots(MenuKnowledgeInscriber menu, RecipeHolder<?> recipe) {
        List<Slot> slots = new ArrayList<>(BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT);
        for (int cell = 0; cell < BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT; cell++) {
            slots.add(menu.slots.get(MenuKnowledgeInscriber.gridSlotIndex(cell)));
        }
        return slots;
    }

    /**
     * The player's 36 slots, which come first in this menu: JEI reads them to work out whether the
     * player has the ingredients, and the transfer never moves them - the grid is a ghost grid.
     */
    @Override
    public List<Slot> getInventorySlots(MenuKnowledgeInscriber menu, RecipeHolder<?> recipe) {
        return menu.slots.subList(0, PLAYER_SLOTS);
    }

    // ---- IRecipeTransferHandler ----------------------------------------

    @Override
    // old 6-arg transferRecipe is the interface's only abstract method in JEI 19.57
    @SuppressWarnings("removal")
    public @Nullable IRecipeTransferError transferRecipe(
            MenuKnowledgeInscriber menu,
            RecipeHolder<?> holder,
            IRecipeSlotsView display,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        // Ingredients come from the recipe's own grid, not the slots JEI drew: re-reading the display would
        // mean depending on how the category chose to lay it out.
        List<List<ItemStack>> cells = ArcaneRecipeTypes.cellsFor(holder);
        if (cells == null) {
            return helper.createInternalError();
        }
        // Simulation first: the machine must be usable before it is touched, checked after the recipe is
        // known to be encodable so a menu with no core still gets the transfer button greyed out.
        if (!menu.canEncode()) {
            return helper.createUserErrorWithTooltip(
                    Component.translatable("thaumicenergistics_ce.gui.knowledge_inscriber.transfer_unavailable"));
        }
        // Several variants: place one the player actually has, else the first. Placing an arbitrary member
        // of a tag is how a transfer fills the grid with something the recipe does not accept.
        List<ItemStack> chosen = new ArrayList<>(BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT);
        for (List<ItemStack> variants : cells) {
            chosen.add(pickVariant(variants, menu));
        }
        if (!doTransfer) {
            return null;
        }
        // Through the menu's slots, not its container: the client's grid is a ghost grid, so writing the
        // container only fills a scratch pad the server never sees. See MenuKnowledgeInscriber.
        menu.fillGridFromRecipe(chosen);
        return null;
    }

    private static ItemStack pickVariant(List<ItemStack> variants, MenuKnowledgeInscriber menu) {
        if (variants.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (ItemStack variant : variants) {
            if (menu.playerHas(variant)) {
                return variant;
            }
        }
        return variants.getFirst();
    }
}
