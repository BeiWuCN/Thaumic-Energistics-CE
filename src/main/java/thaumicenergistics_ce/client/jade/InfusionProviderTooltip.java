package thaumicenergistics_ce.client.jade;

import appeng.api.stacks.AmountFormat;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.Element;
import snownee.jade.api.ui.JadeUI;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.jade.InfusionProviderProvider;
import thaumicenergistics_ce.integration.jade.JadeGridState;

/**
 * 注魔供应器的 Jade tooltip：旁边的祭坛实际能抽到什么。
 * 它是 {@link InfusionProviderProvider} 的绘制半边，两边报同一个 {@link #getUid() UID}，
 * Jade 靠它配对服务端数据。
 * 条目一次一个要素：一个图标，数量标在角上，格式用 {@link AmountFormat#SLOT}。
 */
public final class InfusionProviderTooltip implements IBlockComponentProvider {

    public static final InfusionProviderTooltip INSTANCE = new InfusionProviderTooltip();

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (!tag.contains(JadeGridState.TAG)) {
            return;
        }
                JadeGridState state = JadeGridState.read(tag);
        tooltip.add(JadeUI.text(state.label().copy().withStyle(state.colour())));

        ListTag held = tag.getListOrEmpty(InfusionProviderProvider.TAG_HELD);
        if (held.isEmpty()) {
            tooltip.add(JadeUI.text(Component.translatable("thaumicenergistics_ce.jade.infusion_provider.empty")
                    .withStyle(ChatFormatting.GRAY)));
        } else {
            List<Element> row = new ArrayList<>();
            for (int i = 0; i < held.size(); i++) {
                CompoundTag entry = held.getCompoundOrEmpty(i);
                Holder<IAspect> aspect = resolve(entry.getStringOr(InfusionProviderProvider.TAG_ASPECT, ""));
                if (aspect == null) {
                    continue;
                }
                AEssentiaKey key = AEssentiaKey.of(aspect);
                if (key == null) {
                    // 注册表里没有就整条不列，不给它标成别的东西。
                    continue;
                }
                long amount = entry.getLongOr(InfusionProviderProvider.TAG_AMOUNT, 0L);
                // 用 AE2 自己的缩写「16K」「28M」，图标下面的数字读起来像在终端里。
                String badge = key.formatAmount(amount, AmountFormat.SLOT);
                row.add(new AspectIcon(aspect, badge));
                if (row.size() >= InfusionProviderProvider.PER_ROW) {
                    tooltip.add(row);
                    row = new ArrayList<>();
                }
            }
            if (!row.isEmpty()) {
                tooltip.add(row);
            }
            int kinds = tag.getIntOr(InfusionProviderProvider.TAG_KINDS, 0);
            if (kinds > held.size()) {
                tooltip.add(JadeUI.text(Component.translatable(
                                "thaumicenergistics_ce.jade.infusion_provider.more", kinds - held.size())
                        .withStyle(ChatFormatting.GRAY)));
            }
        }

        // 放最后：这台机器本身不存东西，刚放下它时玩家最需要看的就是这行。
        tooltip.add(JadeUI.text(Component.translatable("thaumicenergistics_ce.jade.infusion_provider.window")
                .withStyle(ChatFormatting.DARK_GRAY)));
    }

    private static Holder<IAspect> resolve(String id) {
        Identifier location = Identifier.tryParse(id);
        if (location == null || Minecraft.getInstance().level == null) {
            return null;
        }
        return Aspects.resolve(
                Minecraft.getInstance().level.registryAccess(),
                ResourceKey.create(IAspect.REGISTRY_KEY, location));
    }

    @Override
    public Identifier getUid() {
        return InfusionProviderProvider.UID;
    }

    /**
     * 一个要素小图标，数量写在旁边。
     * <ul>
     *   <li>Jade 26.1.8 把 {@code IElement} 接口换成了 {@code Element} 类，它的契约是两个方法：
     *       报一段朗读文本，以及在交给你的位置上提取渲染状态。尺寸现在是一个字段，不再是一打重写。
     *   <li><b>坐标要用自己的 {@code getX()}/{@code getY()}，不能用传进来的那两个 int。</b>
     *       {@code BoxElementImpl.extractRenderState} 遍历子元素时，给每个子元素传的是同一个
     *       {@code Vector2i}，也就是整个提示框的原点；真正的位置是布局在排完行之后用
     *       {@code setX}/{@code setY} 写进元素自己的。Jade 自带的元素（{@code ItemStackElement}、
     *       {@code ProgressElement}、{@code TextElementImpl}、{@code SpriteElement}）一律只用自身坐标。
     *       1.21.1 那一版用的是老接口 {@code IElement.render(graphics, x, y, delta, alpha)}，
     *       那里的 x/y 恰好就是元素自己的位置，照抄过来就会把整排图标叠画在提示框左上角。
     *   <li>角标算进宽度：Jade 按各元素上报的宽度排一行，
     *       最早的版本只报了图标的 16 像素，数字就压到下一个图标上。
     *   <li>左对齐，短的最后一行和第一行对齐。
     * </ul>
     */
    private static final class AspectIcon extends Element {

        private static final int CHIP = 16;
        private static final int GAP = 1;

        private final Holder<IAspect> aspect;
        private final String badge;

        private AspectIcon(Holder<IAspect> aspect, String badge) {
            this.aspect = aspect;
            this.badge = badge;
            this.width = CHIP + GAP + Minecraft.getInstance().font.width(badge);
            this.height = CHIP;
            alignSelfStart();
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {
            Font font = Minecraft.getInstance().font;
            // 传进来的 x/y 是提示框原点，不是这个图标的位置；布局把自己的坐标写进了这两个字段。
            int left = getX();
            int top = getY();
            AspectRendering.renderGui(graphics, font, left, top, aspect, 0.0F);
            // 图标最下一行，一行读起来就是一整条数字。上面的数量写 0，
            // 因为这个角标才是那个数字，不是图标自己的标签。
            graphics.text(font, badge, left + CHIP + GAP, top + 9, 0xFFFFFFFF, true);
        }

        @Override
        public Component getNarration() {
            return Component.literal(badge);
        }
    }
}
