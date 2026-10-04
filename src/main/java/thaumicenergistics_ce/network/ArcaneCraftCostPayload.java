package thaumicenergistics_ce.network;

import appeng.core.network.ClientboundPacket;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics_ce.ThEIds;

/**
 * What the Arcane Crafting Terminal's grid would cost in vis, sent to the screen drawing it.
 * <ul>
 * <li>Only the server can work it out - the cost depends on the recipe matched against the grid and
 * on the discounts the player's wand, equipment and curios apply - and a screen that guessed would
 * show a number the craft then disagrees with, which a player will trust and plan around.</li>
 * <li>The menu sends it whenever the grid changes rather than the screen polling: the cost only
 * changes with the grid, which changes far less often than a screen redraws.</li>
 * <li>Implements AE2's {@link appeng.core.network.ClientboundPacket} rather than only NeoForge's
 * payload interface, because that is what {@code AEBaseMenu.sendPacketToClient} accepts.</li>
 * </ul>
 *
 * @param containerId the menu this belongs to, checked on arrival so a packet for a closed screen is
 *     ignored rather than applied to whatever is open now
 * @param aspects each aspect and its cost in centivis, in the order the recipe listed them
 */
public record ArcaneCraftCostPayload(int containerId, List<AspectCost> aspects)
        implements ClientboundPacket {

    /**
     * One aspect's share of the cost, in centivis - the unit the recipe actually asks for, one vis
     * being a hundred centivis. Converting to whole vis here would round, so the number on screen and
     * the number charged could differ by up to a vis.
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
     * Builds a payload from what a craft would charge, dropping aspects that cost nothing.
     */
    public static ArcaneCraftCostPayload of(
            int containerId,
            Map<ResourceKey<IAspect>, Integer> costs) {
        List<AspectCost> list = new ArrayList<>();
        // The map's keys are {@link ResourceKey}s, because a recipe names aspect keys. Only the location
        // travels; the client resolves it against its own registries for a name and an icon.
        costs.forEach((key, centivis) -> {
            if (centivis != null && centivis > 0) {
                list.add(new AspectCost(key.location(), centivis));
            }
        });
        return new ArcaneCraftCostPayload(containerId, list);
    }

    /** An empty cost, for a grid that matches nothing. */
    public static ArcaneCraftCostPayload none(int containerId) {
        return new ArcaneCraftCostPayload(containerId, List.of());
    }
}
