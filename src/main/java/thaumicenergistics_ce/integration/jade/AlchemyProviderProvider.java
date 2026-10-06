package thaumicenergistics_ce.integration.jade;

import appeng.api.networking.IGridNode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProvider;

/**
 * The Alchemy Provider's Jade server data: its grid state, its outlet, and its bound receivers.
 * <ul>
 *   <li>AE2 draws its own grid-state line from a package an addon cannot hook, so the line is written here.
 *   <li>The drawing half is {@code client.jade.AlchemyProviderTooltip}; both report {@link #UID}.
 * </ul>
 */
public class AlchemyProviderProvider implements IServerDataProvider<BlockAccessor> {

    public static final AlchemyProviderProvider INSTANCE = new AlchemyProviderProvider();

    /** Shared with {@code client.jade.AlchemyProviderTooltip}: Jade pairs the two halves by UID. */
    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "alchemy_provider");

    /** The wire format of {@link #appendServerData}. The tooltip half reads it back. */
    public static final String TAG_OUTLET = "Outlet";
    public static final String TAG_RECEIVERS = "Receivers";
    public static final String TAG_STUCK = "Stuck";

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof BlockEntityAlchemyProvider provider)) {
            return;
        }
        IGridNode node = provider.getActionableNode();
        JadeGridState.of(node).write(tag, node);
        tag.putBoolean(TAG_OUTLET, provider.hasOutput());
        tag.putInt(TAG_RECEIVERS, provider.linkedReceiverCount());
        // Kinds, not amounts: the buffer is a waypoint, so anything left sitting here means full neighbours.
        tag.putInt(TAG_STUCK, provider.contents().size());
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
