package thaumicenergistics_ce.integration.jade;

import appeng.api.networking.IGridNode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProvider;

/**
 * 炼金供应器的 Jade 服务端数据：它的网格状态以及绑定到它的接收器。AE2
 * 从附加 mod 无法挂钩的包里绘制自己的网格状态行，所以这一行写在这里。
 * 绘制的那一半是 {@code client.jade.AlchemyProviderTooltip}，两者都上报
 * {@link #UID}。
 */
public class AlchemyProviderProvider implements IServerDataProvider<BlockAccessor> {

    public static final AlchemyProviderProvider INSTANCE = new AlchemyProviderProvider();

    /** 与 {@code client.jade.AlchemyProviderTooltip} 共用：Jade 按 UID 配对这两半。 */
    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "alchemy_provider");

    /** {@link #appendServerData} 的传输格式。tooltip 那一半会把它读回。 */
    public static final String TAG_RECEIVERS = "Receivers";

    /** 供应器持有的 AE：未通电的网格会让该储备为空，而这一点也会显示出来。 */
    public static final String TAG_CACHE = "Cache";

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof BlockEntityAlchemyProvider provider)) {
            return;
        }
        IGridNode node = provider.getActionableNode();
        JadeGridState.of(node).write(tag, node);
        tag.putInt(TAG_RECEIVERS, provider.linkedReceiverCount());
        tag.putInt(TAG_CACHE, provider.cachedAE());
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
