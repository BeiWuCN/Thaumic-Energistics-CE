package thaumicenergistics_ce.net;

/**
 * Where the client installs itself as the receiver of the serverbound-to-client payloads, so the payload
 * classes can name their receiver without naming a screen or a client cache - the reason the protocol
 * package stays free of client-only types.
 * <ul>
 * <li>Installed from {@code ClientSetup} on the client setup event; on a dedicated server nothing is
 * installed and the payload handlers fall through instead of touching a class that is not there.
 * <li>The field itself is common: both sides load this class, only one of them sets its value.
 * </ul>
 */
public final class ClientSinks {

    private static ClientboundReceiver receiver;

    private ClientSinks() {}

    public static void install(ClientboundReceiver sink) {
        receiver = sink;
    }

    public static void acceptArcaneCraftCost(ArcaneCraftCostPayload payload) {
        ClientboundReceiver sink = receiver;
        if (sink != null) {
            sink.acceptArcaneCraftCost(payload);
        }
    }

    public static void acceptGolemBackpack(GolemBackpackPayload payload) {
        ClientboundReceiver sink = receiver;
        if (sink != null) {
            sink.acceptGolemBackpack(payload);
        }
    }
}
