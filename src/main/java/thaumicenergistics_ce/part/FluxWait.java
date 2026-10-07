package thaumicenergistics_ce.part;

import net.minecraft.network.chat.Component;

/** Reasons shown to the player: silent refusals such as an unbound tunnel are not in this list. */
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

    // The server picks the reason; only the client knows the player's language.
    public Component label() {
        return Component.translatableWithFallback(PREFIX + reason, english);
    }
}
