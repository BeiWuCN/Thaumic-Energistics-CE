package thaumicenergistics_ce.client.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;
import thaumicenergistics_ce.integration.jade.AlchemyReceiverProvider;

/**
 * The Alchemy Receiver's Jade tooltip: the provider it is bound to, if any.
 * <ul>
 *   <li>The drawing half of {@link AlchemyReceiverProvider}, paired with it by the shared UID.
 *   <li>No grid state line: the receiver is never on a cable, so the link is all it can report.
 * </ul>
 */
public final class AlchemyReceiverTooltip implements IBlockComponentProvider {

    public static final AlchemyReceiverTooltip INSTANCE = new AlchemyReceiverTooltip();

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (!tag.contains(AlchemyReceiverProvider.TAG_BOUND)) {
            return;
        }
        IElementHelper helper = IElementHelper.get();
        if (tag.getBoolean(AlchemyReceiverProvider.TAG_BOUND)) {
            BlockPos provider = BlockPos.of(tag.getLong(AlchemyReceiverProvider.TAG_PROVIDER));
            // Grey: an address to look up, not a reading of this block.
            tooltip.add(helper.text(Component
                    .translatable("thaumicenergistics_ce.jade.alchemy_receiver.bound",
                            provider.getX(), provider.getY(), provider.getZ())
                    .withStyle(ChatFormatting.GRAY)));
            return;
        }
        tooltip.add(helper.text(Component
                .translatable("thaumicenergistics_ce.jade.alchemy_receiver.unbound")
                .withStyle(ChatFormatting.WHITE)));
    }

    @Override
    public ResourceLocation getUid() {
        return AlchemyReceiverProvider.UID;
    }
}
