package com.beiwu.thaumicenergistics_ce.network;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.menu.MenuArcaneEncodingTerminal;

/**
 * 把 JEI 选好的九个模板填进编码终端的网格。
 * 只填模板，不搬运物品：编码网格存的是"要编什么"，不是真实库存；
 * 服务端收到的内容只用于解析奥术配方，不参与扣费。
 */
public record ArcaneEncodingFillPayload(int containerId, List<ItemStack> cells) implements CustomPacketPayload {

    public static final Type<ArcaneEncodingFillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "arcane_encoding_fill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArcaneEncodingFillPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    ArcaneEncodingFillPayload::containerId,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()),
                    ArcaneEncodingFillPayload::cells,
                    ArcaneEncodingFillPayload::new);

    @Override
    public Type<ArcaneEncodingFillPayload> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (player.containerMenu instanceof MenuArcaneEncodingTerminal menu
                && menu.containerId == containerId) {
            menu.applyEncodedGrid(cells);
        }
    }
}
