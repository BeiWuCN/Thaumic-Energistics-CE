package thaumicenergistics_ce.arcane;

import appeng.api.networking.energy.IEnergySource;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneWorkbenchContext;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneWorkbench;
import com.leclowndu93150.thaumaturge.api.recipe.IWorkbenchAuraSource;
import com.leclowndu93150.thaumaturge.api.recipe.RegisterWorkbenchAuraSourcesEvent;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import thaumicenergistics_ce.util.ThELog;

@EventBusSubscriber(modid = ThEIds.MODID)
public final class TerminalWorkbenchVis {

    private static final IWorkbenchAuraSource AURA = TerminalWorkbenchVis::supplyAura;

    private TerminalWorkbenchVis() {}

    public static void register() {
        TcWorkbench.registerAuraSources(List.of(AURA));
        ThELog.LOG.info(
                "[arcane] registered the arcane crafting terminal as a workbench aura source");
    }

    @SubscribeEvent
    public static void onRegisterAuraSources(RegisterWorkbenchAuraSourcesEvent event) {
        ThELog.LOG.info(
                "[arcane] RegisterWorkbenchAuraSourcesEvent did reach this mod (it is not used to register)");
    }

    private static int supplyAura(
            ArcaneWorkbenchContext context,
            ServerPlayer player,
            IArcaneWorkbench workbench,
            int need,
            TransactionContext transaction) {
        if (!(workbench instanceof TerminalArcaneCraftingInput terminal)) {
            return 0;
        }
        PartArcaneCraftingTerminal part = terminal.part();
        if (part == null) {
            return 0;
        }
        IEnergySource payer = terminal.payer();
        if (payer != null && !player.level().isClientSide()) {
            if (terminal.visConnection()) {
                return TerminalAuraPayment.payBoundAura(player.level(), player.blockPosition(), terminal.visCard(), need, transaction);
            }
            return TerminalAuraPayment.pay(
                    player.level(), player.blockPosition(), payer, need, transaction);
        }
        return part.supplyAura(need, transaction);
    }
}
