package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.network.chat.Component;

/**
 * The Arcane Assembler's reason for waiting or for turning a job away, in one place. The key and
 * its English fallback sit together here, so a missing translation cannot show a raw key.
 */
public final class AssemblerStatus {

    private AssemblerStatus() {}

    /** One of this machine's tooltip reasons. */
    static Component waitReason(String key, String english, Object... args) {
        return Component.translatableWithFallback(
                "jade.thaumicenergistics_ce.arcane_assembler.wait_reason." + key, english, args);
    }

    static Component refusalReason(String key, String english, Object... args) {
        return Component.translatableWithFallback(
                "jade.thaumicenergistics_ce.arcane_assembler.refuse_reason." + key, english, args);
    }

    // ---- The reason keys, named once --------------------------------------
    // Constants, not literals: each key is spelled once here and joined to its key prefix below.

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

    /** The refusal for a recipe whose vis cost is more than this chunk's aura can ever hold. */
    static Component tooExpensive(int price, int capacity) {
        return refusalReason(
                REFUSE_TOO_EXPENSIVE,
                "the recipe costs %s vis and this chunk's aura can never hold more than %s (aura nodes would"
                        + " raise it)",
                price,
                capacity);
    }
}
