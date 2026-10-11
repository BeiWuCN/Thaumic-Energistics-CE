package thaumicenergistics_ce.blockentity.assembler;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

public final class AssemblerStatus {

    private AssemblerStatus() {}

    static Component waitReason(String key, String english, Object... args) {
        return Component.translatableWithFallback(
                "jade.thaumicenergistics_ce.arcane_assembler.wait_reason." + key, english, args);
    }

    static Component refusalReason(String key, String english, Object... args) {
        return Component.translatableWithFallback(
                "jade.thaumicenergistics_ce.arcane_assembler.refuse_reason." + key, english, args);
    }


    static final String WAIT_NO_POWER = "no_power";
    static final String WAIT_NO_VIS = "no_vis";
    static final String WAIT_NO_CRYSTALS = "no_crystals";
    static final String WAIT_NO_CRYSTALS_RECHECK = "no_crystals_recheck";
    static final String WAIT_NO_ROOM = "no_room";
    static final String REFUSE_NODE_INACTIVE = "node_inactive";
    static final String REFUSE_BUSY = "busy";
    static final String REFUSE_NOT_ARCANE = "not_arcane";
    static final String REFUSE_UNRESOLVED = "unresolved";
    static final String REFUSE_TOO_EXPENSIVE = "too_expensive";

    public static List<String> tooltipReasonKeys() {
        List<String> keys = new ArrayList<>();
        for (String key : List.of(
                WAIT_NO_POWER, WAIT_NO_VIS, WAIT_NO_CRYSTALS, WAIT_NO_CRYSTALS_RECHECK, WAIT_NO_ROOM)) {
            keys.add("jade.thaumicenergistics_ce.arcane_assembler.wait_reason." + key);
        }
        for (String key : List.of(
                REFUSE_NODE_INACTIVE,
                REFUSE_BUSY,
                REFUSE_NOT_ARCANE,
                REFUSE_UNRESOLVED,
                REFUSE_TOO_EXPENSIVE)) {
            keys.add("jade.thaumicenergistics_ce.arcane_assembler.refuse_reason." + key);
        }
        return keys;
    }

    static Component tooExpensive(int price, int capacity) {
        return refusalReason(
                REFUSE_TOO_EXPENSIVE,
                "the recipe costs %s vis, exceeding the available vis capacity of %s",
                price,
                capacity);
    }
}
