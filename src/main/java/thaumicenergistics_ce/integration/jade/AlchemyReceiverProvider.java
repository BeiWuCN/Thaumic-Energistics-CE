package thaumicenergistics_ce.integration.jade;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProviderConnection;

/**
 * 炼金接收器的 Jade 服务端数据：它绑到的供应器，没有则为空。它不持网格节点，
 * 故没有频道行，链路就是全部状态。画的那一半是 {@code client.jade.AlchemyReceiverTooltip}，
 * 两边都报 {@link #UID}。
 */
public class AlchemyReceiverProvider implements IServerDataProvider<BlockAccessor> {

    public static final AlchemyReceiverProvider INSTANCE = new AlchemyReceiverProvider();

    /** 与 {@code client.jade.AlchemyReceiverTooltip} 共用：Jade 按 UID 配对这两半。 */
    public static final Identifier UID =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "alchemy_receiver");

    /** {@link #appendServerData} 的传输格式，tooltip 那一半读回它。 */
    public static final String TAG_BOUND = "Bound";

    /** 只在 {@link #TAG_BOUND} 为 true 时写，缺失的坐标永远不会被读成原点。 */
    public static final String TAG_PROVIDER = "Provider";

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof BlockEntityAlchemyProviderConnection receiver)) {
            return;
        }
        // 读字段本身：[resolveProvider()] 会在玩家背后把链路修好或切断。
        BlockPos provider = receiver.linkedProvider();
        tag.putBoolean(TAG_BOUND, provider != null);
        if (provider != null) {
            tag.putLong(TAG_PROVIDER, provider.asLong());
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
