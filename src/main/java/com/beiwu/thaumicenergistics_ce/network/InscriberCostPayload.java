package com.beiwu.thaumicenergistics_ce.network;

import appeng.core.network.ClientboundPacket;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.recipe.arcane.ThEArcanePattern;

/** Original recipe requirements, before assembler crystal substitution or discounts. */
public record InscriberCostPayload(
        int containerId, boolean hasRecipe, int vis, List<ArcaneCraftCostPayload.CrystalCost> crystals)
        implements ClientboundPacket {
    public static final Type<InscriberCostPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "inscriber_cost"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InscriberCostPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, InscriberCostPayload::containerId,
                    ByteBufCodecs.BOOL, InscriberCostPayload::hasRecipe,
                    ByteBufCodecs.VAR_INT, InscriberCostPayload::vis,
                    ArcaneCraftCostPayload.CRYSTAL_COST_CODEC.apply(ByteBufCodecs.list()),
                    InscriberCostPayload::crystals, InscriberCostPayload::new);

    public static InscriberCostPayload of(int containerId, @Nullable ThEArcanePattern pattern) {
        return pattern == null
                ? new InscriberCostPayload(containerId, false, 0, List.of())
                : new InscriberCostPayload(containerId, true, Math.max(0, pattern.baseVis()),
                        ArcaneCraftCostPayload.of(containerId, java.util.Map.of(), pattern.crystals()).crystals());
    }

    @Override public void handleOnClient(net.minecraft.world.entity.player.Player player) {
        com.beiwu.thaumicenergistics_ce.network.ClientSinks.acceptInscriberCost(this);
    }
    @Override
    public Type<InscriberCostPayload> type() {
        return TYPE;
    }
}
