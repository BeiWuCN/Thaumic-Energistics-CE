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
 * 让奥术合成终端用网络支付奥术合成的无属性 vis 消耗。
 * 没有它就无法合成：{@code baseVis} 来自工作台的灵气，而线缆周围没有灵气。
 * 装有 vis 连接卡的终端会直接取用那份灵气，不为此花费任何电力。
 */
@EventBusSubscriber(modid = ThEIds.MODID)
public final class TerminalWorkbenchVis {

    /** 唯一的源。只注册一次——见 {@link #register()}。 */
    private static final IWorkbenchAuraSource AURA = TerminalWorkbenchVis::supplyAura;

    private TerminalWorkbenchVis() {}

    /**
     * 把本模组的灵气源加入 Thaumaturge 的列表。由 {@code commonSetup} 调用且只调用一次：
     * 规划器轮流询问每个源而不做预留，两份副本就会承诺已经被花掉的 vis。
     */
    public static void register() {
        TcWorkbench.registerAuraSources(List.of(AURA));
        ThELog.LOG.info(
                "[arcane] registered the arcane crafting terminal as a workbench aura source");
    }

    /**
     * 报告文档所述的那个注册事件是否会到达本模组。刻意不在此注册，
     * 以免与 {@link #register()} 重复；日志行就是上面那个顺序的实测结果。
     */
    @SubscribeEvent
    public static void onRegisterAuraSources(RegisterWorkbenchAuraSourcesEvent event) {
        ThELog.LOG.info(
                "[arcane] RegisterWorkbenchAuraSourcesEvent did reach this mod (it is not used to register)");
    }

    /**
     * 提供合成价格中无属性灵气的那部分。刻意不使用 context：需要的是终端的
     * 位置，它随输入传递，而不在 context 里。
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
            // 手持终端没有方块：它的灵气就是携带它的玩家周围的灵气。
            if (terminal.visConnection()) {
                // 这个调用完全接触不到能量源，因此装了卡片的合成没有电力可花。
                return TerminalAuraPayment.payAura(player.level(), player.blockPosition(), need, simulate);
            }
            return TerminalAuraPayment.pay(player.level(), player.blockPosition(), payer, need, simulate);
        }
        return part.supplyAura(need, simulate);
    }
}
