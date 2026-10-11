package thaumicenergistics_ce.integration.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.types.IRecipeType;
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
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;

public class KnowledgeInscriberRecipeTransfer
        implements IRecipeTransferInfo<MenuKnowledgeInscriber, RecipeHolder<?>>,
                IRecipeTransferHandler<MenuKnowledgeInscriber, RecipeHolder<?>> {

    private static final int PLAYER_SLOTS = 36;

    private final IRecipeTransferHandlerHelper helper;

    public KnowledgeInscriberRecipeTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }


    @Override
    public Class<? extends MenuKnowledgeInscriber> getContainerClass() {
        return MenuKnowledgeInscriber.class;
    }

    @Override
    public Optional<MenuType<MenuKnowledgeInscriber>> getMenuType() {
        return Optional.of(ModMenuTypes.KNOWLEDGE_INSCRIBER.get());
    }

    @Override
    public IRecipeType<RecipeHolder<?>> getRecipeType() {
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

    @Override
    public List<Slot> getInventorySlots(MenuKnowledgeInscriber menu, RecipeHolder<?> recipe) {
        return menu.slots.subList(0, PLAYER_SLOTS);
    }


    @Override
    @SuppressWarnings("removal")
    public @Nullable IRecipeTransferError transferRecipe(
            MenuKnowledgeInscriber menu,
            RecipeHolder<?> holder,
            IRecipeSlotsView display,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        List<List<ItemStack>> cells = ArcaneRecipeTypes.cellsFor(holder);
        if (cells == null) {
            return helper.createInternalError();
        }
        if (!menu.canEncode()) {
            return helper.createUserErrorWithTooltip(
                    Component.translatable("thaumicenergistics_ce.gui.knowledge_inscriber.transfer_unavailable"));
        }
        List<ItemStack> chosen = new ArrayList<>(BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT);
        for (List<ItemStack> variants : cells) {
            chosen.add(pickVariant(variants, menu));
        }
        if (!doTransfer) {
            return null;
        }
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
