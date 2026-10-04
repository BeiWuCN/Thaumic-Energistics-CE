package thaumicenergistics_ce.blockentity.assembler;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * The Arcane Assembler's reason for waiting or for turning a job away, in one place.
 * <ul>
 * <li>The key and its English fallback sit together so a missing translation cannot show a raw key.
 * <li>Split out of {@link BlockEntityArcaneAssembler}; the self-test enumerates the keys from another
 * package, so this class and {@link #tooltipReasonKeys()} are public and the rest is package-private.
 * </ul>
 */
public final class AssemblerStatus {

    private AssemblerStatus() {}

    /** One of this machine's tooltip reasons. */
    static Component wait(String key, String english, Object... args) {
        return Component.translatableWithFallback(
                "jade.thaumicenergistics_ce.arcane_assembler.wait_reason." + key, english, args);
    }

    static Component refuse(String key, String english, Object... args) {
        return Component.translatableWithFallback(
                "jade.thaumicenergistics_ce.arcane_assembler.refuse_reason." + key, english, args);
    }

    // ---- The reason keys, named once --------------------------------------
    // Constants, not literals: the self-test enumerates them to catch a reason key with no translation.

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

    /** The refusal for a recipe whose vis cost is more than this chunk's aura can ever hold. */
    static Component tooExpensive(int price, int capacity) {
        return refuse(
                REFUSE_TOO_EXPENSIVE,
                "the recipe costs %s vis and this chunk's aura can never hold more than %s (aura nodes would"
                        + " raise it)",
                price,
                capacity);
    }
}
