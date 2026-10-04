package thaumicenergistics_ce.net;

/**
 * The wire contract for the two payloads that travel server to client, where the receiver is a screen or a
 * client cache rather than a menu.
 */
public interface ClientboundReceiver {

    void acceptArcaneCraftCost(ArcaneCraftCostPayload payload);

    void acceptGolemBackpack(GolemBackpackPayload payload);
}
