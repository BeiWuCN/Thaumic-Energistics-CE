package thaumicenergistics_ce.integration.jade;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProviderConnection;

public class AlchemyReceiverProvider implements IServerDataProvider<BlockAccessor> {

    public static final AlchemyReceiverProvider INSTANCE = new AlchemyReceiverProvider();

    public static final Identifier UID =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "alchemy_receiver");

    public static final String TAG_BOUND = "Bound";

    public static final String TAG_PROVIDER = "Provider";

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof BlockEntityAlchemyProviderConnection receiver)) {
            return;
        }
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
