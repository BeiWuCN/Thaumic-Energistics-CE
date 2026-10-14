package thaumicenergistics_ce.recipe.arcane;

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

@EventBusSubscriber(modid = ThEIds.MODID)
public final class TerminalWorkbenchVis {

    private static final IWorkbenchAuraSource AURA = TerminalWorkbenchVis::supplyAura;

    private TerminalWorkbenchVis() {}

    public static void register() {
        TcWorkbench.registerAuraSources(List.of(AURA));
    }

    @SubscribeEvent
    public static void onRegisterAuraSources(RegisterWorkbenchAuraSourcesEvent event) {
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
        return part != null ? part.supplyAura(need, transaction)
                : terminal.payer() == null ? 0
                : TerminalAuraPayment.payChunkAura(player.level(), player.blockPosition(), need, transaction);
    }
}
