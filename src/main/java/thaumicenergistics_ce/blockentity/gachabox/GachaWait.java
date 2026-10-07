package thaumicenergistics_ce.blockentity.gachabox;

import net.minecraft.network.chat.Component;

/** 箱子为什么停着不动：每种拒绝都有原因，所以 "on" 只表示它正处在两次转动之间。 */
public enum GachaWait {
    NO_STRUCTURE("no_structure", "The upper half is missing"),
    NO_BRAIN("no_brain", "Not bound"),
    // 大脑可能在那里却不属于任何人：存档捕捉到中途状态时留下的东西。
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

    /** 一片死屏：得先把装置本身修好，才谈得上有东西可看。 */
    public boolean blankScreen() {
        return this == NO_STRUCTURE || this == NO_CHANNEL;
    }

    // 原因由服务端挑选；只有客户端知道玩家的语言。
    public Component label() {
        return Component.translatableWithFallback(PREFIX + reason, english);
    }
}
