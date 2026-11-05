package com.beiwu.thaumicenergistics_ce.client;

import appeng.recipes.handlers.InscriberRecipeDisplay;
import com.leclowndu93150.thaumaturge.client.render.research.RecipeDisplayWidget.ItemHit;
import com.leclowndu93150.thaumaturge.client.screen.TTScreenTextures;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;

/** Machine recipes keep the book's own artwork, output frame and hover targets. */
public final class ThaumicMachineRecipes {
    private ThaumicMachineRecipes() {}
    public static boolean supports(RecipeDisplay display) {
        return display instanceof SmithingRecipeDisplay || display instanceof InscriberRecipeDisplay;
    }

    public static void render(GuiGraphicsExtractor graphics, int x, int y, RecipeDisplay display) {
        int cx=x+52, cy=y+52;
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx,cy);
        graphics.pose().scale(2,2);
        graphics.blit(RenderPipelines.GUI_TEXTURED,TTScreenTextures.RESEARCH_BOOK_OVERLAY,
                -26,-26,60.0F,15.0F,51,52,51,52,TTScreenTextures.TEX_SIZE,TTScreenTextures.TEX_SIZE,0xFFFFFFFF);
        graphics.blit(RenderPipelines.GUI_TEXTURED,TTScreenTextures.RESEARCH_BOOK_OVERLAY,
                -8,-46,20.0F,3.0F,16,16,16,16,TTScreenTextures.TEX_SIZE,TTScreenTextures.TEX_SIZE,0xFFFFFFFF);
        graphics.pose().popMatrix();
        var font=Minecraft.getInstance().font;
        Component title=Component.translatable(display instanceof SmithingRecipeDisplay
                ? "block.minecraft.smithing_table" : "block.ae2.inscriber");
        graphics.text(font,title,cx-font.width(title)/2,cy-104,0xFF504E50,false);
        for(ItemHit hit:hits(x,y,display)) {
            graphics.item(hit.stack(),hit.x(),hit.y());
            graphics.itemDecorations(font,hit.stack(),hit.x(),hit.y());
        }
    }

    public static List<ItemHit> hits(int x,int y,RecipeDisplay display) {
        var minecraft=Minecraft.getInstance();
        if(minecraft.level==null)return List.of();
        var context=SlotDisplayContext.fromLevel(minecraft.level);
        var hits=new ArrayList<ItemHit>();
        int cx=x+52,cy=y+52;
        if(display instanceof SmithingRecipeDisplay smithing) {
            collect(hits,smithing.template(),context,cx-40,cy-8);
            collect(hits,smithing.base(),context,cx-8,cy-8);
            collect(hits,smithing.addition(),context,cx+24,cy-8);
        } else if(display instanceof InscriberRecipeDisplay inscriber) {
            collect(hits,inscriber.top(),context,cx-8,cy-40);
            collect(hits,inscriber.middle(),context,cx-8,cy-8);
            collect(hits,inscriber.bottom(),context,cx-8,cy+24);
        }
        collect(hits,display.result(),context,cx-8,cy-84);
        return hits;
    }

    private static void collect(List<ItemHit> hits,SlotDisplay display,
            net.minecraft.util.context.ContextMap context,int x,int y) {
        List<ItemStack> alternatives=display.resolveForStacks(context);
        if(alternatives.isEmpty())return;
        long time=Minecraft.getInstance().level.getGameTime();
        ItemStack stack=alternatives.get((int)((time/20)%alternatives.size()));
        if(!stack.isEmpty())hits.add(new ItemHit(stack,x,y));
    }
}
