package thaumicenergistics_ce.integration.jade;

import appeng.api.networking.IGridNode;
import appeng.core.localization.InGameTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * AE2 显示的四种网格节点状态，以及 tooltip 如何绘制它们。
 * 取值、文字与颜色都取自 AE2，翻译键也用它自己的，每台机器共用同一套。
 * 状态在服务端解析（节点在那边），再以 ordinal 发给客户端。
 * 枚举公开是为了 [client.jade] 的 tooltip 那一半，写入侧仍是包级私有。
 */
public enum JadeGridState {

    OFFLINE(InGameTooltip.DeviceOffline),
    BOOTING(InGameTooltip.NetworkBooting),
    MISSING_CHANNEL(InGameTooltip.DeviceMissingChannel),
    ONLINE(InGameTooltip.DeviceOnline);

    public static final String TAG = "GridState";

    private final InGameTooltip text;

    JadeGridState(InGameTooltip text) {
        this.text = text;
    }

    public Component label() {
        return text.text();
    }

    static JadeGridState of(IGridNode node) {
        if (node == null || !node.isPowered()) {
            return OFFLINE;
        }
        if (!node.hasGridBooted()) {
            return BOOTING;
        }
        if (!node.meetsChannelRequirements()) {
            return MISSING_CHANNEL;
        }
        return ONLINE;
    }

    void write(CompoundTag tag, IGridNode node) {
        tag.putByte(TAG, (byte) of(node).ordinal());
    }

    public static JadeGridState read(CompoundTag tag) {
        // 做了边界检查：越界的 ordinal 从对面传来会让客户端崩溃。
        JadeGridState[] states = values();
        int ordinal = tag.getByte(TAG);
        return ordinal >= 0 && ordinal < states.length ? states[ordinal] : OFFLINE;
    }

    public ChatFormatting colour() {
        return switch (this) {
            case ONLINE -> ChatFormatting.GREEN;
            case OFFLINE -> ChatFormatting.RED;
            case BOOTING, MISSING_CHANNEL -> ChatFormatting.YELLOW;
        };
    }
}
