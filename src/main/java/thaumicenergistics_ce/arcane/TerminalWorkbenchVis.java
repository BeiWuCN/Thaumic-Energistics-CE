package thaumicenergistics_ce.arcane;

import appeng.api.networking.energy.IEnergySource;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneWorkbenchContext;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneWorkbench;
import com.leclowndu93150.thaumaturge.api.recipe.IWorkbenchAuraSource;
import com.leclowndu93150.thaumaturge.api.recipe.RegisterWorkbenchAuraSourcesEvent;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import thaumicenergistics_ce.util.ThELog;

/**
 * 让奥术合成终端用网络付奥术合成的无属性 vis 消耗。
 * 没有它就合不了：{@code baseVis} 取自工作台的灵气，线缆周围没有灵气。
 * 装了 vis 连接卡的终端直接取那份灵气，不为这个花电。
 */
@EventBusSubscriber(modid = ThEIds.MODID)
public final class TerminalWorkbenchVis {

    /** 唯一的源。只注册一次，见 {@link #register()}。 */
    private static final IWorkbenchAuraSource AURA = TerminalWorkbenchVis::supplyAura;

    private TerminalWorkbenchVis() {}

    /**
     * 把本模组的灵气源加进 Thaumaturge 的列表。{@code commonSetup} 只调一次：
     * 规划器轮流问每个源而不预留，两份副本会承诺已经花掉的 vis。
     */
    public static void register() {
        TcWorkbench.registerAuraSources(List.of(AURA));
        ThELog.LOG.info(
                "[arcane] registered the arcane crafting terminal as a workbench aura source");
    }

    /**
     * 记录文档里说的注册事件到不到得了本模组。刻意不在这里注册，免得和 {@link #register()} 重复；
     * 日志行就是上面那个顺序的实测结果。
     */
    @SubscribeEvent
    public static void onRegisterAuraSources(RegisterWorkbenchAuraSourcesEvent event) {
        ThELog.LOG.info(
                "[arcane] RegisterWorkbenchAuraSourcesEvent did reach this mod (it is not used to register)");
    }

    /**
     * 出合成价格里无属性灵气那部分。刻意不碰 context：
     * 要的是终端的位置，它随输入传进来，不在 context 里。
     */
    private static int supplyAura(
            ArcaneWorkbenchContext context,
            Player player,
            IArcaneWorkbench workbench,
            int need,
            boolean simulate) {
        if (!(workbench instanceof TerminalArcaneCraftingInput terminal)) {
            return 0;
        }
        PartArcaneCraftingTerminal part = terminal.part();
        if (part == null) {
            return 0;
        }
        IEnergySource payer = terminal.payer();
        if (payer != null && !player.level().isClientSide) {
            // 手持终端没有方块：它的灵气取自携带它的玩家周围。
            if (terminal.visConnection()) {
                // 这条路完全碰不到能量源，装了卡片的合成没有电可花。
                return TerminalAuraPayment.payAura(player.level(), player.blockPosition(), need, simulate);
            }
            return TerminalAuraPayment.pay(player.level(), player.blockPosition(), payer, need, simulate);
        }
        return part.supplyAura(need, simulate);
    }
}
