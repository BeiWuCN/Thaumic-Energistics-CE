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
                    continue;
                }
                long amount = entry.getLongOr(InfusionProviderProvider.TAG_AMOUNT, 0L);
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

    private static final class AspectIcon extends Element {

        private static final int CHIP = 16;
        private static final int GAP = 1;
        private static final float NUMBER_SCALE = 0.5F;

        private final Holder<IAspect> aspect;
        private final String badge;

        private AspectIcon(Holder<IAspect> aspect, String badge) {
            this.aspect = aspect;
            this.badge = badge;
            this.width = CHIP + GAP + (int) Math.ceil(Minecraft.getInstance().font.width(badge) * NUMBER_SCALE);
            this.height = CHIP;
            alignSelfStart();
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int left = getX();
            int top = getY();
            AspectRendering.renderGui(graphics, font, left, top, aspect, 0.0F);
            graphics.pose().pushMatrix();
            graphics.pose().translate(left + CHIP + GAP, top + CHIP - font.lineHeight * NUMBER_SCALE);
            graphics.pose().scale(NUMBER_SCALE, NUMBER_SCALE);
            graphics.text(font, badge, 0, 0, 0xFFFFFFFF, true);
            graphics.pose().popMatrix();
        }

        @Override
        public Component getNarration() {
            return Component.literal(badge);
        }
    }
}
