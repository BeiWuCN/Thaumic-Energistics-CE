package thaumicenergistics_ce.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Thaumaturge 保存每个换行后页面行的起始样式，并把同一样式既作为起始样式、又作为
 * {@code §r} 重置到的样式交给 StringDecomposer，于是在 {@code §l} 段中途开始的行
 * 在自己的重置之后仍然保持粗体；而粗体字符的步进比该行换行时所用的更宽，就会被画到
 * 文本栏之外。我们的行把格式作为代码写在文本里，因此把那个样式改写成等价的代码再
 * 清空它，就能让 {@code §r} 重置为无，
 * 并恢复分页器为该栏量出的宽度。
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

    /** 与客户端手写的代码相同，按原版解析它们的顺序排列。 */
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
