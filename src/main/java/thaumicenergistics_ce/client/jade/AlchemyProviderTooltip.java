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


/**
 * 炼金供应器的 Jade tooltip：网格够不够得到它，它把源质交给谁。
 * 它是 {@link AlchemyProviderProvider} 的绘制半边，两者靠共用的 UID 配对。
 * 画三行：网格状态、已绑定的无线接收器，以及连接花掉储备的进度条。
 */
public final class AlchemyProviderTooltip implements IBlockComponentProvider {

    public static final AlchemyProviderTooltip INSTANCE = new AlchemyProviderTooltip();

    /**
     * 储备条的填充色。Jade 26.1.8 的 [ProgressStyle] 不再收颜色（它只点名精灵），
     * 颜色改由 [ProgressView.Part] 自己带：不传 overlay 时 Jade 用内置的
     * `jade:progressbar` 精灵，并按 part 的颜色给它上色。颜色传 -1 就是「不指定」，
     * 于是条跟着提示框主题走 —— 那正是这条发白的原因。
     * 这里沿用 1.21.1 版的 AE 能量红（旧代码是 progressStyle().color(0xFFAA0000, 0xFF660000)）。
     */
    private static final int RESERVE_COLOUR = 0xFFAA0000;

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
        // 所以填充和写在上面的数字装在同一个对象里，颜色必须由 part 带着走
        // （见 RESERVE_COLOUR，漏了它这条就会是主题色、也就是发白）。
        int cache = tag.getIntOr(AlchemyProviderProvider.TAG_CACHE, 0);
        ProgressView view = new ProgressView(
                ProgressView.Part.of((float) Math.min(1.0, cache / BlockEntityAlchemyProvider.AE_CACHE),
                        RESERVE_COLOUR),
                Component.translatable("thaumicenergistics_ce.jade.alchemy_provider.reserve", cache,
                        (int) BlockEntityAlchemyProvider.AE_CACHE),
                JadeUI.progressStyle().canDecrease(true),
                BoxStyle.nestedBox());
        tooltip.add(JadeUI.progress(view));
    }

    @Override
    public Identifier getUid() {
        return AlchemyProviderProvider.UID;
    }
}
