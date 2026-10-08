package thaumicenergistics_ce.client.jade;

import java.util.List;
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


/**
 * 炼金供应器的 Jade tooltip：网格够不够得到它，它把源质交给谁。
 * 它是 {@link AlchemyProviderProvider} 的绘制半边，两者靠共用的 UID 配对。
 * 画三行：网格状态、已绑定的无线接收器，以及连接花掉储备的进度条。
 */
public final class AlchemyProviderTooltip implements IBlockComponentProvider {

    public static final AlchemyProviderTooltip INSTANCE = new AlchemyProviderTooltip();

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

        // 用进度条而不是数字。Jade 26.1.8 用 ProgressView 建刻度，不再用裸分数，
        // 所以填充和写在上面的数字装在同一个对象里；旧的双色条纹样式没了，
        // ProgressStyle 现在点名的是精灵而不是颜色。
        int cache = tag.getIntOr(AlchemyProviderProvider.TAG_CACHE, 0);
        ProgressView view = new ProgressView(JadeUI.progressStyle().canDecrease(true), BoxStyle.nestedBox());
        view.text = Component.translatable("thaumicenergistics_ce.jade.alchemy_provider.reserve", cache,
                (int) BlockEntityAlchemyProvider.AE_CACHE);
        view.parts = List.of(
                ProgressView.Part.of((float) Math.min(1.0, cache / BlockEntityAlchemyProvider.AE_CACHE)));
        tooltip.add(JadeUI.progress(view));
    }

    @Override
    public Identifier getUid() {
        return AlchemyProviderProvider.UID;
    }
}
