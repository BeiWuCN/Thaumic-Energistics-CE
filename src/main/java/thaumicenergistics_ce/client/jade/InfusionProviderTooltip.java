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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.jade.InfusionProviderProvider;
import thaumicenergistics_ce.integration.jade.JadeGridState;

/**
 * 注魔供应器的 Jade tooltip：旁边的祭坛实际能抽取到什么。
 * 它是 {@link InfusionProviderProvider} 的绘制半边，两者报告同一个
 * {@link #getUid() UID}，Jade 借此把服务端数据与它配对。条目一次一个
 * 要素：一个图标，数量标在角上，采用 {@link AmountFormat#SLOT} 的数字格式。
 */
public final class InfusionProviderTooltip implements IBlockComponentProvider {

    public static final InfusionProviderTooltip INSTANCE = new InfusionProviderTooltip();

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (!tag.contains(JadeGridState.TAG)) {
            return;
        }
        IElementHelper helper = IElementHelper.get();
        JadeGridState state = JadeGridState.read(tag);
        tooltip.add(helper.text(state.label().copy().withStyle(state.colour())));

        ListTag held = tag.getList(InfusionProviderProvider.TAG_HELD, Tag.TAG_COMPOUND);
        if (held.isEmpty()) {
            tooltip.add(helper.text(Component.translatable("thaumicenergistics_ce.jade.infusion_provider.empty")
                    .withStyle(ChatFormatting.GRAY)));
        } else {
            List<IElement> row = new ArrayList<>();
            for (int i = 0; i < held.size(); i++) {
                CompoundTag entry = held.getCompound(i);
                Holder<IAspect> aspect = resolve(entry.getString(InfusionProviderProvider.TAG_ASPECT));
                if (aspect == null) {
                    continue;
                }
                AEssentiaKey key = AEssentiaKey.of(aspect);
                if (key == null) {
                    // 不由注册表支撑：宁可不列出该条目，也不要把它标成别的东西。
                    continue;
                }
                long amount = entry.getLong(InfusionProviderProvider.TAG_AMOUNT);
                // 用 AE2 自己的缩写——“16K”、“28M”——好让图标下面的数字读起来像在终端里。
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
            int kinds = tag.getInt(InfusionProviderProvider.TAG_KINDS);
            if (kinds > held.size()) {
                tooltip.add(helper.text(Component.translatable(
                                "thaumicenergistics_ce.jade.infusion_provider.more", kinds - held.size())
                        .withStyle(ChatFormatting.GRAY)));
            }
        }

        // 放在最后，因为这台机器本身不存东西：玩家刚放下它之后最需要看到的就是这行。
        tooltip.add(helper.text(Component.translatable("thaumicenergistics_ce.jade.infusion_provider.window")
                .withStyle(ChatFormatting.DARK_GRAY)));
    }

    private static Holder<IAspect> resolve(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || Minecraft.getInstance().level == null) {
            return null;
        }
        return Aspects.resolve(
                Minecraft.getInstance().level.registryAccess(),
                ResourceKey.create(IAspect.REGISTRY_KEY, location));
    }

    @Override
    public ResourceLocation getUid() {
        return InfusionProviderProvider.UID;
    }

    private static final class AspectIcon implements IElement {

        private static final int CHIP = 16;
        private static final int GAP = 1;
        /** 比图标小，所以一行数字位于图标下方而不是旁边。 */
        private static final float BADGE_SCALE = 0.75F;

        private final Holder<IAspect> aspect;
        private final String badge;
        private final Vec2 size;

        private AspectIcon(Holder<IAspect> aspect, String badge) {
            Font font = Minecraft.getInstance().font;
            this.aspect = aspect;
            this.badge = badge;
            // 角标算在宽度里：Jade 按各元素上报的尺寸排一行，而第一版只上报了图标的
            // 16 像素，于是数字压到了下一个图标上。
            this.size = new Vec2(CHIP + GAP + Math.round(font.width(badge) * BADGE_SCALE), CHIP);
        }

        @Override
        public void render(GuiGraphics graphics, float x, float y, float delta, float alpha) {
            Font font = Minecraft.getInstance().font;
            AspectRendering.renderGui(graphics, font, (int) x, (int) y, aspect, 0.0F);
            // 图标的最下一行，这样一行读起来就是一整条数字。上面的数量为 0，
            // 因为这个角标才是那个数字，而不是图标自己的标签。
            graphics.pose().pushPose();
            graphics.pose().translate(x + CHIP + GAP, y + 9, 0.0F);
            graphics.pose().scale(BADGE_SCALE, BADGE_SCALE, 1.0F);
            graphics.drawString(font, badge, 0, 0, 0xFFFFFFFF, true);
            graphics.pose().popPose();
        }

        @Override
        public Vec2 getSize() {
            return size;
        }

        @Override
        public Vec2 getCachedSize() {
            return size;
        }

        @Override
        public IElement size(Vec2 size) {
            return this;
        }

        @Override
        public IElement align(IElement.Align alignment) {
            return this;
        }

        /** 左对齐，让短的最后一行与第一行对齐。 */
        @Override
        public IElement.Align getAlignment() {
            return IElement.Align.LEFT;
        }

        @Override
        public IElement translate(Vec2 translation) {
            return this;
        }

        @Override
        public Vec2 getTranslation() {
            return Vec2.ZERO;
        }

        @Override
        public IElement tag(ResourceLocation tag) {
            return this;
        }

        @Override
        public ResourceLocation getTag() {
            return null;
        }

        @Override
        public String getCachedMessage() {
            return badge;
        }

        @Override
        public String getMessage() {
            return badge;
        }

        @Override
        public IElement clearCachedMessage() {
            return this;
        }

        @Override
        public IElement message(String message) {
            return this;
        }
    }
}
