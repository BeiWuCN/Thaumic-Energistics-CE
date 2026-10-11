package thaumicenergistics_ce.client.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.JadeUI;
import snownee.jade.api.view.ProgressView;
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProvider;
import thaumicenergistics_ce.integration.jade.AlchemyProviderProvider;
import thaumicenergistics_ce.integration.jade.JadeGridState;


public final class AlchemyProviderTooltip implements IBlockComponentProvider {

    public static final AlchemyProviderTooltip INSTANCE = new AlchemyProviderTooltip();

    private static final snownee.jade.api.ui.Element ENERGY_FILL = JadeUI.horizontalTiledSprite(
            net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
            Identifier.fromNamespaceAndPath("jade", "energy_progress"), 16, 16);

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (!tag.contains(JadeGridState.TAG)) {
            return;
        }
        JadeGridState state = JadeGridState.read(tag);
        tooltip.add(JadeUI.text(state.label().copy().withStyle(state.colour())));

        int receivers = tag.getIntOr(AlchemyProviderProvider.TAG_RECEIVERS, 0);
        if (receivers > 0) {
            tooltip.add(JadeUI.text(Component
                    .translatable("thaumicenergistics_ce.jade.alchemy_provider.receivers", receivers,
                            BlockEntityAlchemyProvider.MAX_LINKED_RECEIVERS)
                    .withStyle(ChatFormatting.WHITE)));
        } else {
            tooltip.add(JadeUI.text(Component
                    .translatable("thaumicenergistics_ce.jade.alchemy_provider.no_receivers")
                    .withStyle(ChatFormatting.WHITE)));
        }

        int cache = tag.getIntOr(AlchemyProviderProvider.TAG_CACHE, 0);
        ProgressView view = new ProgressView(
                ProgressView.Part.of((float) Math.min(1.0, cache / BlockEntityAlchemyProvider.AE_CACHE),
                        ENERGY_FILL),
                Component.translatable("thaumicenergistics_ce.jade.alchemy_provider.reserve", cache,
                        (int) BlockEntityAlchemyProvider.AE_CACHE),
                JadeUI.progressStyle(),
                BoxStyle.nestedBox());
        tooltip.add(JadeUI.progress(view));
    }

    @Override
    public Identifier getUid() {
        return AlchemyProviderProvider.UID;
    }
}
