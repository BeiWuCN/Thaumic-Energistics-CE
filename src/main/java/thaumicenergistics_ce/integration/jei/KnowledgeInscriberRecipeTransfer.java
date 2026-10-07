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
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;

/**
 * 让 JEI 的「转移配方」按钮用奥术工作台配方填充知识铭刻机的网格。
 * 处理器为 Thaumaturge 的奥术工作台分类注册，所以不会给它提供编码不了的配方；
 * 它同时带着两半：[IRecipeTransferInfo] 定位槽位，处理器决定并搬运它们。两者放在一起，
 * 它同时带着两半：[IRecipeTransferInfo] 定位槽位，处理器决定并搬运它们。
 * 两者放在一起，因为映射不是简单复制——水晶需求还要额外的槽位。
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
     * 玩家的 36 个槽位，在这个菜单里排在最前：JEI 读它们来判断玩家是否备齐原料，
     * 而转移从不移动它们——网格是幽灵网格。
     */
    @Override
    public List<Slot> getInventorySlots(MenuKnowledgeInscriber menu, RecipeHolder<?> recipe) {
        return menu.slots.subList(0, PLAYER_SLOTS);
    }

    // ---- IRecipeTransferHandler ----------------------------------------

    @Override
    // JEI 19.57 里旧的 6 参数 [transferRecipe] 是接口唯一的抽象方法
    @SuppressWarnings("removal")
    public @Nullable IRecipeTransferError transferRecipe(
            MenuKnowledgeInscriber menu,
            RecipeHolder<?> holder,
            IRecipeSlotsView display,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        // 原料取自配方自己的网格，而不是 JEI 画出的槽位：重读显示内容意味着
        // 依赖那个分类选择如何摆放它。
        List<List<ItemStack>> cells = ArcaneRecipeTypes.cellsFor(holder);
        if (cells == null) {
            return helper.createInternalError();
        }
        // 先模拟：必须先确认机器可用才动它；放在确认配方可编码之后检查，
        // 好让没有核心的菜单也能拿到置灰的转移按钮。
        if (!menu.canEncode()) {
            return helper.createUserErrorWithTooltip(
                    Component.translatable("thaumicenergistics_ce.gui.knowledge_inscriber.transfer_unavailable"));
        }
        // 多个变体时：放玩家确实拥有的那个，否则放第一个。放置标签中任意一个成员，
        // 正是转移把配方并不接受的东西填进网格的原因。
        List<ItemStack> chosen = new ArrayList<>(BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT);
        for (List<ItemStack> variants : cells) {
            chosen.add(pickVariant(variants, menu));
        }
        if (!doTransfer) {
            return null;
        }
        // 通过菜单的槽位而不是它的容器：客户端的网格是幽灵网格，只写容器
        // 填的是一块服务端永远看不到的草稿。见 MenuKnowledgeInscriber。
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
