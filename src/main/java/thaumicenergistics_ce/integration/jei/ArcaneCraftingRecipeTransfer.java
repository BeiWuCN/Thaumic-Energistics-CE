package thaumicenergistics_ce.integration.jei;

import appeng.api.stacks.AEItemKey;
import appeng.core.network.serverbound.FillCraftingGridFromRecipePacket;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.IClientRepo;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.types.IRecipeType;
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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class ArcaneCraftingRecipeTransfer
        implements IRecipeTransferInfo<MenuArcaneCraftingTerminal, RecipeHolder<?>>,
                IRecipeTransferHandler<MenuArcaneCraftingTerminal, RecipeHolder<?>> {

    private final IRecipeTransferHandlerHelper helper;
    private final MenuType<MenuArcaneCraftingTerminal> menuType;

    public ArcaneCraftingRecipeTransfer(
            IRecipeTransferHandlerHelper helper, MenuType<MenuArcaneCraftingTerminal> menuType) {
        this.helper = helper;
        this.menuType = menuType;
    }


    @Override
    public Class<? extends MenuArcaneCraftingTerminal> getContainerClass() {
        return MenuArcaneCraftingTerminal.class;
    }

    @Override
    public Optional<MenuType<MenuArcaneCraftingTerminal>> getMenuType() {
        return Optional.of(menuType);
    }

    @Override
    public IRecipeType<RecipeHolder<?>> getRecipeType() {
        return ArcaneJeiRecipeType.arcane();
    }

    @Override
    public boolean canHandle(MenuArcaneCraftingTerminal menu, RecipeHolder<?> recipe) {
        return ArcaneRecipeTypes.fitsGrid(recipe);
    }

    @Override
    public List<Slot> getRecipeSlots(MenuArcaneCraftingTerminal menu, RecipeHolder<?> recipe) {
        List<Slot> grid = menu.getSlots(SlotSemantics.CRAFTING_GRID);
        return grid.size() == PartArcaneCraftingTerminal.GRID_SIZE ? grid : List.of();
    }

    @Override
    public List<Slot> getInventorySlots(MenuArcaneCraftingTerminal menu, RecipeHolder<?> recipe) {
        return menu.getSlots(SlotSemantics.PLAYER_INVENTORY);
    }


    @Override
    @SuppressWarnings("removal")
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

        IClientRepo repo = menu.getClientRepo();
        NonNullList<ItemStack> templates = NonNullList.withSize(PartArcaneCraftingTerminal.GRID_SIZE, ItemStack.EMPTY);
        boolean missing = false;
        for (int cell = 0; cell < PartArcaneCraftingTerminal.GRID_SIZE; cell++) {
            List<ItemStack> variants = cell < cells.size() ? cells.get(cell) : List.of();
            templates.set(cell, pickSuppliable(variants, repo, player));
            if (templates.get(cell).isEmpty() && asksForSomething(variants)) {
                missing = true;
            }
        }

        if (missing) {
            return helper.createUserErrorWithTooltip(
                    Component.translatable("thaumicenergistics_ce.jei.transfer.missing_ingredients"));
        }
        if (!doTransfer) {
            return null;
        }

        ClientPacketDistributor.sendToServer(new FillCraftingGridFromRecipePacket(null, templates, false));
        return null;
    }


    private static ItemStack pickSuppliable(
            List<ItemStack> variants, @Nullable IClientRepo repo, Player player) {
        ItemStack best = ItemStack.EMPTY;
        long bestSupply = 0;
        for (ItemStack variant : variants) {
            if (variant.isEmpty()) {
                continue;
            }
            long supply = supplyOf(variant, repo, player);
            if (supply > bestSupply) {
                best = variant.copyWithCount(1);
                bestSupply = supply;
            }
        }
        return best;
    }

    private static long supplyOf(ItemStack variant, @Nullable IClientRepo repo, Player player) {
        long supply = 0;
        AEItemKey wanted = AEItemKey.of(variant);
        if (repo != null && wanted != null) {
            for (GridInventoryEntry entry : repo.getByIngredient(Ingredient.of(variant.getItem()))) {
                if (wanted.equals(entry.getWhat())) {
                    supply += entry.getStoredAmount();
                }
            }
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack carried = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(carried, variant)) {
                supply += carried.getCount();
            }
        }
        return supply;
    }

    private static boolean asksForSomething(List<ItemStack> variants) {
        for (ItemStack variant : variants) {
            if (!variant.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
