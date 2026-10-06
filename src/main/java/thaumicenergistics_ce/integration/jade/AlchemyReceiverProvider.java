package thaumicenergistics_ce.integration.jade;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProviderConnection;

/**
 * The Alchemy Receiver's Jade server data: the provider it is bound to, if any.
 * <ul>
 *   <li>It holds no grid node, so there is no channel line: the link is the whole state.
 *   <li>The drawing half is {@code client.jade.AlchemyReceiverTooltip}; both report {@link #UID}.
 * </ul>
 */
public class AlchemyReceiverProvider implements IServerDataProvider<BlockAccessor> {

    public static final AlchemyReceiverProvider INSTANCE = new AlchemyReceiverProvider();

    /** Shared with {@code client.jade.AlchemyReceiverTooltip}: Jade pairs the two halves by UID. */
    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "alchemy_receiver");

    /** The wire format of {@link #appendServerData}. The tooltip half reads it back. */
    public static final String TAG_BOUND = "Bound";

    /** Written only while {@link #TAG_BOUND} is true, so an absent position never reads as the origin. */
    public static final String TAG_PROVIDER = "Provider";

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof BlockEntityAlchemyProviderConnection receiver)) {
            return;
        }
        // Read the field: resolveProvider() would heal or sever the link behind the player's back.
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
