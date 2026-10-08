package thaumicenergistics_ce.network;

/**
 * 客户端在这里把自己装成服务端发往客户端那些载荷的接收者，
 * 载荷类就能指名接收者，不必指名某个屏幕：协议里没有客户端类型的原因在此。
 * 客户端从 {@code ClientSetup} 安装自己；专用服务端什么都不装，处理器自然落空。
 * 接收者字段本身在 common：两侧都加载这个类，只有一侧设置它的值。
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
