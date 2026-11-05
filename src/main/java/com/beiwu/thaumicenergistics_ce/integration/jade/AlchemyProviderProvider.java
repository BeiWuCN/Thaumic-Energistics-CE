package com.beiwu.thaumicenergistics_ce.integration.jade;

import appeng.api.networking.IGridNode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProvider;

public class AlchemyProviderProvider implements IServerDataProvider<BlockAccessor> {

    public static final AlchemyProviderProvider INSTANCE = new AlchemyProviderProvider();

    public static final Identifier UID =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "alchemy_provider");

    public static final String TAG_RECEIVERS = "Receivers";

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
    public Identifier getUid() {
        return UID;
    }
}
