package thaumicenergistics_ce.client.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProvider;
import thaumicenergistics_ce.integration.jade.AlchemyProviderProvider;
import thaumicenergistics_ce.integration.jade.JadeGridState;

/**
 * The Alchemy Provider's Jade tooltip: whether the grid reaches it and who it hands essentia to.
 * <ul>
 *   <li>The drawing half of {@link AlchemyProviderProvider}, paired with it by the shared UID.
 *   <li>Three lines: the grid state, the bound receivers, and the AE the link still has to spend.
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

        // An empty reserve is why nothing is moving, so it is drawn red rather than as a plain number.
        int cache = tag.getInt(AlchemyProviderProvider.TAG_CACHE);
        tooltip.add(helper.text(Component
                .translatable("thaumicenergistics_ce.jade.alchemy_provider.cache", cache,
                        (int) BlockEntityAlchemyProvider.AE_CACHE)
                .withStyle(cache > 0 ? ChatFormatting.WHITE : ChatFormatting.RED)));
    }

    @Override
    public ResourceLocation getUid() {
        return AlchemyProviderProvider.UID;
    }
}
