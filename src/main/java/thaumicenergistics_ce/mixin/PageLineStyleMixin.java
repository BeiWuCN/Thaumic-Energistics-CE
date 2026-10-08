package thaumicenergistics_ce.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Thaumaturge 为换行之后的页面行保存起始样式，并把同一样式既当起始样式、
 * 又当 {@code §r} 重置到的样式交给 StringDecomposer，
 * 于是在 {@code §l} 段中途开始的行在自己的重置之后仍是粗体；
 * 粗体字符步进比换行时量出的更宽，会被画到文本栏之外。
 * 本 mod 的行把格式写成文本里的代码，把那个样式改写成等价代码再清空它，
 * {@code §r} 就重置为无，分页器为该栏量出的宽度也回来了。
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

    /** 与客户端手写的代码相同，按原版解析代码的顺序排列。 */
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
