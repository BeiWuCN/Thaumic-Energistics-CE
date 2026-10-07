package thaumicenergistics_ce.integration.jade;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProviderConnection;

/**
 * 炼金接收器的 Jade 服务端数据：它绑定到的供应器（如果有）。它不持有
 * 网格节点，因此没有频道行，链接就是全部状态。绘制的那一半是
 * {@code client.jade.AlchemyReceiverTooltip}，两者都上报 {@link #UID}。
 */
public class AlchemyReceiverProvider implements IServerDataProvider<BlockAccessor> {

    public static final AlchemyReceiverProvider INSTANCE = new AlchemyReceiverProvider();

    /** 与 {@code client.jade.AlchemyReceiverTooltip} 共用：Jade 按 UID 配对这两半。 */
    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "alchemy_receiver");

    /** {@link #appendServerData} 的传输格式。tooltip 那一半会把它读回。 */
    public static final String TAG_BOUND = "Bound";

    /** 仅在 {@link #TAG_BOUND} 为 true 时写入，这样缺失的坐标永远不会被读成原点。 */
    public static final String TAG_PROVIDER = "Provider";

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof BlockEntityAlchemyProviderConnection receiver)) {
            return;
        }
        // 读取字段本身：[resolveProvider()] 会在玩家背后把链接修复或切断。
        BlockPos provider = receiver.linkedProvider();
        tag.putBoolean(TAG_BOUND, provider != null);
        if (provider != null) {
            tag.putLong(TAG_PROVIDER, provider.asLong());
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
