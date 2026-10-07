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
        // 源质终端搬的是容器内容而非物品，AE2 自己的终端数据包装不了。
        registrar.playToServer(
                EssentiaDepositPayload.TYPE,
                EssentiaDepositPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        registrar.playToServer(
                EssentiaFillPayload.TYPE,
                EssentiaFillPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // 落在 ME 接口自身配置行或存储行上的标记。
        // 走服务端方向：源质键不是物品，AE2 的幽灵槽位通道只收物品。
        registrar.playToServer(
                EssentiaInterfaceMarkPayload.TYPE,
                EssentiaInterfaceMarkPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // 从 JEI 设置存储元件工作台的分区井，走服务端方向。
        // AE2 的网格数据包只有经 [AEBaseMenu] 才能到达伪槽位，见 [PartitionWellPayload]。
        registrar.playToServer(
                PartitionWellPayload.TYPE,
                PartitionWellPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // 蒸馏编码器的界面里要素井没有物品槽位（要素不是物品），选要素和请求样板都按指令传输。
        registrar.playToServer(
                EncoderActionPayload.TYPE,
                EncoderActionPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // 还有来源模板：编码器指令里唯一一条参数不是数字、服务端也推导不出来，见 [EncoderSourcePayload]。
        registrar.playToServer(
                EncoderSourcePayload.TYPE,
                EncoderSourcePayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // 傀儡的背包。内容存在傀儡的持久化数据里，原版不同步，得专门画出来。
        registrar.playToClient(
                GolemBackpackPayload.TYPE,
                GolemBackpackPayload.CODEC,
                (payload, context) -> payload.handleOnClient(context.player()));
        // 唯一一个服务端到客户端的载荷：奥术配方的 vis 消耗只有服务端算得出，界面要画。
        registrar.playToClient(
                ArcaneCraftCostPayload.TYPE,
                ArcaneCraftCostPayload.CODEC,
                (payload, context) -> payload.handleOnClient(context.player()));
        // 忘记已配对的终端。潜行左键在客户端发起，只有客户端看得到点向空处的点击。
        // 见 [ArcaneUnbindPayload]。
        registrar.playToServer(
                ArcaneUnbindPayload.TYPE,
                ArcaneUnbindPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
    }
}
