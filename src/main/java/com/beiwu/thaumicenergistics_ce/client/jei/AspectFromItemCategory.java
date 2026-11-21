package com.beiwu.thaumicenergistics_ce.client.jei;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.compat.jei.category.AspectFromStacksCategory;
import com.leclowndu93150.thaumaturge.compat.jei.ingredient.AspectIngredientRenderer;
import com.leclowndu93150.thaumaturge.compat.jei.ingredient.AspectIngredientType;
import com.leclowndu93150.thaumaturge.registry.TTItems;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.integration.jei.AspectFromItemRecipe;

/**
 * 「从物品提取要素」：一件物品 -> 它蒸馏出的要素。
 *
 * <p>版式不另画，直接拿神秘使的 {@link AspectFromStacksCategory} 当模板调用它的 {@code draw}：
 * 同一张 {@code gui_inner.png} 内页、同一圈魔导手册书页纹样、同样半透明。区别只在槽位——
 * 那张页是"要素在上、含它的物品铺在下面的九格"，这张对调过来：
 * 物品放在原来要素那一格，要素铺在原来物品的那片九格里。
 * 要素图标也用同一套 {@link AspectIngredientRenderer}（魔导手册里的要素图标 + 数量）。
 *
 * <p>转移目标就是 ME 样板编码终端（{@code PatternEncodingTermMenu}）的处理编码页。
 */
public final class AspectFromItemCategory implements IRecipeCategory<AspectFromItemRecipe> {

    public static final ResourceLocation UID = ThEIds.id("aspect_from_item");

    public static final RecipeType<AspectFromItemRecipe> RECIPE_TYPE =
            new RecipeType<>(UID, AspectFromItemRecipe.class);

    /** 一页最多排这么多个要素：两行九列，再多就截断。 */
    public static final int MAX_ASPECTS = 18;

    /** 原页面的要素槽：现在放蒸馏原料。 */
    private static final int INPUT_X = 80;
    private static final int INPUT_Y = 8;

    /** 原页面的物品九格：现在铺要素。 */
    private static final int GRID_X = 6;
    private static final int GRID_Y = 32;
    private static final int GRID_PITCH = 18;
    private static final int GRID_COLUMNS = 9;

    private final IDrawable icon;

    /** 远程借用的那张页，只用来画底纹和要素框。 */
    private final AspectFromStacksCategory template;

    public AspectFromItemCategory(IGuiHelper helper) {
        // 分类图标跟神秘使那张页一样用魔导手册本体。
        this.icon = helper.createDrawableItemStack(new ItemStack(TTItems.THAUMONOMICON.get()));
        this.template = new AspectFromStacksCategory(helper);
    }

    @Override
    public RecipeType<AspectFromItemRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.thaumicenergistics_ce.aspect_from_item");
    }

    @Override
    public int getWidth() {
        return template.getWidth();
    }

    @Override
    public int getHeight() {
        return template.getHeight();
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AspectFromItemRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(INPUT_X, INPUT_Y).addItemStack(recipe.source());
        List<AspectInstance> aspects = recipe.aspects();
        for (int i = 0; i < aspects.size() && i < MAX_ASPECTS; i++) {
            int column = i % GRID_COLUMNS;
            int row = i / GRID_COLUMNS;
            builder.addOutputSlot(GRID_X + column * GRID_PITCH, GRID_Y + row * GRID_PITCH)
                    .setCustomRenderer(AspectIngredientType.INSTANCE, AspectIngredientRenderer.INSTANCE)
                    .addIngredient(AspectIngredientType.INSTANCE, aspects.get(i));
        }
    }

    @Override
    public void draw(
            AspectFromItemRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        // 顶部那圈纹样是这张页的装饰，借原页面的 draw 原样画出来。
        List<AspectInstance> aspects = recipe.aspects();
        if (aspects.isEmpty()) {
            return;
        }
        Holder<IAspect> decoration = aspects.get(0).aspect();
        template.draw(
                new AspectFromStacksCategory.Wrapper(decoration, List.of()),
                slots,
                graphics,
                mouseX,
                mouseY);
    }
}
