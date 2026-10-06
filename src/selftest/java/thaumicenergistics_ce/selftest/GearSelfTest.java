package thaumicenergistics_ce.selftest;

import appeng.api.features.GridLinkables;
import appeng.api.ids.AEComponents;
import com.leclowndu93150.thaumaturge.api.casters.FocusElementType;
import com.leclowndu93150.thaumaturge.api.casters.FocusEngine;
import com.leclowndu93150.thaumaturge.api.casters.FocusMedium;
import com.leclowndu93150.thaumaturge.content.casters.ItemFocus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import thaumicenergistics_ce.focus.AEWrench;
import thaumicenergistics_ce.focus.FocusEffectAEWrench;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.item.ItemFocusAEWrench;
import thaumicenergistics_ce.item.ItemGolemWirelessBackpack;
import thaumicenergistics_ce.util.ThELog;

/**
 * Self-test for the two items that are not machines: the wrench focus and the golem backpack.
 * <ul>
 *   <li>These failures are silent, so they need a test: an unregistered focus element casts nothing, and
 *       an item that arrived without its package looks exactly like one that has it.
 *   <li>Runs on {@code ServerStartedEvent} only with {@code THAUMICENERGISTICS_GEAR_SELFTEST=true}.
 * </ul>
 */
public final class GearSelfTest {

    private GearSelfTest() {}

    public static void run(ServerStartedEvent event) {
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_GEAR_SELFTEST"))) {
            return;
        }
        Level level = event.getServer().overworld();
        if (level == null) {
            return;
        }

