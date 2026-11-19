package com.beiwu.thaumicenergistics_ce.client;

import appeng.recipes.handlers.InscriberRecipe;
import com.leclowndu93150.thaumaturge.client.render.research.RecipeDisplayWidget.ItemHit;
import com.leclowndu93150.thaumaturge.client.screen.TTScreenTextures;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;

/** Machine recipe pages use 1.21.1 holders and preserve the book artwork and hover targets. */
public final class ThaumicMachineRecipes {
    private ThaumicMachineRecipes() {}
    public static boolean supports(RecipeHolder<?> holder) {
        return holder.value() instanceof SmithingRecipe || holder.value() instanceof InscriberRecipe;
    }
    public static void render(GuiGraphics graphics, int x, int y, RecipeHolder<?> holder) {
        int cx=x+52, cy=y+52;
        graphics.pose().pushPose();
        graphics.pose().translate(cx,cy,0);
        graphics.pose().scale(2,2,1);
        graphics.blit(TTScreenTextures.RESEARCH_BOOK_OVERLAY,-26,-26,60,15,51,52,TTScreenTextures.TEX_SIZE,TTScreenTextures.TEX_SIZE);
        graphics.blit(TTScreenTextures.RESEARCH_BOOK_OVERLAY,-8,-46,20,3,16,16,TTScreenTextures.TEX_SIZE,TTScreenTextures.TEX_SIZE);
        graphics.pose().popPose();
        var font=Minecraft.getInstance().font;
        Component title=Component.translatable(holder.value() instanceof SmithingRecipe ? "block.minecraft.smithing_table" : "block.ae2.inscriber");
        graphics.drawString(font,title,cx-font.width(title)/2,cy-104,0xFF504E50,false);
        for(ItemHit hit:hits(x,y,holder)) {
            graphics.renderItem(hit.stack(),hit.x(),hit.y());
            graphics.renderItemDecorations(font,hit.stack(),hit.x(),hit.y());
        }
    }
    public static List<ItemHit> hits(int x,int y,RecipeHolder<?> holder) {
        var level=Minecraft.getInstance().level;
        if(level==null)return List.of();
        var hits=new ArrayList<ItemHit>();
        int cx=x+52,cy=y+52;
        if(holder.value() instanceof InscriberRecipe recipe) {
            collect(hits,recipe.getTopOptional(),cx-8,cy-40);
            collect(hits,recipe.getMiddleInput(),cx-8,cy-8);
            collect(hits,recipe.getBottomOptional(),cx-8,cy+24);
        } else {
            var ingredients=holder.value() instanceof com.beiwu.thaumicenergistics_ce.mixin.SmithingRecipeIngredientsAccessor access
                    ? List.of(access.tce$template(),access.tce$base(),access.tce$addition()) : holder.value().getIngredients();
            for(int i=0;i<Math.min(3,ingredients.size());i++)collect(hits,ingredients.get(i),cx-40+i*32,cy-8);
        }
        var output=holder.value().getResultItem(level.registryAccess());
        if(!output.isEmpty())hits.add(new ItemHit(output,cx-8,cy-84));
        return hits;
    }
    private static void collect(List<ItemHit> hits,Ingredient ingredient,int x,int y) {
        ItemStack[] choices=ingredient.getItems();
        if(choices.length==0)return;
        long time=Minecraft.getInstance().level.getGameTime();
        hits.add(new ItemHit(choices[(int)((time/20)%choices.length)],x,y));
    }
}
