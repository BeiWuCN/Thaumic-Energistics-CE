package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.network.chat.Component;

/**
 * 奥术组装机等待或拒绝任务的原因，集中在一处。
 * 键和它的英文回退放在一起：缺翻译时不会把原始键显示给玩家。
 */
public final class AssemblerStatus {

    private AssemblerStatus() {}

    /** 本机器的一个 tooltip 原因。 */
    static Component waitReason(String key, String english, Object... args) {
        return Component.translatableWithFallback(
                "jade.thaumicenergistics_ce.arcane_assembler.wait_reason." + key, english, args);
    }

    static Component refusalReason(String key, String english, Object... args) {
        return Component.translatableWithFallback(
                "jade.thaumicenergistics_ce.arcane_assembler.refuse_reason." + key, english, args);
    }

    // ---- 原因键，只命名一次 --------------------------------------
    // 用常量不用字面量：每个键在这里拼一次，下面再拼上键前缀。

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

    /** vis 消耗超过本区块灵气容量上限的配方在此拒收。 */
    static Component tooExpensive(int price, int capacity) {
        return refusalReason(
                REFUSE_TOO_EXPENSIVE,
                "the recipe costs %s vis and this chunk's aura can never hold more than %s (aura nodes would"
                        + " raise it)",
                price,
                capacity);
    }
}
