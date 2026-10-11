package thaumicenergistics_ce.recipe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSink;

public final class ResearchFormattingChecks {
    private ResearchFormattingChecks() {}

    public static void main(String[] args) throws Exception {
        Class<?> textType = Class.forName(
                "com.leclowndu93150.thaumaturge.client.render.research.PageParser$PageElement$Text");
        Class<?> renderer = Class.forName(
                "com.leclowndu93150.thaumaturge.client.screen.research.detail.page.PageTextLayout");
        Method render = renderer.getDeclaredMethod("lambda$drawTextLine$0", textType, FormattedCharSink.class);
        render.setAccessible(true);
        Style inherited = Style.EMPTY.withBold(true).withUnderlined(true).withColor(0x123456);
        List<Style> styles = new ArrayList<>();
        FormattedCharSink sink = (index, style, codePoint) -> {
            styles.add(style);
            return true;
        };
        Object text = textType.getConstructor(String.class, Style.class, boolean.class)
                .newInstance("续§r正文§l标题§r后文", inherited, false);
        render.invoke(null, text, sink);
        require(styles.size() == 7, "Unexpected rendered character count");
        require(styles.getFirst().equals(inherited), "Wrapped line lost its inherited style");
        for (int index : new int[] {1, 2, 5, 6}) {
            require(styles.get(index).equals(Style.EMPTY), "Reset retained formatting at " + index);
        }
        require(styles.get(3).isBold() && !styles.get(3).isUnderlined(), "Explicit bold heading failed");
        styles.clear();
        text = textType.getConstructor(String.class, Style.class, boolean.class)
                .newInstance("整行", inherited, false);
        render.invoke(null, text, sink);
        require(styles.stream().allMatch(inherited::equals), "Style was cleared without a reset");
        System.out.println("[research-format] PASS: inherited style, reset, heading and following prose");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