        List<String> failures = new ArrayList<>();
        checkWrenchTag(failures);
        checkFocusElementRegistered(event, failures);
        checkFocusItemAssembled(failures);
        checkBackpackLinkHandler(failures);
        checkEveryWirelessItemIsLinkable(failures);
        report(failures);
    }

    /**
     * AE2 flags a wrench via {@code c:tools/wrench}; if that tag stops resolving, the focus still casts,
     * still spends vis, finds no wrench, and reports nothing.
     */
    private static void checkWrenchTag(List<String> failures) {
        ItemStack wrench = AEWrench.wrenchStack();
        if (wrench.isEmpty()) {
            failures.add("AE2's quartz wrench is missing, so the focus has nothing to borrow");
            return;
        }
        if (!AEWrench.isWrench(wrench)) {
            failures.add("AE2's quartz wrench is not in c:tools/wrench - the focus would borrow a wrench "
                    + "AE2 does not recognise, and do nothing");
        }
    }

    private static void checkFocusElementRegistered(ServerStartedEvent event, List<String> failures) {
        var lookup = event.getServer().registryAccess().lookupOrThrow(FocusElementType.REGISTRY_KEY);
        Holder<FocusElementType> holder = lookup
                .get(ResourceKey.create(FocusElementType.REGISTRY_KEY, FocusEffectAEWrench.KEY))
                .orElse(null);
        if (holder == null) {
            List<String> present = new ArrayList<>();
            for (Holder.Reference<FocusElementType> h : lookup.listElements().toList()) {
                present.add(h.key().location().toString());
            }
            failures.add("the wrench focus element is not registered; the registry holds " + present.size()
                    + ": " + present);
            return;
        }

        FocusElementType type = holder.value();
        if (type.element() == null) {
            failures.add("the wrench focus element registry entry carries no element");
        } else if (!FocusEffectAEWrench.KEY.equals(type.element().id())) {
            failures.add("the wrench focus element reports id " + type.element().id() + " instead of "
                    + FocusEffectAEWrench.KEY);
        }
        if (type.icon() == null) {
            failures.add("the wrench focus element has no icon, so the focal manipulator would draw nothing");
        }
    }

    private static void checkFocusItemAssembled(List<String> failures) {
        ItemStack stack = new ItemStack(ModItems.FOCUS_AEWRENCH.get());

        boolean wrote = ItemFocusAEWrench.assemble(stack);
        if (ItemFocus.getPackage(stack) == null) {
            failures.add("a freshly made wrench focus carries no package and assemble() did not add one");
            return;
        }
        if (!wrote) {
            failures.add("assemble() reported no work on a stack that had no package");
        }
        if (ItemFocusAEWrench.assemble(stack)) {
            failures.add("assemble() rewrote a package that was already there");
        }

        var pkg = ItemFocus.getPackage(stack);
        if (pkg == null || pkg.units().isEmpty()) {
            failures.add("the wrench focus package has no units, so the wand would cast nothing");
            return;
        }

        // The first unit has to be a medium: CastExecutor silently drops an effect that gets no targets.
        var first = pkg.units().get(0);
        var rootElement = FocusEngine.element(first.element());
        if (!(rootElement instanceof FocusMedium)) {
            failures.add("the wrench focus package starts with " + first.element() + ", which is "
                    + (rootElement == null ? "not a registered element" : "not a medium")
                    + " - the effect would never receive a target");
        }

        boolean hasWrench = pkg.units().stream().anyMatch(u -> FocusEffectAEWrench.KEY.equals(u.element()));
        if (!hasWrench) {
            failures.add("the wrench focus package never names " + FocusEffectAEWrench.KEY);
        }
        if (pkg.complexity() <= 0) {
            failures.add("the wrench focus package has complexity " + pkg.complexity()
                    + ", which prices the cast at nothing - Thaumaturge derives vis from complexity / 5");
        }

        // The wand charges a focus's price before the cast runs, so that price must be 0; the effect pays
        // visCost() once a wrench has acted, so that figure is the real one.
        float upFront = ((ItemFocus) stack.getItem()).getVisCost(stack);
        if (upFront != 0.0F || ItemFocusAEWrench.visCost() <= 0.0F) {
            failures.add("the wrench focus charges " + upFront + " vis up front and "
                    + ItemFocusAEWrench.visCost() + " per use; the up-front price must be 0 and the per-use "
                    + "price above 0, or a cast that does nothing costs vis");
        }
    }

    /** The backpack's link is silent when it fails: a memory card that does not recognise the item does
     * not link it. */
    private static void checkBackpackLinkHandler(List<String> failures) {
        var registered = GridLinkables.get(ModItems.GOLEM_WIFI_BACKPACK.get());
        if (registered == null) {
            failures.add("no IGridLinkableHandler is registered for the golem wireless backpack - a memory "
                    + "card cannot link it");
            return;
        }

        ItemStack stack = new ItemStack(ModItems.GOLEM_WIFI_BACKPACK.get());
        if (!registered.canLink(stack)) {
            failures.add("the backpack's link handler refuses its own item");
            return;
        }
        if (ItemGolemWirelessBackpack.isLinked(stack)) {
            failures.add("a freshly made backpack is already linked");
        }

        GlobalPos pos = GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 64, 2));
        registered.link(stack, pos);
        GlobalPos read = stack.get(AEComponents.WIRELESS_LINK_TARGET);
        if (!pos.equals(read)) {
            failures.add("linking the backpack stored " + read + " instead of " + pos);
        }
        registered.unlink(stack);
        if (stack.get(AEComponents.WIRELESS_LINK_TARGET) != null) {
            failures.add("unlinking the backpack left the link behind");
        }
    }

    private static void checkEveryWirelessItemIsLinkable(List<String> failures) {
        record Wireless(String what, Item item) {}
        List<Wireless> wireless = List.of(
                new Wireless("the wireless essentia terminal", ModItems.WIRELESS_ESSENTIA_TERMINAL.get()),
                new Wireless("the wireless arcane crafting terminal",
                        ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get()),
                new Wireless("the golem wireless backpack", ModItems.GOLEM_WIFI_BACKPACK.get()));

        for (Wireless w : wireless) {
            if (GridLinkables.get(w.item()) == null) {
                failures.add("no IGridLinkableHandler is registered for " + w.what()
                        + " - a memory card cannot bind it to a wireless access point, and nothing reports it");
                continue;
            }
            ItemStack stack = new ItemStack(w.item());
            if (!GridLinkables.get(w.item()).canLink(stack)) {
                failures.add("the link handler registered for " + w.what() + " refuses its own item");
            }
        }
    }

    private static void report(List<String> failures) {
        if (failures.isEmpty()) {
            ThELog.LOG.info("[gear] self-test passed");
            return;
        }
        for (String failure : failures) {
            ThELog.LOG.error("[gear] FAIL {}", failure);
        }
        ThELog.LOG.error("[gear] self-test failed with {} problem(s)", failures.size());
    }
}
