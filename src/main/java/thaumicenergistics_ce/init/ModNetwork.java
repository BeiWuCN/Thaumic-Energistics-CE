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
 * 网络注册。知识铭刻机的按钮不需要载荷，因为原版自己的菜单按钮数据包
 * 就带着 id，但铭刻机的合成网格需要：它是客户端填充的幽灵网格，
 * 只有服务端能把它变成配方。
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
        // 一次发送整个网格，用于已保存的配方或 JEI 转移：写九次会让机器去解析
        // 八个既不是配方、也不会被绘制的网格——见 [InscriberGridFillPayload]。
        registrar.playToServer(
                InscriberGridFillPayload.TYPE,
                InscriberGridFillPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // 源质终端搬运的是容器内容而不是物品，AE2 自己的终端数据包
        // 表达不了这种情况——原因见各个载荷。
        registrar.playToServer(
                EssentiaDepositPayload.TYPE,
                EssentiaDepositPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        registrar.playToServer(
                EssentiaFillPayload.TYPE,
                EssentiaFillPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // 落在 ME 接口自身配置行或存储行上的标记。走服务端方向，因为一个源质
        // 键不是物品，而 AE2 的幽灵槽位通道只能携带物品。
        registrar.playToServer(
                EssentiaInterfaceMarkPayload.TYPE,
                EssentiaInterfaceMarkPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // 从 JEI 设置的存储元件工作台分区井。出于同样的理由走服务端方向，还有第二个
        // 理由：AE2 的网格数据包只有经由 [AEBaseMenu] 才能到达伪槽位——见 [PartitionWellPayload]。
        registrar.playToServer(
                PartitionWellPayload.TYPE,
                PartitionWellPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // 蒸馏编码器的界面没有为它的要素井准备物品槽位——要素不是物品——
        // 所以选取一个要素和请求一份样板都以指令的形式传输。
        registrar.playToServer(
                EncoderActionPayload.TYPE,
                EncoderActionPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // 还有来源模板，它是编码器指令中唯一一条参数既不是数字、
        // 也无法在服务端推导出来的——见 [EncoderSourcePayload]。
        registrar.playToServer(
                EncoderSourcePayload.TYPE,
                EncoderSourcePayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
        // 傀儡的背包，告知正在观察该傀儡的玩家：它是什么存放在傀儡的
        // 持久化数据里，原版不会同步，所以仍然需要专门绘制。
        registrar.playToClient(
                GolemBackpackPayload.TYPE,
                GolemBackpackPayload.CODEC,
                (payload, context) -> payload.handleOnClient(context.player()));
        // 服务端到客户端，也是唯一走这个方向的载荷：奥术配方的 vis 消耗
        // 只能在服务端算出，而界面需要把它画出来。
        registrar.playToClient(
                ArcaneCraftCostPayload.TYPE,
                ArcaneCraftCostPayload.CODEC,
                (payload, context) -> payload.handleOnClient(context.player()));
        // 忘记已配对的终端：请求此操作的潜行左键发生在客户端，
        // 因为只有客户端能看到点向空处的点击——见 [ArcaneUnbindPayload]。
        registrar.playToServer(
                ArcaneUnbindPayload.TYPE,
                ArcaneUnbindPayload.CODEC,
                (payload, context) -> payload.handle(context.player()));
    }
}
