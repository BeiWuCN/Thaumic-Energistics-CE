package thaumicenergistics_ce.integration.jei;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternEncodingTermMenu;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

/**
 * 把坩埚配方写进样板编码终端的处理样板：催化剂进第一格，一个要素一格源质。
 * 用 Thaumaturge 自己的坩埚分类，玩家才会在惯常位置找到转移按钮。
 * 写成处理样板而不是合成样板：坩埚要的要素不是九宫格里能摆下的东西。
 * 搬运交给 [EncodingHelper]：它把模式切到处理样板，再逐格发数据包；
 * 幽灵槽里的“物品”只是 [GenericStack] 的包装，玩家自己一格一格摆走的也是这条路。
 * 九个格装不下就整次拒绝，不部分填充：半填的样板看着像是“这条配方就是这样”。
 */
public class CruciblePatternTransfer
        implements IRecipeTransferInfo<PatternEncodingTermMenu, RecipeHolder<?>>,
                IRecipeTransferHandler<PatternEncodingTermMenu, RecipeHolder<?>> {

    private static final String TOO_LARGE = "thaumicenergistics_ce.jei.transfer.pattern_too_large";

    private final IRecipeTransferHandlerHelper helper;

    public CruciblePatternTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    // ---- IRecipeTransferInfo -------------------------------------------

    @Override
    public Class<? extends PatternEncodingTermMenu> getContainerClass() {
        return PatternEncodingTermMenu.class;
    }

    /**
     * 这个菜单类的任意菜单类型：样板编码终端有方块、线缆部件与无线三种形态，
     * 只写一个菜单类型会让另外两个没转移按钮。
     */
    @Override
    public Optional<MenuType<PatternEncodingTermMenu>> getMenuType() {
        return Optional.empty();
    }

    @Override
    public RecipeType<RecipeHolder<?>> getRecipeType() {
        return CrucibleJeiRecipeType.crucible();
    }

    @Override
    public boolean canHandle(PatternEncodingTermMenu menu, RecipeHolder<?> recipe) {
        return CrucibleRecipeTypes.isCrucible(recipe);
    }

    /** 处理输入是幽灵槽：JEI 拿它们判断玩家是否备齐原料，转移不往里放真物品。 */
    @Override
    public List<Slot> getRecipeSlots(PatternEncodingTermMenu menu, RecipeHolder<?> recipe) {
        return List.<Slot>of(menu.getProcessingInputSlots());
    }

    @Override
    public List<Slot> getInventorySlots(PatternEncodingTermMenu menu, RecipeHolder<?> recipe) {
        return menu.getSlots(SlotSemantics.PLAYER_INVENTORY);
    }

    // ---- IRecipeTransferHandler ----------------------------------------

    // JEI 19.57 里这个 6 参数的 [transferRecipe] 是接口唯一的抽象方法。
    @Override
    @SuppressWarnings("removal")
    public @Nullable IRecipeTransferError transferRecipe(
            PatternEncodingTermMenu menu,
            RecipeHolder<?> holder,
            IRecipeSlotsView display,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        List<List<GenericStack>> inputs = CrucibleRecipeTypes.inputsFor(holder);
        List<GenericStack> outputs = CrucibleRecipeTypes.outputsFor(holder);
        if (inputs == null || outputs == null) {
            return helper.createInternalError();
        }
        // 坩埚的要素数量没有上限，样板的输入格有：装不下就明说。
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
