package thaumicenergistics_ce.part;

import net.minecraft.network.chat.Component;

/**
 * Why a pair is not moving flux, as the player is told it. Refusals meant to be silent, such as an
 * unbound tunnel or an unloaded landing chunk, are not in this list. Each key carries its English
 * beside it so a missing translation never shows a raw key.
 */
public enum FluxWait {
    NO_FLUX("no_flux", "Not enough flux in this chunk to draw"),
    NO_SPACE("no_room", "Not enough room to vent"),
    LOW_ESSENTIA("no_essentia", "Not enough stable essentia to sustain the transfer"),
    NO_ENERGY("no_power", "Not enough energy"),
    NO_NETWORK("no_network", "Not on a network");

    private static final String PREFIX =
            "jade.thaumicenergistics_ce.flux_transfer_interface.wait_reason.";

    private final String reason;
    private final String english;

    FluxWait(String reason, String english) {
        this.reason = reason;
        this.english = english;
    }

    /** Built here because the server picks the reason and only the client knows the player's language. */
    public Component label() {
        return Component.translatableWithFallback(PREFIX + reason, english);
    }
}
