package thaumicenergistics_ce.blockentity.gachabox;

import net.minecraft.network.chat.Component;

public enum GachaWait {
    NO_STRUCTURE("no_structure", "The upper half is missing"),
    NO_BRAIN("no_brain", "Not bound"),
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

    public boolean blankScreen() {
        return this == NO_STRUCTURE || this == NO_CHANNEL;
    }

    public Component label() {
        return Component.translatableWithFallback(PREFIX + reason, english);
    }
}
