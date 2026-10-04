package thaumicenergistics_ce.arcane;

import com.leclowndu93150.thaumaturge.api.recipe.ArcaneWorkbenchContext;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneWorkbench;
import com.leclowndu93150.thaumaturge.api.recipe.IWorkbenchAuraSource;
import com.leclowndu93150.thaumaturge.api.recipe.RegisterWorkbenchAuraSourcesEvent;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * Lets the Arcane Crafting Terminal pay an arcane craft's untyped vis cost out of the network.
 * Without it nothing crafts: {@code baseVis} comes from a workbench's aura, which a cable has none of.
 */
@EventBusSubscriber(modid = ThEIds.MODID)
public final class TerminalWorkbenchVis {

    /** The single source. Registered exactly once - see {@link #register()}. */
    private static final IWorkbenchAuraSource AURA = TerminalWorkbenchVis::supplyAura;

    private TerminalWorkbenchVis() {}

    /**
     * Adds this mod's aura source to Thaumaturge's list. Called from {@code commonSetup}, exactly once: the
     * planner asks each source in turn without reserving, so two copies would promise vis already spent.
     */
    public static void register() {
        TcWorkbench.registerAuraSources(List.of(AURA));
        ThaumicEnergistics.LOG.info(
                "[arcane] registered the arcane crafting terminal as a workbench aura source");
    }

    /**
     * Reports whether the documented registration event reaches this mod. Deliberately does not register, so
     * it cannot double up with {@link #register()}; the log line is the measurement of the ordering above.
     */
    @SubscribeEvent
    public static void onRegisterAuraSources(RegisterWorkbenchAuraSourcesEvent event) {
        ThaumicEnergistics.LOG.info(
                "[arcane] RegisterWorkbenchAuraSourcesEvent did reach this mod (it is not used to register)");
    }

    /**
     * Supplies the untyped aura part of a craft's price. The context is deliberately unused: the position
     * needed is the terminal's, which travels on the input rather than in the context.
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
        return part == null ? 0 : part.supplyAura(need, simulate);
    }
}
