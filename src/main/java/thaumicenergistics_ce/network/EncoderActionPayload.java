package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.menu.MenuDistillationEncoder;

/**
 * What the Distillation Encoder screen asks the server to do: instructions, never state, since both
 * sides derive the aspect list from the source item the slot sync already carries.
 *
 * @param containerId the menu this applies to; a packet for a closed screen is ignored
 * @param action what to do; see the constants
 * @param value the selected aspect index for {@link #ACTION_SELECT}, unused otherwise
 */
public record EncoderActionPayload(int containerId, int action, int value) implements CustomPacketPayload {

    /** Picks the aspect at {@code value}, or clears the pick when it is negative. */
    public static final int ACTION_SELECT = 0;

    /** Writes one pattern from the current source item, aspect and blank. */
    public static final int ACTION_ENCODE = 1;

    /**
     * Moves one blank pattern into the blank well for JEI's drag. A drag that conjured one would mint
     * patterns, since the next encode consumes what lands there.
     */
    public static final int ACTION_INSERT_BLANK = 2;

    public static final Type<EncoderActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "encoder_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EncoderActionPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    EncoderActionPayload::containerId,
                    ByteBufCodecs.VAR_INT,
                    EncoderActionPayload::action,
                    ByteBufCodecs.VAR_INT,
                    EncoderActionPayload::value,
                    EncoderActionPayload::new);

    @Override
    public Type<EncoderActionPayload> type() {
        return TYPE;
    }

    /**
     * Applies this instruction to the open menu. Every action re-checks its own preconditions on the
     * server: a screen is a suggestion, and the block entity validates again before anything is spent.
     */
    public void handle(Player player) {
        if (!(player.containerMenu instanceof MenuDistillationEncoder menu) || menu.containerId != containerId) {
            return;
        }
        switch (action) {
            case ACTION_SELECT -> menu.selectAspect(value);
            case ACTION_ENCODE -> menu.encode();
            case ACTION_INSERT_BLANK -> menu.insertBlankFromInventory(player);
            default -> {
                // An action this build does not know: ignore it rather than guess.
            }
        }
    }
}
