package thaumicenergistics.integration.jade;

import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import java.util.ArrayList;
import java.util.Comparator;
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
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;
import thaumicenergistics.ThEIds;
import thaumicenergistics.blockentity.BlockEntityInfusionProvider;
import thaumicenergistics.integration.ae2.AEssentiaKey;

/**
 * The Infusion Provider's Jade tooltip: what the altar beside it can actually draw.
 *
 * <p>{@code getAspects} is empty on purpose - the block is a window, not a container, so essentia pipes do
 * not spend their time pumping it - which leaves this tooltip as the only place the network's contents show
 * up. One aspect per entry, as a chip with the amount in the corner; the amounts are the server's,
 * abbreviated with {@link AmountFormat#SLOT} so they read as they do in a terminal.
 */
public class InfusionProviderProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final InfusionProviderProvider INSTANCE = new InfusionProviderProvider();

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "infusion_provider");

    /** Server data keys; Kinds is the full count, before the MAX_ICONS cut. */
    private static final String TAG_ASPECT = "Aspect";
    private static final String TAG_AMOUNT = "Amount";
    private static final String TAG_KINDS = "Kinds";

    /** How many aspects are listed before the tooltip stops drawing them. */
    private static final int MAX_ICONS = 11;

    /** How many icons fit on one row. */
    private static final int PER_ROW = 6;

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof BlockEntityInfusionProvider provider)) {
            return;
        }
        IGridNode node = provider.getActionableNode();
        JadeGridState.of(node).write(tag, node);

        List<AspectAmount> held = new ArrayList<>();
        for (var entry : provider.visibleEssentia()) {
            AEKey key = entry.getKey();
            if (key instanceof AEssentiaKey essentia && entry.getLongValue() > 0) {
                held.add(new AspectAmount(essentia.getId(), entry.getLongValue()));
            }
        }
        held.sort(Comparator.comparingLong(AspectAmount::amount).reversed());
        tag.putInt(TAG_KINDS, held.size());

        ListTag list = new ListTag();
        for (AspectAmount amount : held.subList(0, Math.min(MAX_ICONS, held.size()))) {
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_ASPECT, amount.aspect().toString());
            entry.putLong(TAG_AMOUNT, amount.amount());
            list.add(entry);
        }
        tag.put("Held", list);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (!tag.contains(JadeGridState.TAG)) {
            return;
        }
        IElementHelper helper = IElementHelper.get();
        JadeGridState state = JadeGridState.read(tag);
        tooltip.add(helper.text(state.label().copy().withStyle(state.colour())));

        ListTag held = tag.getList("Held", Tag.TAG_COMPOUND);
        if (held.isEmpty()) {
            tooltip.add(helper.text(Component.translatable("thaumicenergistics.jade.infusion_provider.empty")
                    .withStyle(ChatFormatting.GRAY)));
        } else {
            List<IElement> row = new ArrayList<>();
            for (int i = 0; i < held.size(); i++) {
                CompoundTag entry = held.getCompound(i);
                Holder<IAspect> aspect = resolve(entry.getString(TAG_ASPECT));
                if (aspect == null) {
                    continue;
                }
                AEssentiaKey key = AEssentiaKey.of(aspect);
                if (key == null) {
                    // Not registry-backed: leave the entry out rather than badge it as something else.
                    continue;
                }
                long amount = entry.getLong(TAG_AMOUNT);
                // AE2's own abbreviation - "16K", "28M" - so a figure under an icon reads as in a terminal.
                String badge = key.formatAmount(amount, AmountFormat.SLOT);
                row.add(new AspectIcon(aspect, badge));
                if (row.size() >= PER_ROW) {
                    tooltip.add(row);
                    row = new ArrayList<>();
                }
            }
            if (!row.isEmpty()) {
                tooltip.add(row);
            }
            int kinds = tag.getInt(TAG_KINDS);
            if (kinds > held.size()) {
                tooltip.add(helper.text(Component.translatable(
                                "thaumicenergistics.jade.infusion_provider.more", kinds - held.size())
                        .withStyle(ChatFormatting.GRAY)));
            }
        }

        // Last, because the machine holds nothing itself: the line a player needs right after placing one.
        tooltip.add(helper.text(Component.translatable("thaumicenergistics.jade.infusion_provider.window")
                .withStyle(ChatFormatting.DARK_GRAY)));
    }

    /** The aspect behind an id, or {@code null} when this client's registry has never heard of it. */
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
        return UID;
    }

    /** One aspect the network holds and how much of it, for sorting and for the tag. */
    private record AspectAmount(ResourceLocation aspect, long amount) {}

    /**
     * An aspect's chip with its amount in the corner, drawn through Thaumaturge's own
     * {@code AspectRendering.renderGui} so the texture, the blend mode and the undiscovered-aspect mask are
     * all its. A crystal item cannot be told aspect from aspect at a glance.
     */
    private static final class AspectIcon implements IElement {

        /** Chip, gap, badge: the element is as wide as all three. */
        private static final int CHIP = 16;
        private static final int GAP = 1;

        private final Holder<IAspect> aspect;
        private final String badge;
        private final Vec2 size;

        private AspectIcon(Holder<IAspect> aspect, String badge) {
            this.aspect = aspect;
            this.badge = badge;
            // The badge is part of the width: Jade lays a row out by the sizes elements report, and the first
            // version reported only the chip's 16 pixels, so numbers printed on top of the next chip.
            this.size = new Vec2(CHIP + GAP + Minecraft.getInstance().font.width(badge), CHIP);
        }

        @Override
        public void render(GuiGraphics graphics, float x, float y, float delta, float alpha) {
            Font font = Minecraft.getInstance().font;
            AspectRendering.renderGui(graphics, font, (int) x, (int) y, aspect, 0.0F);
            // The chip's bottom line, so a row reads as one band of numbers. The amount passed above is zero
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
