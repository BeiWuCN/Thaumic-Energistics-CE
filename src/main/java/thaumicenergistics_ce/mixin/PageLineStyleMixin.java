package thaumicenergistics_ce.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Thaumaturge stores each wrapped page line's leading style and hands it to StringDecomposer as
 * both the starting style and the style a {@code §r} resets to, so a line starting inside a
 * {@code §l} run stays bold past its own reset and, bold advances being wider than the ones the
 * line was wrapped at, is drawn past the text column. Our lines carry formatting as codes in the
 * text, so rewriting that style into equivalent codes and emptying it makes {@code §r} reset to
 * nothing and restores the width the paginator measured for the column.
 */
@Mixin(targets = "com.leclowndu93150.thaumaturge.client.render.research.PageParser$Paginator")
public abstract class PageLineStyleMixin {

    @ModifyArgs(
            method = "feed(Ljava/lang/String;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/leclowndu93150/thaumaturge/client/render/research/PageParser$Paginator;appendLine(Ljava/lang/String;Lnet/minecraft/network/chat/Style;)V"))
    private void tce$carryLineStyleInText(Args args) {
        Style style = args.get(1);
        if (style == null || style.isEmpty()) {
            return;
        }
        args.set(0, tce$legacyCodes(style) + (String) args.get(0));
        args.set(1, Style.EMPTY);
    }

    /** Same codes a client would have written by hand, in the order vanilla parses them. */
    private static String tce$legacyCodes(Style style) {
        StringBuilder codes = new StringBuilder(8);
        TextColor color = style.getColor();
        if (color != null) {
            for (ChatFormatting formatting : ChatFormatting.values()) {
                Integer rgb = formatting.getColor();
                if (rgb != null && rgb == color.getValue()) {
                    codes.append('\u00a7').append(formatting.getChar());
                    break;
                }
            }
        }
        if (style.isObfuscated()) {
            codes.append("\u00a7k");
        }
        if (style.isBold()) {
            codes.append("\u00a7l");
        }
        if (style.isStrikethrough()) {
            codes.append("\u00a7m");
        }
        if (style.isUnderlined()) {
            codes.append("\u00a7n");
        }
        if (style.isItalic()) {
            codes.append("\u00a7o");
        }
        return codes.toString();
    }
}
