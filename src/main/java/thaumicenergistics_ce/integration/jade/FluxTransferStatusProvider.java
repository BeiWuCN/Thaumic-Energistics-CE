package thaumicenergistics_ce.integration.jade;

import appeng.api.integrations.igtooltip.PartTooltips;
import appeng.api.integrations.igtooltip.providers.ServerDataProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.part.FluxWait;
import thaumicenergistics_ce.part.PartFluxTransferInterface;

/**
 * The Flux Transfer Interface's tooltip data, written against AE2's part tooltip registry rather than
 * Jade's: Jade only knows block entities, and AE2's bridge hands the part under the crosshair over as
 * if it were one. That is why the two halves are registered from the mod constructor and the client
 * setup rather than from a {@code @WailaPlugin}, and why there is no UID to pair them by. The reason
 * travels as a component, not as its text, since the server picks the reason but cannot know the
 * player's language. The client half is {@code client.jade.FluxTransferTooltip}.
 */
public final class FluxTransferStatusProvider
        implements ServerDataProvider<PartFluxTransferInterface> {

    public static final FluxTransferStatusProvider INSTANCE = new FluxTransferStatusProvider();

    /** Read back by the drawing half, so the two names below are this class's own public contract. */
    public static final String TAG_WAIT = "WaitReason";

    public static final String TAG_WORKING = "Working";

    private FluxTransferStatusProvider() {}

    /** Called once, from the mod's constructor: the registry is keyed by part class, so it has to be
     * filled before any cable bus is looked at, and it is cheap enough to fill unconditionally. */
    public static void register() {
        PartTooltips.addServerData(PartFluxTransferInterface.class, INSTANCE);
    }

    @Override
    public void provideServerData(Player player, PartFluxTransferInterface part, CompoundTag tag) {
        FluxWait wait = part.waitReason();
        if (wait != null) {
            tag.put(TAG_WAIT, encode(wait.label()));
        }
        tag.putBoolean(TAG_WORKING, part.working());
    }

    private static Tag encode(Component component) {
        return ComponentSerialization.CODEC
                .encodeStart(NbtOps.INSTANCE, component)
                .result()
                .orElse(new CompoundTag());
    }
}
