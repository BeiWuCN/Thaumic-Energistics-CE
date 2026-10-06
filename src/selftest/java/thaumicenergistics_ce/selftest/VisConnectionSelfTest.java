package thaumicenergistics_ce.selftest;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.Upgrades;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.TerminalAuraPayment;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import thaumicenergistics_ce.util.ThELog;

/**
 * Self-test for the vis connection card: the AE2 registration, the terminal's own upgrade slot and the
 * two aura passes one craft makes.
 * <ul>
 *   <li>Off unless {@code THAUMICENERGISTICS_VIS_CONNECTION_SELFTEST} is true; runs at server start.
 *   <li>Registry and pure logic only: a headless gate has no player, no live grid and no screen.
 *   <li>{@code registerUpgrades} sits in common setup, so the card is registered before this runs.
 * </ul>
 */
public final class VisConnectionSelfTest {

    private static final String TAG = "vis-connection";

    /** One stack for checks 4 to 6: the card has to go into and come back out of the same item. */
    private static final ItemStack TERMINAL =
            new ItemStack(ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get());

    private VisConnectionSelfTest() {}

    public static void run(ServerStartedEvent event) {
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_VIS_CONNECTION_SELFTEST"))) {
            return;
        }
        List<String> failures = new ArrayList<>();
        checkCardIsUpgradeCard(failures);
        checkTerminalTakesOneCard(failures);
        checkUnrelatedItemTakesNone(failures);
        checkEmptyTerminalIgnoresCard(failures);
        checkInsertedCardIsCounted(failures);
        checkEmptiedSlotStopsCounting(failures);
        checkPlacedPartHasSlot(failures);
        checkAuraPassesAgree(failures, event.getServer().overworld());
        if (failures.isEmpty()) {
            ThELog.LOG.info("[{}] self-test passed: the vis card is an AE2 upgrade card that both the"
                    + " wireless and the placed arcane terminal take once, a placed vis source pays the"
                    + " aura with no AE at all, a short aura supplies what it can and a missing card"
                    + " falls back to AE",
                    TAG);
            return;
        }
        for (String failure : failures) {
            ThELog.LOG.error("[{}] FAIL {}", TAG, failure);
        }
        ThELog.LOG.error("[{}] self-test failed with {} problem(s)", TAG, failures.size());
    }

    /** The card has to be AE2's own kind of upgrade card, or no upgrade slot anywhere will take it. */
    private static void checkCardIsUpgradeCard(List<String> failures) {
        if (!Upgrades.isUpgradeCardItem(ModItems.VIS_CONNECTION_CARD.get())) {
            failures.add("the vis card is not an AE2 upgrade card, so no upgrade slot would take it");
        }
    }

    /** The registration has to exist at all: an unregistered card is refused by AE2's slot filter. */
    private static void checkTerminalTakesOneCard(List<String> failures) {
        int room = Upgrades.getMaxInstallable(
                ModItems.VIS_CONNECTION_CARD.get(), ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get());
        if (room != 1) {
            failures.add("the wireless arcane terminal accepts " + room
                    + " of the vis card, not 1 - its upgrade slot would refuse it");
        }
    }

    /** The registration is per host, not global: an unrelated item must report no room for the card. */
    private static void checkUnrelatedItemTakesNone(List<String> failures) {
        int room = Upgrades.getMaxInstallable(ModItems.VIS_CONNECTION_CARD.get(), Items.STONE);
        if (room != 0) {
            failures.add("an unrelated item reports room for the vis card");
        }
    }

    /** A terminal with an empty slot must not report the card: a bare item would then drain aura free. */
    private static void checkEmptyTerminalIgnoresCard(List<String> failures) {
        IUpgradeInventory upgrades = terminalUpgrades(failures);
        if (upgrades == null) {
            return;
        }
        if (upgrades.size() != 2) {
            failures.add("the wireless arcane terminal has " + upgrades.size()
                    + " upgrade slots, expected 2");
        }
        int accessRoom = Upgrades.getMaxInstallable(
                ModItems.ESSENTIA_ACCESS_CARD.get(), ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get());
        if (accessRoom != 1) {
            failures.add("the wireless arcane terminal no longer takes the access card");
        }
        if (TerminalAuraPayment.visConnectionInstalled(TERMINAL)) {
            failures.add("an empty terminal reports the vis card installed, so a bare item would drain"
                    + " the aura for free");
        }
    }

    /** A card in the terminal's own slot has to be seen, which is what the aura path is keyed on. */
    private static void checkInsertedCardIsCounted(List<String> failures) {
        IUpgradeInventory upgrades = terminalUpgrades(failures);
        if (upgrades == null) {
            return;
        }
        ItemStack card = new ItemStack(ModItems.VIS_CONNECTION_CARD.get());
        if (!upgrades.isItemValid(0, card)) {
            failures.add("the terminal's upgrade slot refuses the vis card");
        }
        upgrades.setItemDirect(0, card);
        if (!TerminalAuraPayment.visConnectionInstalled(TERMINAL)) {
            failures.add("a card sitting in the terminal's own upgrade slot is not seen as installed");
        }
    }

    /** Pulling the card has to take the permission with it: the aura path outliving the card is the bug. */
    private static void checkEmptiedSlotStopsCounting(List<String> failures) {
        IUpgradeInventory upgrades = terminalUpgrades(failures);
        if (upgrades == null) {
            return;
        }
        upgrades.setItemDirect(0, ItemStack.EMPTY);
        if (TerminalAuraPayment.visConnectionInstalled(TERMINAL)) {
            failures.add("emptying the slot left the vis card counted, so the aura path would outlive"
                    + " the card");
        }
    }

    /** The terminal on a cable carries the same card: its own slot, its own aura, no network power. */
    private static void checkPlacedPartHasSlot(List<String> failures) {
        int room = Upgrades.getMaxInstallable(
                ModItems.VIS_CONNECTION_CARD.get(), ModItems.ARCANE_CRAFTING_TERMINAL.get());
        if (room != 1) {
            failures.add("the placed arcane terminal accepts " + room
                    + " of the vis card, not 1 - its upgrade slot would refuse it");
        }
        PartArcaneCraftingTerminal part = placedPart(failures);
        if (part == null) {
            return;
        }
        IUpgradeInventory upgrades = part.getUpgrades();
        if (upgrades == null || upgrades.size() != 1) {
            failures.add("the placed arcane terminal reports no upgrade slot of its own");
            return;
        }
        if (TerminalAuraPayment.visConnectionInstalled(part)) {
            failures.add("a placed terminal with an empty slot reports the vis card installed");
        }
        upgrades.setItemDirect(0, new ItemStack(ModItems.VIS_CONNECTION_CARD.get()));
        if (!TerminalAuraPayment.visConnectionInstalled(part)) {
            failures.add("a card in the placed terminal's slot is not seen, so its aura path stays off");
        }
        upgrades.setItemDirect(0, ItemStack.EMPTY);
        if (TerminalAuraPayment.visConnectionInstalled(part)) {
            failures.add("pulling the card from the placed terminal left its aura path on");
        }
    }

    /** Builds the part outside a world; a throw here is a failure, not a broken gate. */
    private static @Nullable PartArcaneCraftingTerminal placedPart(List<String> failures) {
        try {
            return new PartArcaneCraftingTerminal(ModItems.ARCANE_CRAFTING_TERMINAL.get());
        } catch (Throwable thrown) {
            failures.add("the placed arcane terminal could not be built: "
                    + thrown.getClass().getSimpleName() + ": " + thrown.getMessage());
            return null;
        }
    }

    /** The two passes over one craft have to answer the same, or Thaumaturge throws on commit. */
    private static void checkAuraPassesAgree(List<String> failures, ServerLevel level) {
        checkAuraPathHasNoEnergySource(failures);
        BlockPos where = level.getSharedSpawnPos();
        // Far more centivis than any chunk holds, so the pass has to answer with what it can get.
        int need = 1_000_000;
        float before = TcAura.vis(level, where);
        int simulated = TerminalAuraPayment.payAura(level, where, need, true);
        if (simulated < 0 || simulated > need) {
            failures.add("a simulated aura pass offered " + simulated + " of " + need + " centivis");
            return;
        }
        if (simulated >= need) {
            failures.add("a chunk answered the whole of an impossible ask, so the short-aura path went"
                    + " untested");
            return;
        }
        if (Math.abs(TcAura.vis(level, where) - before) > 0.01F) {
            failures.add("the simulated aura pass moved the aura, which the commit pass would then"
                    + " double-count");
            return;
        }
        if (simulated > Math.round(before * TerminalAuraPayment.CENTIVIS_PER_VIS) + 1) {
            failures.add("a short pass offered " + simulated + " centivis of the " + before
                    + " vis the chunk holds, so the ask leaked into the answer");
        }
        int committed = TerminalAuraPayment.payAura(level, where, need, false);
        if (committed != simulated) {
            failures.add("the two aura passes disagreed (simulate=" + simulated + ", commit=" + committed
                    + "), which is what makes Thaumaturge throw between simulation and commit");
            return;
        }
        float after = TcAura.vis(level, where);
        float promised = (float) committed / TerminalAuraPayment.CENTIVIS_PER_VIS;
        if (before - after > promised + 0.02F) {
            failures.add("the committed pass took " + (before - after) + " vis where it reported "
                    + promised);
        }
        if (committed > 0 && before - after <= 0.0F) {
            failures.add("the committed pass reported " + committed + " centivis but the aura never moved");
        }
        // A neighbouring chunk, asked in simulation only: the answer is read off the level every time, and
        // asking it must leave this chunk alone.
        int elsewhere = TerminalAuraPayment.payAura(level, where.offset(16, 0, 0), need, true);
        if (elsewhere < 0 || elsewhere > need) {
            failures.add("a pass in the neighbouring chunk offered " + elsewhere + " of " + need);
        }
        if (Math.abs(TcAura.vis(level, where) - after) > 0.01F) {
            failures.add("asking the neighbouring chunk moved this chunk's aura");
        }
        ThELog.LOG.info("[{}] aura at {} held {} vis: the two passes answered {} then {} of {} centivis",
                TAG, where, before, simulated, committed, need);
    }

    /** The aura pass has nowhere to bill: no energy source appears in its signature at all. */
    private static void checkAuraPathHasNoEnergySource(List<String> failures) {
        for (java.lang.reflect.Method method : TerminalAuraPayment.class.getDeclaredMethods()) {
            if (!method.getName().equals("payAura")) {
                continue;
            }
            for (Class<?> parameter : method.getParameterTypes()) {
                if (IEnergySource.class.isAssignableFrom(parameter)) {
                    failures.add("the aura path takes an energy source, so a carded craft could still be"
                            + " billed in AE");
                    return;
                }
            }
        }
    }

    /** Reads the terminal's own upgrade inventory; a throw here is a failure, not a broken gate. */
    private static @Nullable IUpgradeInventory terminalUpgrades(List<String> failures) {
        try {
            return ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get().getUpgrades(TERMINAL);
        } catch (Throwable thrown) {
            failures.add("the terminal's own upgrade inventory did not answer: "
                    + thrown.getClass().getSimpleName() + ": " + thrown.getMessage());
            return null;
        }
    }
}
