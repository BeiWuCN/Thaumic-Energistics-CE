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
 * The Infusion Provider's Jade tooltip: what the altar beside it can actually draw.
 * <ul>
 *   <li>The drawing half of {@link InfusionProviderProvider}. Both report the same {@link #getUid() UID},
 *       which is how Jade pairs the server data with this.
 *   <li>One aspect per entry: a chip with the amount in the corner, {@link AmountFormat#SLOT} figures.
 * </ul>
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
                    // Not registry-backed: leave the entry out rather than badge it as something else.
                    continue;
                }
                long amount = entry.getLong(InfusionProviderProvider.TAG_AMOUNT);
                // AE2's own abbreviation - "16K", "28M" - so a figure under an icon reads as in a terminal.
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

        // Last, because the machine holds nothing itself: the line a player needs right after placing one.
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

        private final Holder<IAspect> aspect;
        private final String badge;
        private final Vec2 size;

        private AspectIcon(Holder<IAspect> aspect, String badge) {
            this.aspect = aspect;
            this.badge = badge;
            // The badge is part of the width: Jade lays a row out by the sizes elements report, and the
            // first version reported only the chip's 16 pixels, so numbers printed on top of the next chip.
            this.size = new Vec2(CHIP + GAP + Minecraft.getInstance().font.width(badge), CHIP);
        }

        @Override
        public void render(GuiGraphics graphics, float x, float y, float delta, float alpha) {
            Font font = Minecraft.getInstance().font;
            AspectRendering.renderGui(graphics, font, (int) x, (int) y, aspect, 0.0F);
            // The chip's bottom line, so a row reads as one band of numbers. The amount above is zero
            // because this badge is the number, not the chip's own label.
            graphics.drawString(font, badge, (int) x + CHIP + GAP, (int) y + 9, 0xFFFFFFFF, true);
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

        /** Left, so a short last row lines up under the first. */
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
