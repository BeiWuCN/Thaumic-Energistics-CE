package thaumicenergistics_ce.network;

public interface ClientboundReceiver {

    void acceptArcaneCraftCost(ArcaneCraftCostPayload payload);

    void acceptGolemBackpack(GolemBackpackPayload payload);
}
