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

        // 用进度条不用数字，形状沿用 Jade 给能量缓冲画的那种：
        // 普通的 [progress()] 是箭头仪表，储备改用带条纹的条，数字写在条上面。
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
