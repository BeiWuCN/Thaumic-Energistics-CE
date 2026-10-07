package thaumicenergistics_ce.blockentity.gachabox;

import net.minecraft.network.chat.Component;

/** Why the box is standing still: every refusal has one, so "on" only ever means it is between turns. */
public enum GachaWait {
    NO_STRUCTURE("no_structure", "The upper half is missing"),
    NO_BRAIN("no_brain", "Not bound"),
    // A brain can be there and still belong to nobody: what a save caught half way leaves behind.
    UNBOUND_BRAIN("unbound_brain", "Not bound"),
    NO_CHANNEL("no_channel", "No channel, or no power on the network"),
    OWNER_OFFLINE("owner_offline", "The player it is bound to is offline"),
    NO_POWER("no_power", "The box has not banked enough energy"),
    NO_ESSENTIA("no_essentia", "Not enough essentia");

    private static final String PREFIX = "jade.thaumicenergistics_ce.gacha_box.wait_reason.";

    private final String reason;
    private final String english;

    GachaWait(String reason, String english) {
        this.reason = reason;
        this.english = english;
    }

    /** A dead screen: the setup itself has to be fixed before there is anything to look at. */
    public boolean blankScreen() {
        return this == NO_STRUCTURE || this == NO_CHANNEL;
    }

    // The server picks the reason; only the client knows the player's language.
    public Component label() {
        return Component.translatableWithFallback(PREFIX + reason, english);
    }
}
