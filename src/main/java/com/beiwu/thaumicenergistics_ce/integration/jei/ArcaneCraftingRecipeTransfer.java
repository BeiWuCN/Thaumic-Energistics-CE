package com.beiwu.thaumicenergistics_ce.integration.jei;

import appeng.api.stacks.AEItemKey;
import appeng.core.network.serverbound.FillCraftingGridFromRecipePacket;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.IClientRepo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import com.leclowndu93150.thaumaturge.registry.TTItems;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import com.beiwu.thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class ArcaneCraftingRecipeTransfer
        implements IRecipeTransferInfo<MenuArcaneCraftingTerminal, RecipeHolder<?>>,
                IRecipeTransferHandler<MenuArcaneCraftingTerminal, RecipeHolder<?>> {

    private final IRecipeTransferHandlerHelper helper;

    public ArcaneCraftingRecipeTransfer(
            IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }


    @Override
    public Class<? extends MenuArcaneCraftingTerminal> getContainerClass() {
        return MenuArcaneCraftingTerminal.class;
    }

    @Override
    public Optional<MenuType<MenuArcaneCraftingTerminal>> getMenuType() {
        // JEI keys handlers by menu class, which is shared by both terminal types.
        return Optional.empty();
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
        List<Integer> missingCells = new ArrayList<>();
        for (int cell = 0; cell < PartArcaneCraftingTerminal.GRID_SIZE; cell++) {
            List<ItemStack> variants = cell < cells.size() ? cells.get(cell) : List.of();
            templates.set(cell, pickSuppliable(variants, repo, player));
            if (templates.get(cell).isEmpty() && asksForSomething(variants)) {
                missingCells.add(cell);
            }
        }

        // 摆不下就整次拒绝，不部分填充：半填的网格看着像是这个终端合成不了，
        // 不像是在说缺料。缺的那几格照 AE2 自己的合成终端那样标红，
        // 玩家不用读提示就知道是哪一味没凑齐。
        if (!missingCells.isEmpty()) {
            List<IRecipeSlotView> inputs = display.getSlotViews(RecipeIngredientRole.INPUT);
            List<IRecipeSlotView> highlight = new ArrayList<>(missingCells.size());
            for (int cell : missingCells) {
                if (cell < inputs.size()) {
                    highlight.add(inputs.get(cell));
                }
            }
            Component message = Component.translatable("thaumicenergistics_ce.jei.transfer.missing_ingredients");
            return highlight.isEmpty()
                    ? helper.createUserErrorWithTooltip(message)
                    : helper.createUserErrorForMissingSlots(message, highlight);
        }
        if (!doTransfer) {
            return null;
        }

        // 不传配方 id：配方不在原版管理器里，只能走数据包的模板路径。
        ClientPacketDistributor.sendToServer(new FillCraftingGridFromRecipePacket(null, templates, false));
        if (maxTransfer) {
            menu.fillRecipeMax(holder.id().toString());
        } else {
            menu.fillRecipeCrystals(holder.id().toString());
        }
        return null;
    }


    private static ItemStack pickSuppliable(
            List<ItemStack> variants, @Nullable IClientRepo repo, Player player) {
        ItemStack best = ItemStack.EMPTY;
        long bestSupply = 0;
        for (ItemStack variant : expandCrystalVariants(variants, repo, player)) {
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

    private static List<ItemStack> expandCrystalVariants(
            List<ItemStack> variants, @Nullable IClientRepo repo, Player player) {
        if (variants.stream().noneMatch(stack -> stack.is(TTItems.ESSENTIA_CRYSTAL.get())
                && !stack.has(TTDataComponents.CRYSTAL_ASPECT.get()))) {
            return variants;
        }
        var expanded = new ArrayList<>(variants);
        Ingredient crystal = Ingredient.of(TTItems.ESSENTIA_CRYSTAL.get());
        if (repo != null) {
            for (GridInventoryEntry entry : repo.getByIngredient(crystal)) {
                if (entry.getWhat() instanceof AEItemKey key) {
                    expanded.add(key.toStack());
                }
            }
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (crystal.test(stack)) {
                expanded.add(stack);
            }
        }
        return expanded;
    }

    /** 网络对这个确切物品报的数量，加玩家身上带的。 */
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
