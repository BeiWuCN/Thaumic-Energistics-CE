package thaumicenergistics.network;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics.ThEIds;

/**
 * What the Arcane Crafting Terminal's grid would cost in vis, sent to the screen drawing it.
 *
 * <p>The figure has to travel because only the server can work it out: the cost depends on the recipe
 * matched against the grid, and on the discounts the player's wand, equipment and curios apply. None of
 * that is derivable on the client, and a screen that guessed would show a number the craft would then
 * disagree with - which is worse than showing nothing, because a player will trust it and plan around it.
 *
 * <p>Sent by the menu whenever the grid changes, rather than polled. The cost only changes when the grid
 * does, and the grid changes far less often than a screen redraws.
 *
 * <p>Implements AE2's {@link appeng.core.network.ClientboundPacket} rather than only NeoForge's payload
 * interface, because that is what {@code AEBaseMenu.sendPacketToClient} accepts - and the menu is where
 * this is sent from, since it already knows which player to send it to.
 *
 * @param containerId the menu this belongs to, checked on arrival so a packet for a closed screen is
 *     ignored rather than applied to whatever is open now
 * @param aspects each aspect and its cost in centivis, in the order the recipe listed them
 */
public record ArcaneCraftCostPayload(int containerId, List<AspectCost> aspects)
        implements appeng.core.network.ClientboundPacket {

    /**
     * One aspect's share of the cost.
     *
     * <p>Centivis, not vis. Thaumaturge works in hundredths internally - one vis is a hundred centivis - and
     * that is the unit the recipe actually asks for. Converting to whole vis here would round, and rounding
     * a cost means the number on screen and the number charged can differ by up to a vis.
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
     *
     * <p>Takes the cost's own map, whose keys are {@link ResourceKey}s - the recipe's cost is worked out
     * from aspect <em>keys</em>, because that is what a recipe names. Only the location travels: the client
     * resolves it against its own registries to get a name and an icon, which is the right way round when
     * the two sides could have different registry contents.
     */
    public static ArcaneCraftCostPayload of(
            int containerId,
            java.util.Map<net.minecraft.resources.ResourceKey<IAspect>, Integer> costs) {
        List<AspectCost> list = new ArrayList<>();
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
