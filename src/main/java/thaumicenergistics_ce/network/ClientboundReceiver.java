package thaumicenergistics_ce.network;

/**
 * 两个从服务端发往客户端的载荷的线上约定，其接收者是屏幕或
 * 客户端缓存，而不是菜单。
 */
public interface ClientboundReceiver {

    void acceptArcaneCraftCost(ArcaneCraftCostPayload payload);

    void acceptGolemBackpack(GolemBackpackPayload payload);
}
