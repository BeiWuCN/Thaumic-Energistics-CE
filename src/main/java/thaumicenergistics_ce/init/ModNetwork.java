package thaumicenergistics_ce.init;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import thaumicenergistics_ce.network.ArcaneUnbindPayload;
import thaumicenergistics_ce.network.EncoderActionPayload;
import thaumicenergistics_ce.network.EncoderSourcePayload;
import thaumicenergistics_ce.network.EssentiaDepositPayload;
import thaumicenergistics_ce.network.EssentiaFillPayload;
import thaumicenergistics_ce.network.EssentiaInterfaceMarkPayload;
import thaumicenergistics_ce.network.GolemBackpackPayload;
import thaumicenergistics_ce.network.InscriberGridFillPayload;
import thaumicenergistics_ce.network.InscriberGridPayload;
import thaumicenergistics_ce.network.PartitionWellPayload;

/**
 * 网络注册。铭刻机按钮不用另发载荷，原版菜单按钮包自带 id。
 * 合成网格要发：那是客户端填的幽灵网格，只有服务端能把它变成配方。
 */
public final class ModNetwork {

    private ModNetwork() {}

    private static final String VERSION = "1";

    public static void register(IEventBus modBus) {
        modBus.addListener(ModNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(
                InscriberGridPayload.TYPE,
                InscriberGridPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // 整格一次发；写九次会让机器去解析八个既不是配方、也画不出来的网格。
        // 见 [InscriberGridFillPayload]。
        registrar.playToServer(
                InscriberGridFillPayload.TYPE,
                InscriberGridFillPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // 整格一次发；写九次会让机器去解析八个既不是配方、也画不出来的网格。
        // 见 [InscriberGridFillPayload]。
        registrar.playToServer(
                EssentiaDepositPayload.TYPE,
                EssentiaDepositPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        registrar.playToServer(
                EssentiaFillPayload.TYPE,
                EssentiaFillPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // 整格一次发；写九次会让机器去解析八个既不是配方、也画不出来的网格。
        // 见 [InscriberGridFillPayload]。
        registrar.playToServer(
                EssentiaInterfaceMarkPayload.TYPE,
                EssentiaInterfaceMarkPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // 整格一次发；写九次会让机器去解析八个既不是配方、也画不出来的网格。
        // 见 [InscriberGridFillPayload]。
        registrar.playToServer(
                PartitionWellPayload.TYPE,
                PartitionWellPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // 整格一次发；写九次会让机器去解析八个既不是配方、也画不出来的网格。
        // 见 [InscriberGridFillPayload]。
        registrar.playToServer(
                EncoderActionPayload.TYPE,
                EncoderActionPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // 整格一次发；写九次会让机器去解析八个既不是配方、也画不出来的网格。
        // 见 [InscriberGridFillPayload]。
        registrar.playToServer(
                EncoderSourcePayload.TYPE,
                EncoderSourcePayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // 傀儡的背包，告诉看着这个傀儡的玩家：它装了什么存在傀儡的持久数据里，
        // 原版不同步，所以还是得画出来。
        registrar.playToClient(GolemBackpackPayload.TYPE, GolemBackpackPayload.CODEC);
        // 服务端到客户端，也是唯一往这个方向走的载荷：奥术配方的 vis 消耗
        // 只有在服务端算得出来，而界面要把它画出来。
        registrar.playToClient(ArcaneCraftCostPayload.TYPE, ArcaneCraftCostPayload.CODEC);
        // 整格一次发；写九次会让机器去解析八个既不是配方、也画不出来的网格。
        // 见 [InscriberGridFillPayload]。
        registrar.playToServer(
                ArcaneUnbindPayload.TYPE,
                ArcaneUnbindPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
    }
}
