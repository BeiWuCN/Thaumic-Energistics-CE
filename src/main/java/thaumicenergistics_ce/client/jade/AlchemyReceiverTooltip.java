package thaumicenergistics_ce.client.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.JadeUI;
import thaumicenergistics_ce.integration.jade.AlchemyReceiverProvider;

/**
 * 炼金无线接收器的 Jade tooltip：它绑定到哪个供应器。
 * 它是 {@link AlchemyReceiverProvider} 的绘制半边，两者靠共用的 UID 配对。
 * 接收器从不接在线上，不显示网格状态行；连接关系就是它能报告的全部。
 */
public final class AlchemyReceiverTooltip implements IBlockComponentProvider {

    public static final AlchemyReceiverTooltip INSTANCE = new AlchemyReceiverTooltip();

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (!tag.contains(AlchemyReceiverProvider.TAG_BOUND)) {
            return;
        }
                if (tag.getBooleanOr(AlchemyReceiverProvider.TAG_BOUND, false)) {
            BlockPos provider = BlockPos.of(tag.getLongOr(AlchemyReceiverProvider.TAG_PROVIDER, 0L));
            // 灰色：这是待查的地址，不是本方块自身的读数。
            tooltip.add(JadeUI.text(Component
                    .translatable("thaumicenergistics_ce.jade.alchemy_receiver.bound",
                            provider.getX(), provider.getY(), provider.getZ())
                    .withStyle(ChatFormatting.GRAY)));
            return;
        }
        tooltip.add(JadeUI.text(Component
                .translatable("thaumicenergistics_ce.jade.alchemy_receiver.unbound")
                .withStyle(ChatFormatting.WHITE)));
    }

    @Override
    public Identifier getUid() {
        return AlchemyReceiverProvider.UID;
    }
}
