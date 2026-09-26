package thaumicenergistics.integration.jade;

import appeng.api.networking.IGridNode;
import appeng.core.localization.InGameTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * The four states AE2 shows for a grid node, and how our tooltips draw them.
 *
 * <p>The cases, words and colours are AE2's and its translation keys are reused on purpose, so every machine
 * here that reports a network state shares one set of strings.
 *
 * <p>Worked out on the server, where the node lives, and sent to the client as an ordinal under {@link #TAG}.
 */
enum JadeGridState {

    OFFLINE(InGameTooltip.DeviceOffline),
    BOOTING(InGameTooltip.NetworkBooting),
    MISSING_CHANNEL(InGameTooltip.DeviceMissingChannel),
    ONLINE(InGameTooltip.DeviceOnline);

    /** The key both halves use in the tooltip's data tag. */
    static final String TAG = "GridState";

    private final InGameTooltip text;

    JadeGridState(InGameTooltip text) {
        this.text = text;
    }

    Component label() {
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

    static JadeGridState read(CompoundTag tag) {
        // Bounds-checked: the ordinal arrives in a payload from the other side, and an index out of range
        // would take the client down rather than show a wrong colour.
        JadeGridState[] states = values();
        int ordinal = tag.getByte(TAG);
        return ordinal >= 0 && ordinal < states.length ? states[ordinal] : OFFLINE;
    }

    ChatFormatting colour() {
        return switch (this) {
            case ONLINE -> ChatFormatting.GREEN;
            case OFFLINE -> ChatFormatting.RED;
            case BOOTING, MISSING_CHANNEL -> ChatFormatting.YELLOW;
        };
    }
}
