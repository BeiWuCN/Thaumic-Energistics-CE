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
 * 注魔供应器的 Jade 服务端数据：它旁边的注魔祭坛实际能抽取什么。
 * [getAspects] 刻意为空，因为这个方块是一扇窗口而不是容器，管道会跳过它。绘制那一半是
 * [client.jade.InfusionProviderTooltip]，它不能放在这里，因为解析要素 id 需要客户端的 level；
 * 两边都上报 [UID]，Jade 就是靠它把两者配对的。
 * 没有这一对，tooltip 就挂不到方块上。
 */
public class InfusionProviderProvider implements IServerDataProvider<BlockAccessor> {

    public static final InfusionProviderProvider INSTANCE = new InfusionProviderProvider();

    /** 与 [client.jade.InfusionProviderTooltip] 共享：Jade 按 [UID] 配对这两半。 */
    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "infusion_provider");

    /** [appendServerData] 的线上格式。tooltip 那一半把它读回来。 */
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
