package thaumicenergistics_ce.network;

/**
 * Where the client installs itself as the receiver of the serverbound-to-client payloads, so a
 * payload class can name its receiver without naming a screen - the reason the protocol has no
 * client types. The client installs itself here from {@code ClientSetup}; a dedicated server
 * installs nothing and the handlers fall through. The receiver field itself is common, because
 * both sides load this class and only one of them sets its value.
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
