package thaumicenergistics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics.ThEIds;
import thaumicenergistics.menu.MenuDistillationEncoder;

/**
 * What the Distillation Encoder screen asks the server to do.
 *
 * <p>A few instructions travel, and no state does. The aspect list is not sent in either direction: both
 * sides derive it from the source item, which vanilla's slot sync already carries, using the same lookup.
 * Sending it would be a second copy of the same answer, and the two could disagree after the item changed.
 *
 * <p>The selected aspect travels as an <em>index</em> into that derived list rather than as an aspect id,
 * for the same reason - an index cannot name an aspect the item does not have, so a stale or forged value
 * fails to resolve instead of selecting something impossible.
 *
 * @param containerId the menu this applies to, checked on arrival so a packet for a closed screen is
 *     ignored rather than applied to whatever is open now
 * @param action what to do; see the constants
 * @param value the selected aspect index for {@link #ACTION_SELECT}, unused otherwise
 */
public record EncoderActionPayload(int containerId, int action, int value) implements CustomPacketPayload {

    /** Picks the aspect at {@code value}, or clears the pick when it is negative. */
    public static final int ACTION_SELECT = 0;

    /** Writes one pattern from the current source item, aspect and blank. */
    public static final int ACTION_ENCODE = 1;

    /**
     * Moves one blank pattern from the player's inventory into the blank well.
     *
     * <p>For JEI's drag, and it has to be a real move rather than the ghost write the source well takes.
     * The source well is a template, so naming an item there costs nothing and gives nothing back; the
     * blank well is not - what lands there is consumed by the next encode and can be taken back out - so a
     * drag that conjured a pattern from JEI would be a way to mint them.
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
     * Applies this instruction to the open menu.
     *
     * <p>Every action still re-checks its own preconditions on the server. The screen greys out what it can,
     * but a screen is a suggestion - the item, the blank pattern and the aspect are all validated again by
     * the block entity before anything is consumed, which is what keeps a client from writing patterns it
     * should not be able to.
     */
    public void handle(net.minecraft.world.entity.player.Player player) {
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
