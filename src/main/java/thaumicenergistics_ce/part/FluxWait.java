package thaumicenergistics_ce.part;

import net.minecraft.network.chat.Component;

/** Reasons shown to the player: every refusal the tick makes has one, so "idle" only ever means it worked. */
public enum FluxWait {
    NO_FLUX("no_flux", "Not enough flux in this chunk to draw"),
    NO_SPACE("no_room", "Not enough clear space in front of the interface"),
    LOW_ESSENTIA("no_essentia", "Not enough stable essentia to sustain the transfer"),
    NO_ENERGY("no_power", "Not enough energy"),
    NO_NETWORK("no_network", "Not on a network"),
    NO_PARTNER("no_partner", "No paired interface at the other end"),
    OUT_UNLOADED("out_unloaded", "The release end is not loaded"),
    OUT_OFFLINE("out_offline", "The release end has no power or channel"),
    OUT_BLOCKED("out_blocked", "Not enough clear space in front of the release end"),
    OUT_NO_LANDING(
            "out_no_landing", "The release end found no landing: its controller is not loaded");

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
