package thaumicenergistics.integration.jei;

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
import thaumicenergistics.blockentity.BlockEntityKnowledgeInscriber;
import thaumicenergistics.init.ModMenuTypes;
import thaumicenergistics.menu.MenuKnowledgeInscriber;

/**
 * Lets JEI's "transfer recipe" button fill the Knowledge Inscriber's grid from an arcane workbench
 * recipe.
 *
 * <p>Registered for Thaumaturge's own arcane workbench category, so the player opens a recipe in JEI the
 * way they always do and presses the button - no separate JEI page, and no chance of a recipe being
 * offered that the machine cannot actually encode.
 *
 * <p>This is both the {@link IRecipeTransferInfo} and the handler, rather than the usual pair of classes.
 * The info half says where the recipe's slots are and where the player's are; the handler half decides
 * whether the transfer can happen and then makes it. Keeping them together matters here because the
 * mapping between them is not a straight copy: JEI's recipe view has more ingredient slots than the
 * machine has cells - an arcane recipe carries its crystal requirement as separate ingredient slots -
 * so the transfer has to read the recipe rather than mirror slot for slot.
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
        return ArcaneRecipeTypes.arcane();
    }

    @Override
    public boolean canHandle(MenuKnowledgeInscriber menu, RecipeHolder<?> recipe) {
        return ArcaneRecipeTypes.fitsGrid(recipe);
    }

    /** The nine grid cells, in reading order - the order a recipe's cells are written to and read from. */
    @Override
    public List<Slot> getRecipeSlots(MenuKnowledgeInscriber menu, RecipeHolder<?> recipe) {
        List<Slot> slots = new ArrayList<>(BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT);
        for (int cell = 0; cell < BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT; cell++) {
            slots.add(menu.slots.get(MenuKnowledgeInscriber.gridSlotIndex(cell)));
        }
        return slots;
    }

    /**
     * The player's 36 slots, which come first in this menu.
     *
     * <p>JEI uses these to work out whether the player has the ingredients. The transfer never moves
     * them, because the grid is a ghost grid - listing them is about availability, not about moving.
     */
    @Override
    public List<Slot> getInventorySlots(MenuKnowledgeInscriber menu, RecipeHolder<?> recipe) {
        return menu.slots.subList(0, PLAYER_SLOTS);
    }

    // ---- IRecipeTransferHandler ----------------------------------------

    @Override
    public @Nullable IRecipeTransferError transferRecipe(
            MenuKnowledgeInscriber menu,
            RecipeHolder<?> holder,
            IRecipeSlotsView display,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        // The ingredients come from the recipe's own grid, not from the slots JEI drew: the display is a
        // picture, and re-reading it would mean depending on how the category chose to lay it out.
        List<List<ItemStack>> cells = ArcaneRecipeTypes.cellsFor(holder);
        if (cells == null) {
            return helper.createInternalError();
        }
        // Simulation first: the machine must be usable before it is touched. Checked after the recipe is
        // known to be encodable, so a menu with no core still gets JEI's transfer button greyed out for
        // the right reason.
        if (!menu.canEncode()) {
            return helper.createUserErrorWithTooltip(
                    Component.translatable("thaumicenergistics.gui.knowledge_inscriber.transfer_unavailable"));
        }
        // An ingredient with several variants is placed as one the player actually has, falling back to
        // the first. Placing an arbitrary member of a tag is how a transfer ends up filling the grid with
        // something the recipe does not accept.
        List<ItemStack> chosen = new ArrayList<>(BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT);
        for (List<ItemStack> variants : cells) {
            chosen.add(pickVariant(variants, menu));
        }
        if (!doTransfer) {
            return null;
        }
        // Through the menu's slots, not its container: the client's grid is a ghost grid, so writing the
        // container only filled a scratch pad the server never sees. See MenuKnowledgeInscriber.
        menu.fillGridFromRecipe(chosen);
        return null;
    }

    /** The variant of an ingredient to place: one the player is carrying, else the first. */
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
