package thaumicenergistics_ce.network;

import appeng.api.stacks.GenericStack;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.menu.implementations.InterfaceMenu;
import appeng.util.ConfigInventory;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.util.ThELog;

/**
 * 「把这个要素放进那个接口槽位」，玩家从 JEI 中拖一个丢到接口上时发出。
 * 该槽位总在配置行里，也正是卡片靠其标记把邻居拉进来的那一行。
 * 要素以 id 上路，因为槽位写入要经过
 * {@code AEItemKey}，而它会丢弃不是物品的键。
 */
public record EssentiaInterfaceMarkPayload(int containerId, int index, ResourceLocation aspectId)
        implements CustomPacketPayload {

    public static final Type<EssentiaInterfaceMarkPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "essentia_interface_mark"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EssentiaInterfaceMarkPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    EssentiaInterfaceMarkPayload::containerId,
                    ByteBufCodecs.VAR_INT,
                    EssentiaInterfaceMarkPayload::index,
                    ResourceLocation.STREAM_CODEC,
                    EssentiaInterfaceMarkPayload::aspectId,
                    EssentiaInterfaceMarkPayload::new);

    public static final ResourceLocation CLEAR = ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "clear");

    @Override
    public Type<EssentiaInterfaceMarkPayload> type() {
        return TYPE;
    }

    /** 应用该标记，但仅当这个菜单所属的接口里装有访问卡时。 */
    public void handle(Player player) {
        if (!(player.containerMenu instanceof InterfaceMenu menu) || menu.containerId != containerId) {
            return;
        }
        // 没有卡片时这一行属于 AE2 自己，放个源质键进去毫无意义。
        if (!menu.getUpgrades().isInstalled(ModItems.ESSENTIA_ACCESS_CARD.get())) {
            return;
        }
        if (!(menu.getHost() instanceof InterfaceLogicHost host)) {
            return;
        }
        InterfaceLogic logic = host.getInterfaceLogic();
        ConfigInventory target = logic.getConfig();
        if (index < 0 || index >= target.size()) {
            ThELog.LOG.debug("[essentia-interface] mark {} is out of range", index);
            return;
        }
        if (CLEAR.equals(aspectId)) {
            target.setStack(index, null);
            return;
        }
        Holder<IAspect> aspect = Aspects.resolve(
                player.level(),
                ResourceKey.create(IAspect.REGISTRY_KEY, aspectId));
        if (aspect == null) {
            ThELog.LOG.warn("[essentia-interface] the server cannot resolve aspect {}", aspectId);
            return;
        }
        AEssentiaKey key = AEssentiaKey.of(aspect);
        if (key == null) {
            // 没有注册表背书：没有 id，该标记也就永远匹配不到任何东西。
            ThELog.LOG.warn("[essentia-interface] aspect {} is not a registry entry", aspectId);
            return;
        }
        target.setStack(index, new GenericStack(key, 1));
    }
}
