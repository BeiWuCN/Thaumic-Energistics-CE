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
 * 处理器只注册给 Thaumaturge 的奥术工作台分类，不会收到编码不了的配方。
 * 两半放在一起：[IRecipeTransferInfo] 定位槽位，处理器搬运它们。
 * 水晶需求还要额外的槽位，映射不是简单复制。
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
     * 玩家的 36 个槽位在本菜单里排最前。
     * JEI 靠它们判断玩家是否备齐原料；转移从不移动它们，网格是幽灵网格。
     */
    @Override
    public List<Slot> getInventorySlots(MenuKnowledgeInscriber menu, RecipeHolder<?> recipe) {
        return menu.slots.subList(0, PLAYER_SLOTS);
    }

    // ---- IRecipeTransferHandler ----------------------------------------

    @Override
    // JEI 19.57 的 6 参数 [transferRecipe] 是该接口唯一的抽象方法。
    @SuppressWarnings("removal")
    public @Nullable IRecipeTransferError transferRecipe(
            MenuKnowledgeInscriber menu,
            RecipeHolder<?> holder,
            IRecipeSlotsView display,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        // 原料取自配方自己的网格，不重读 JEI 画出的槽位。
        // 重读显示内容会依赖该分类摆放槽位的方式。
        List<List<ItemStack>> cells = ArcaneRecipeTypes.cellsFor(holder);
        if (cells == null) {
            return helper.createInternalError();
        }
        // 先模拟：确认机器可用才动它。
        // 这次检查排在看配方能不能编码之后，没有核心的菜单才会拿到置灰按钮。
        if (!menu.canEncode()) {
            return helper.createUserErrorWithTooltip(
                    Component.translatable("thaumicenergistics_ce.gui.knowledge_inscriber.transfer_unavailable"));
        }
        // 多个变体时：放玩家确实拥有的那个，都没有就放第一个。
        // 随便放标签里的一个成员，就会把配方并不接受的东西填进网格。
        List<ItemStack> chosen = new ArrayList<>(BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT);
        for (List<ItemStack> variants : cells) {
            chosen.add(pickVariant(variants, menu));
        }
        if (!doTransfer) {
            return null;
        }
        // 走菜单的槽位，不直接写容器。
        // 客户端的网格是幽灵网格，写容器只是服务端看不到的草稿，见 MenuKnowledgeInscriber。
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
