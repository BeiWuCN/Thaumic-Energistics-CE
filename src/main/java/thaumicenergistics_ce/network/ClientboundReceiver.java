package thaumicenergistics_ce.network;

/**
 * 两个从服务端发往客户端的载荷的线上约定；接收者是屏幕或客户端缓存，不是菜单。
 */
public interface ClientboundReceiver {

    void acceptArcaneCraftCost(ArcaneCraftCostPayload payload);

    void acceptGolemBackpack(GolemBackpackPayload payload);
}
