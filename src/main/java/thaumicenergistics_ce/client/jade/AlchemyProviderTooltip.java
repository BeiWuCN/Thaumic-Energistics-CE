package thaumicenergistics_ce.client.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElementHelper;
import snownee.jade.api.ui.ProgressStyle;
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProvider;
import thaumicenergistics_ce.integration.jade.AlchemyProviderProvider;
import thaumicenergistics_ce.integration.jade.JadeGridState;

/**
 * The Alchemy Provider's Jade tooltip: whether the grid reaches it and who it hands essentia to.
 * <ul>
 *   <li>The drawing half of {@link AlchemyProviderProvider}, paired with it by the shared UID.
 *   <li>Three lines: the grid state, the bound receivers, and a bar for the reserve the link spends.
 * </ul>
 */
public final class AlchemyProviderTooltip implements IBlockComponentProvider {

    public static final AlchemyProviderTooltip INSTANCE = new AlchemyProviderTooltip();

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (!tag.contains(JadeGridState.TAG)) {
            return;
        }
        IElementHelper helper = IElementHelper.get();
        JadeGridState state = JadeGridState.read(tag);
        tooltip.add(helper.text(state.label().copy().withStyle(state.colour())));

        int receivers = tag.getInt(AlchemyProviderProvider.TAG_RECEIVERS);
        if (receivers > 0) {
            tooltip.add(helper.text(Component
                    .translatable("thaumicenergistics_ce.jade.alchemy_provider.receivers", receivers,
                            BlockEntityAlchemyProvider.MAX_LINKED_RECEIVERS)
                    .withStyle(ChatFormatting.WHITE)));
        } else {
            tooltip.add(helper.text(Component
                    .translatable("thaumicenergistics_ce.jade.alchemy_provider.no_receivers")
                    .withStyle(ChatFormatting.WHITE)));
        }

        // A bar rather than a number, in the shape Jade draws for an energy buffer: plain progress()
        // is its arrow gauge, so the reserve gets a striped bar with the numbers written across it.
        int cache = tag.getInt(AlchemyProviderProvider.TAG_CACHE);
        ProgressStyle style = helper.progressStyle().color(0xFFAA0000, 0xFF660000);
        tooltip.add(helper.progress(
                (float) Math.min(1.0, cache / BlockEntityAlchemyProvider.AE_CACHE),
                Component.translatable("thaumicenergistics_ce.jade.alchemy_provider.reserve", cache,
                        (int) BlockEntityAlchemyProvider.AE_CACHE),
                style, BoxStyle.getNestedBox(), true));
    }

    @Override
    public ResourceLocation getUid() {
        return AlchemyProviderProvider.UID;
    }
}
