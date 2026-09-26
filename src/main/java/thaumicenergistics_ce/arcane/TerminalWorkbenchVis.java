package thaumicenergistics_ce.arcane;

import com.leclowndu93150.thaumaturge.api.recipe.ArcaneWorkbenchContext;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneWorkbench;
import com.leclowndu93150.thaumaturge.api.recipe.IWorkbenchAuraSource;
import com.leclowndu93150.thaumaturge.api.recipe.RegisterWorkbenchAuraSourcesEvent;
import com.leclowndu93150.thaumaturge.content.workbench.WorkbenchPayment;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * Lets the Arcane Crafting Terminal pay an arcane craft's untyped vis cost out of the network.
 *
 * <p>Without this the terminal cannot craft <em>anything</em>. Thaumaturge prices every arcane recipe in two
 * parts - a crystal requirement, paid with crystals or a wand, and an untyped {@code baseVis} cost, paid
 * from the aura of the workbench block the craft happens at. A terminal on a cable has no such block, so it
 * presents a <em>virtual</em> workbench context, and the planner's only remaining route for the aura is the
 * list of registered {@code IWorkbenchAuraSource}s. That list was empty, so no reservation was ever granted
 * and every craft came back {@code PAYMENT_UNAVAILABLE} - reported as *"合成终端里没法合成"*, and then, once
 * the six crystal slots existed, as *"目标产物还是没出现"* with the refusal in the log.
 *
 * <p>This is not a workaround for the missing workbench block; it is the extension point Thaumaturge
 * provides for exactly this situation: *"an addon (for example a networked storage system adjacent to the
 * workbench) can pay part of a craft's cost"*. The terminal is that networked storage system, and it pays by
 * draining the aura at its own position and charging the ME network for the vis.
 *
 * <h2>Why this registers directly instead of through the event</h2>
 *
 * <p>Thaumaturge fires {@link RegisterWorkbenchAuraSourcesEvent} from inside its own mod constructor:
 *
 * <pre>
 *   RegisterWorkbenchAuraSourcesEvent auraSourcesEvent = new RegisterWorkbenchAuraSourcesEvent();
 *   modBus.post(auraSourcesEvent);
 *   WorkbenchPayment.registerAuraSources(auraSourcesEvent.sources());
 * </pre>
 *
 * <p>Thaumaturge is a dependency, so its constructor runs <em>before</em> this mod's, and a
 * {@code @EventBusSubscriber} of ours is injected too late to be posted to - the event is constructed,
 * posted, and its collected sources copied into the static list before we exist. Listening for it would be
 * silently registering nothing, which is the failure mode this whole class exists to fix, so the
 * subscription below only <em>reports</em> whether the event reached us and never registers.
 *
 * <p>What works is calling the same registration method Thaumaturge calls. It appends to a static list that
 * is only read when a craft is priced, so calling it from this mod's own setup is both safe and early
 * enough. It lives in Thaumaturge's {@code content} package rather than its {@code api} one, which is a
 * wart worth naming: the API documents a way to register that an addon cannot actually use.
 */
@EventBusSubscriber(modid = ThEIds.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class TerminalWorkbenchVis {

    /** The single source. Registered exactly once - see {@link #register()}. */
    private static final IWorkbenchAuraSource AURA = TerminalWorkbenchVis::supplyAura;

    private TerminalWorkbenchVis() {}

    /**
     * Adds this mod's aura source to Thaumaturge's list.
     *
     * <p>Called from {@code commonSetup}. Exactly once, and it matters that it is not twice: the planner
     * asks each registered source in turn for what is left of the price, and a source answers from the
     * aura's current contents without reserving them in between. Two copies of this source would therefore
     * both promise the same vis and the second would be promising vis the first had already spent.
     */
    public static void register() {
        WorkbenchPayment.registerAuraSources(List.of(AURA));
        ThaumicEnergistics.LOG.info(
                "[arcane] registered the arcane crafting terminal as a workbench aura source");
    }

    /**
     * Reports whether the documented registration event reaches this mod.
     *
     * <p>Deliberately does not register, so that this cannot double up with {@link #register()}. It exists
     * because the ordering described above was a guess until it was measured; the log line is the
     * measurement.
     */
    @SubscribeEvent
    public static void onRegisterAuraSources(RegisterWorkbenchAuraSourcesEvent event) {
        ThaumicEnergistics.LOG.info(
                "[arcane] RegisterWorkbenchAuraSourcesEvent did reach this mod (it is not used to register)");
    }

    /**
     * Supplies the untyped aura part of a craft's price.
     *
     * <p>The context is deliberately unused. A virtual context has no block position, and the position this
     * needs is the terminal's - which travels on the input rather than in the context.
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
