package thaumicenergistics_ce.network;

import appeng.core.network.ClientboundPacket;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.ThEIds;

/**
 * 奥术合成终端网格要花多少 vis，发给绘制它的屏幕：只有服务端能算出来，
 * 屏幕若自行猜测，显示的数值会与随后的合成对不上。
 *
 * @param containerId 它所属的菜单；发给已关闭屏幕的数据包被忽略
 * @param aspects 每个要素及其以 centivis 计的开销，按配方列出它们的顺序
 */
public record ArcaneCraftCostPayload(int containerId, List<AspectCost> aspects)
        implements ClientboundPacket {

    /**
     * 单个要素分担的开销，以 centivis 计——这是配方实际索要的单位，1 vis
     * 等于一百 centivis。在这里换算成整数 vis 会取整，屏幕上的数与被扣的数
     * 最多可能差整整一个 vis。
     */
    public record AspectCost(ResourceLocation aspect, int centivis) {}

    public static final Type<ArcaneCraftCostPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "arcane_craft_cost"));

    private static final StreamCodec<RegistryFriendlyByteBuf, AspectCost> ASPECT_COST_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC,
                    AspectCost::aspect,
                    ByteBufCodecs.VAR_INT,
                    AspectCost::centivis,
                    AspectCost::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, ArcaneCraftCostPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    ArcaneCraftCostPayload::containerId,
                    ASPECT_COST_CODEC.apply(ByteBufCodecs.list()),
                    ArcaneCraftCostPayload::aspects,
                    ArcaneCraftCostPayload::new);

    @Override
    public Type<ArcaneCraftCostPayload> type() {
        return TYPE;
    }

    /**
     * AE2 的另一个重载 {@code handleOnClient(IPayloadContext)} 会入队并转发到这一个：
     * 注册器本来就在主线程上运行，所以这里不得再入队一次。
     */
    @Override
    public void handleOnClient(Player player) {
        ClientSinks.acceptArcaneCraftCost(this);
    }

    /** 由一次合成将要收取的费用构建载荷，丢弃开销为零的要素。 */
    public static ArcaneCraftCostPayload of(
            int containerId,
            Map<ResourceKey<IAspect>, Integer> costs) {
        List<AspectCost> list = new ArrayList<>();
        // 映射的键是 {@link ResourceKey}，因为配方指名的是要素键。只有位置
        // 会上路；客户端用它自己的注册表解析出名称与图标。
        costs.forEach((key, centivis) -> {
            if (centivis != null && centivis > 0) {
                list.add(new AspectCost(key.location(), centivis));
            }
        });
        return new ArcaneCraftCostPayload(containerId, list);
    }

    public static ArcaneCraftCostPayload none(int containerId) {
        return new ArcaneCraftCostPayload(containerId, List.of());
    }
}
