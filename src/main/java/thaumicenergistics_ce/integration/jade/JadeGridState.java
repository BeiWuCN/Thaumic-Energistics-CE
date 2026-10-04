package thaumicenergistics_ce.integration.jade;

import appeng.api.networking.IGridNode;
import appeng.core.localization.InGameTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * The four grid-node states AE2 shows, and how our tooltips draw them.
 * <ul>
 *   <li>Cases, words and colours are AE2's; its translation keys are reused so every machine
 *       reporting a network state shares one set of strings.
 *   <li>Resolved on the server, where the node lives, and sent to the client as an ordinal.
 *   <li>Public because the tooltip halves live in {@code client.jade} and read {@link #TAG},
 *       {@link #read}, {@link #label()} and {@link #colour()} from there. The write side
 *       ({@code of} / {@code write}) stays package-private: it is the server's own.
 * </ul>
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
        // Bounds-checked: an out-of-range ordinal from the other side would crash the client.
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
