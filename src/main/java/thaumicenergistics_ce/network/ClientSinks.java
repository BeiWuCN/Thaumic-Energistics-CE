package thaumicenergistics_ce.network;

/**
 * 客户端把自己安装为服务端发往客户端那些载荷的接收者之处，这样载荷类就能指名它的
 * 接收者而不必指名某个屏幕——协议里没有客户端类型的原因就在此。客户端从
 * {@code ClientSetup} 在这里安装自己；专用服务端什么都不安装，处理器自然落空。接收者字段
 * 接收者字段本身是 common 的，因为两侧都会加载
 * 这个类，而只有一侧会设置它的值。
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
