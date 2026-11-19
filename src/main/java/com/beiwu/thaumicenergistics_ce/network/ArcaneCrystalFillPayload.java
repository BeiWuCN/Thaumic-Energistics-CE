package com.beiwu.thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;

/**
 * 让服务端按当前网格把缺的魔力水晶从 ME 网络补进晶体槽。
 * JEI 转移只填九个格子，晶体槽是终端自己的容器，客户端碰不到，只能请服务端补。
 * 不携带数据：要补多少由服务端照着网格重新算，客户端说的数不可信。
 */
public record ArcaneCrystalFillPayload(int containerId) implements CustomPacketPayload {

    public static final Type<ArcaneCrystalFillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "arcane_crystal_fill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArcaneCrystalFillPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    ArcaneCrystalFillPayload::containerId,
                    ArcaneCrystalFillPayload::new);

    @Override
    public Type<ArcaneCrystalFillPayload> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (player.containerMenu instanceof MenuArcaneCraftingTerminal menu
                && menu.containerId == containerId) {
            menu.fillCrystalsFromNetwork();
        }
    }
}
