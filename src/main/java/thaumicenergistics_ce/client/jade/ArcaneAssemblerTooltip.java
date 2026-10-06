package thaumicenergistics_ce.client.jade;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.integration.jade.ArcaneAssemblerProvider;
import thaumicenergistics_ce.integration.jade.JadeGridState;

/**
 * The Arcane Assembler's Jade tooltip: the drawing half of {@link ArcaneAssemblerProvider}.
 * Everything it needs is in the data tag the server wrote, and the registry lookups are the
 * client's own, which is why this half lives in the client tree. Both halves report
 * {@link ArcaneAssemblerProvider#UID}, which is how Jade pairs them.
 */
public final class ArcaneAssemblerTooltip implements IBlockComponentProvider {

    public static final ArcaneAssemblerTooltip INSTANCE = new ArcaneAssemblerTooltip();

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (!tag.contains(JadeGridState.TAG)) {
            // Only reachable if the block entity was not the assembler. Better nothing than a wrong line.
            return;
        }
        var helper = IElementHelper.get();

        JadeGridState state = JadeGridState.read(tag);
        tooltip.add(helper.text(state.label().copy().withStyle(state.colour())));

        if (tag.getBoolean(ArcaneAssemblerProvider.TAG_CRAFTING)) {
            tooltip.add(helper.text(
                    Component.translatable("jade.thaumicenergistics_ce.arcane_assembler.crafting")
                            .withStyle(ChatFormatting.WHITE)));
            // The arrow row: what goes in on the left and what comes out on the right, so "what is it
            // making" and "out of what" need no sentence; full-size icons because Jade's sprite is 22x16.
            ListTag inputs = tag.getList(ArcaneAssemblerProvider.TAG_INPUTS, Tag.TAG_COMPOUND);
            List<IElement> row = new ArrayList<>();
            var level = accessor.getLevel();
            if (level != null) {
                var registries = level.registryAccess();
                for (int i = 0; i < inputs.size(); i++) {
                    ItemStack input = ItemStack.parseOptional(registries, inputs.getCompound(i));
                    if (!input.isEmpty()) {
                        row.add(helper.item(input));
                    }
                }
            }
            row.add(helper.progress(tag.getFloat(ArcaneAssemblerProvider.TAG_PROGRESS)));
            if (level != null) {
                ItemStack product = ItemStack.parseOptional(level.registryAccess(),
                        tag.getCompound(ArcaneAssemblerProvider.TAG_TARGET_STACK));
                if (!product.isEmpty()) {
                    row.add(helper.item(product));
                }
            }
            tooltip.add(row);
            String target = tag.getString(ArcaneAssemblerProvider.TAG_TARGET);
            if (!target.isEmpty()) {
                tooltip.add(helper.text(
                        Component.translatable("jade.thaumicenergistics_ce.arcane_assembler.produces",
                                        Component.translatable(target))
                                .withStyle(ChatFormatting.GRAY)));
            }
        }

        // One line, not two: banked and drawable answer the same question, and split they read as two
        // facts to compare. The first number is a cache, not plain vis.
        tooltip.add(helper.text(Component.translatable(
                        "jade.thaumicenergistics_ce.arcane_assembler.vis",
                        tag.getInt(ArcaneAssemblerProvider.TAG_VIS),
                        BlockEntityArcaneAssembler.visBufferTarget(),
                        tag.getInt(ArcaneAssemblerProvider.TAG_AURA))
                .withStyle(ChatFormatting.GRAY)));

        int discount = tag.getInt(ArcaneAssemblerProvider.TAG_DISCOUNT);
        if (discount > 0) {
            tooltip.add(helper.text(Component.translatable(
                            "jade.thaumicenergistics_ce.arcane_assembler.discount", discount)
                    .withStyle(ChatFormatting.GRAY)));
        }

        int speed = tag.getInt(ArcaneAssemblerProvider.TAG_SPEED);
        if (speed > 0) {
            tooltip.add(helper.text(Component.translatable(
                            "jade.thaumicenergistics_ce.arcane_assembler.speed", speed)
                    .withStyle(ChatFormatting.GRAY)));
        }

        tooltip.add(helper.text(Component.translatable(
                        "jade.thaumicenergistics_ce.arcane_assembler.patterns",
                        tag.getInt(ArcaneAssemblerProvider.TAG_PATTERNS))
                .withStyle(ChatFormatting.GRAY)));

        // A waiting machine and an idle one look identical from outside; the CPU waiting on it shows
        // only a stopped timer.
        Component wait = decode(tag, ArcaneAssemblerProvider.TAG_WAIT);
        if (wait != null) {
            tooltip.add(helper.text(Component.translatable(
                            "jade.thaumicenergistics_ce.arcane_assembler.waiting", wait)
                    .withStyle(ChatFormatting.GOLD)));
        }
        Component refusal = decode(tag, ArcaneAssemblerProvider.TAG_REFUSAL);
        if (refusal != null) {
            tooltip.add(helper.text(Component.translatable(
                            "jade.thaumicenergistics_ce.arcane_assembler.refused", refusal)
                    .withStyle(ChatFormatting.RED)));
        }
    }

    /**
     * The component back, or {@code null} when the tag is absent or unreadable: an empty line under
     * "waiting" claims the machine waits for nothing.
     */
    private static @Nullable Component decode(CompoundTag tag, String key) {
        Tag encoded = tag.get(key);
        if (encoded == null) {
            return null;
        }
        return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null);
    }

    @Override
    public ResourceLocation getUid() {
        return ArcaneAssemblerProvider.UID;
    }
}
