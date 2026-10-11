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
