package thaumicenergistics_ce.network;

import appeng.core.network.ClientboundPacket;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import thaumicenergistics_ce.ThEIds;

/**
 * 奥术合成终端网格要花多少 vis，发给画它的屏幕：只有服务端算得出来，
 * 屏幕自己猜的话，显示的数值会和随后的合成对不上。
 * @param containerId 它所属的菜单；发给已关闭屏幕的数据包被忽略
 * @param aspects 每个要素和它以 centivis 计的开销，按配方列出它们的顺序
 * @param crystals 要从六个晶体槽里扣掉的晶体，按配方列出的顺序
 */
public record ArcaneCraftCostPayload(
        int containerId, List<AspectCost> aspects, List<CrystalCost> crystals)
        implements ClientboundPacket {

    /**
     * 单个要素分担的开销，单位 centivis，也就是配方实际索要的单位：1 vis 等于一百 centivis。
     * 这里换成整数 vis 会取整，屏幕上的数最多能跟被扣的数差一个 vis。
     */
    public record AspectCost(Identifier aspect, int centivis) {}

    /**
     * 一次合成从六个晶体槽里扣掉的晶体，按要素记：晶体是整颗的，没有 centivis 那一层。
     * 只发要素名，屏幕自己拿注册表拼出那一颗晶体的物品堆来画。
     */
    public record CrystalCost(Identifier aspect, int count) {}

    public static final Type<ArcaneCraftCostPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ThEIds.MODID, "arcane_craft_cost"));

    private static final StreamCodec<RegistryFriendlyByteBuf, AspectCost> ASPECT_COST_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC,
                    AspectCost::aspect,
                    ByteBufCodecs.VAR_INT,
                    AspectCost::centivis,
                    AspectCost::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, CrystalCost> CRYSTAL_COST_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC,
                    CrystalCost::aspect,
                    ByteBufCodecs.VAR_INT,
                    CrystalCost::count,
                    CrystalCost::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, ArcaneCraftCostPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    ArcaneCraftCostPayload::containerId,
                    ASPECT_COST_CODEC.apply(ByteBufCodecs.list()),
                    ArcaneCraftCostPayload::aspects,
                    CRYSTAL_COST_CODEC.apply(ByteBufCodecs.list()),
                    ArcaneCraftCostPayload::crystals,
                    ArcaneCraftCostPayload::new);

    @Override
    public Type<ArcaneCraftCostPayload> type() {
        return TYPE;
    }

    /** 按一次合成将要收取的费用建载荷，丢开开销为零的要素和颗数为零的晶体。 */
    public static ArcaneCraftCostPayload of(
            int containerId,
            Map<ResourceKey<IAspect>, Integer> costs,
            AspectList crystals) {
        List<AspectCost> list = new ArrayList<>();
        // 键用 {@link ResourceKey}：配方指名的是要素键。只有位置会发出去，
        // 客户端拿自己的注册表解析名称和图标。
        costs.forEach((key, centivis) -> {
            if (centivis != null && centivis > 0) {
                list.add(new AspectCost(key.identifier(), centivis));
            }
        });
        List<CrystalCost> gems = new ArrayList<>();
        for (AspectInstance entry : crystals.entries()) {
            Identifier id = entry.aspect().unwrapKey().map(ResourceKey::identifier).orElse(null);
            if (id != null && entry.amount() > 0) {
                gems.add(new CrystalCost(id, entry.amount()));
            }
        }
        return new ArcaneCraftCostPayload(containerId, list, gems);
    }

    public static ArcaneCraftCostPayload none(int containerId) {
        return new ArcaneCraftCostPayload(containerId, List.of(), List.of());
    }
}
