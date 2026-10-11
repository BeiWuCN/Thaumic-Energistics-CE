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

public record ArcaneCraftCostPayload(
        int containerId, List<AspectCost> aspects, List<CrystalCost> crystals)
        implements ClientboundPacket {

    public record AspectCost(Identifier aspect, int centivis) {}

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

    public static ArcaneCraftCostPayload of(
            int containerId,
            Map<ResourceKey<IAspect>, Integer> costs,
            AspectList crystals) {
        List<AspectCost> list = new ArrayList<>();
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
