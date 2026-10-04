package thaumicenergistics_ce.integration.jade;

import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.BlockEntityInfusionProvider;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;

/**
 * The Infusion Provider's Jade server data: what the altar beside it can actually draw.
 * <ul>
 *   <li>{@code getAspects} is empty on purpose: the block is a window, not a container, so pipes skip it.
 *   <li>The drawing half is {@code client.jade.InfusionProviderTooltip} - resolving an aspect id needs the
 *       client's level, so it cannot live here. Both report {@link #UID}, which is how Jade pairs the two.
 * </ul>
 */
public class InfusionProviderProvider implements IServerDataProvider<BlockAccessor> {

    public static final InfusionProviderProvider INSTANCE = new InfusionProviderProvider();

    /** Shared with {@code client.jade.InfusionProviderTooltip}: Jade pairs server data to a tooltip by UID. */
    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "infusion_provider");

    /** The wire format of {@link #appendServerData}. The tooltip half reads it back. */
    public static final String TAG_ASPECT = "Aspect";
    public static final String TAG_AMOUNT = "Amount";
    public static final String TAG_KINDS = "Kinds";
    public static final String TAG_HELD = "Held";

    private static final int MAX_ICONS = 11;

    public static final int PER_ROW = 6;

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof BlockEntityInfusionProvider provider)) {
            return;
        }
        IGridNode node = provider.getActionableNode();
        JadeGridState.of(node).write(tag, node);

        List<AspectAmount> held = new ArrayList<>();
        for (var entry : provider.visibleEssentia()) {
            AEKey key = entry.getKey();
            if (key instanceof AEssentiaKey essentia && entry.getLongValue() > 0) {
                held.add(new AspectAmount(essentia.getId(), entry.getLongValue()));
            }
        }
        held.sort(Comparator.comparingLong(AspectAmount::amount).reversed());
        tag.putInt(TAG_KINDS, held.size());

        ListTag list = new ListTag();
        for (AspectAmount amount : held.subList(0, Math.min(MAX_ICONS, held.size()))) {
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_ASPECT, amount.aspect().toString());
            entry.putLong(TAG_AMOUNT, amount.amount());
            list.add(entry);
        }
        tag.put(TAG_HELD, list);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    private record AspectAmount(ResourceLocation aspect, long amount) {}
}
