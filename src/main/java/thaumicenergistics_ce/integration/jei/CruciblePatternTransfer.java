package thaumicenergistics_ce.integration.jei;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternEncodingTermMenu;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

public class CruciblePatternTransfer
        implements IRecipeTransferInfo<PatternEncodingTermMenu, RecipeHolder<?>>,
                IRecipeTransferHandler<PatternEncodingTermMenu, RecipeHolder<?>> {

    private static final String TOO_LARGE = "thaumicenergistics_ce.jei.transfer.pattern_too_large";

    private final IRecipeTransferHandlerHelper helper;

    public CruciblePatternTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }


    @Override
    public Class<? extends PatternEncodingTermMenu> getContainerClass() {
        return PatternEncodingTermMenu.class;
    }

    @Override
    public Optional<MenuType<PatternEncodingTermMenu>> getMenuType() {
        return Optional.empty();
    }

    @Override
    public IRecipeType<RecipeHolder<?>> getRecipeType() {
        return CrucibleJeiRecipeType.crucible();
    }

    @Override
    public boolean canHandle(PatternEncodingTermMenu menu, RecipeHolder<?> recipe) {
        return CrucibleRecipeTypes.isCrucible(recipe);
    }

    @Override
    public List<Slot> getRecipeSlots(PatternEncodingTermMenu menu, RecipeHolder<?> recipe) {
        return List.<Slot>of(menu.getProcessingInputSlots());
    }

    @Override
    public List<Slot> getInventorySlots(PatternEncodingTermMenu menu, RecipeHolder<?> recipe) {
        return menu.getSlots(SlotSemantics.PLAYER_INVENTORY);
    }


    @Override
    @SuppressWarnings("removal")
    public @Nullable IRecipeTransferError transferRecipe(
            PatternEncodingTermMenu menu,
            RecipeHolder<?> holder,
            IRecipeSlotsView display,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        List<List<GenericStack>> inputs = CrucibleRecipeTypes.inputsFor(holder, player.level().registryAccess());
        List<GenericStack> outputs = CrucibleRecipeTypes.outputsFor(holder);
        if (inputs == null || outputs == null) {
            return helper.createInternalError();
        }
        if (inputs.size() > menu.getProcessingInputSlots().length
                || outputs.size() > menu.getProcessingOutputSlots().length) {
            return helper.createUserErrorWithTooltip(Component.translatable(TOO_LARGE));
        }
        if (!doTransfer) {
            return null;
        }
        EncodingHelper.encodeProcessingRecipe(menu, inputs, outputs);
        return null;
    }
}
