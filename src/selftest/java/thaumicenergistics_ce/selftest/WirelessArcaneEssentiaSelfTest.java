package thaumicenergistics_ce.selftest;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.item.ItemWirelessArcaneCraftingTerminal;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.menu.MenuEssentiaTerminal;
import thaumicenergistics_ce.network.EssentiaTerminalReceiver;
import thaumicenergistics_ce.util.ThELog;

/**
 * Self-test for the wireless arcane terminal's access card: the AE2 registration, the two upgrade slots
 * and the card that has to ride on the terminal's own stack.
 * <ul>
 *   <li>Off unless {@code THAUMICENERGISTICS_ARCANE_ESSENTIA_SELFTEST=true}; runs on {@code ServerStartedEvent}.
 *   <li>Pure logic and the registry only: a headless gate has no player, no live grid and no screen, so
 *       nothing here loads a client class or builds a menu.
 *   <li>The card has to be registered before this runs: {@code registerUpgrades} sits in the mod's own
 *       common setup, which completes ahead of the server starting.
 * </ul>
 */
public final class WirelessArcaneEssentiaSelfTest {

    private static final String TAG = "wireless-arcane-essentia";

    private WirelessArcaneEssentiaSelfTest() {}

    /** The two stacks every check works on, and the reader the item itself hands out for them. */
    private static final class Params {

        private final ItemStack card;

        private final ItemStack terminal;

        private final IUpgradeInventory upgrades;
        private final String setupError;

        private Params(String setupError, ItemStack card, ItemStack terminal, IUpgradeInventory upgrades) {
            this.card = card;
            this.terminal = terminal;
            this.upgrades = upgrades;
            this.setupError = setupError;
        }

        private static Params build() {
            ItemStack card = new ItemStack(ModItems.ESSENTIA_ACCESS_CARD.get());
            ItemStack terminal = new ItemStack(ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get());
            IUpgradeInventory upgrades = null;
            String error = null;
            try {
                ItemWirelessArcaneCraftingTerminal item =
                        ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get();
                upgrades = item.getUpgrades(terminal);
            } catch (Throwable thrown) {
                error = thrown.getClass().getSimpleName() + ": " + thrown.getMessage();
            }
            return new Params(error, card, terminal, upgrades);
        }
    }

    public static void run(ServerStartedEvent event) {
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_ARCANE_ESSENTIA_SELFTEST"))) {
            return;
        }
        List<String> failures = new ArrayList<>();
        Params params = null;
        try {
            params = Params.build();
        } catch (Throwable thrown) {
            failures.add("the upgrade inventory of the terminal item could not be built: "
                    + thrown.getClass().getSimpleName() + ": " + thrown.getMessage());
        }
        checkCardIsUpgradeCard(failures);
        checkTerminalTakesOneCard(failures);
        checkInterfaceStillTakesCard(failures);
        checkUnrelatedItemTakesNone(failures);
        if (params == null || params.setupError != null) {
            failures.add("the terminal item's own upgrade inventory did not answer: "
                    + (params == null ? "the test parameters were not built" : params.setupError));
        } else {
            checkTerminalHasTwoUpgradeSlots(failures, params);
            checkUpgradeSlotAcceptsCard(failures, params);
            checkInsertedCardIsCounted(failures, params);
            checkEmptiedSlotStopsCounting(failures, params);
            checkCardRidesOnTheStack(failures, params);
        }
        checkBothMenusAnswerTheFiller(failures);
        checkBothSidesAskForTheCard(failures);
        if (failures.isEmpty()) {
            ThELog.LOG.info("[{}] self-test passed: the access card is an upgrade card, the wireless arcane"
                    + " terminal has 2 upgrade slots and takes exactly one, the card rides on its own stack, and"
                    + " both terminal menus answer the filler", TAG);
            return;
        }
        for (String failure : failures) {
            ThELog.LOG.error("[{}] FAIL {}", TAG, failure);
        }
        ThELog.LOG.error("[{}] self-test failed with {} problem(s)", TAG, failures.size());
    }

    /** The card has to be AE2's own kind of upgrade card, or no upgrade slot anywhere will take it. */
    private static void checkCardIsUpgradeCard(List<String> failures) {
        if (!Upgrades.isUpgradeCardItem(ModItems.ESSENTIA_ACCESS_CARD.get())) {
            failures.add("the access card is not an AE2 upgrade card, so no upgrade slot would take it");
        }
    }

    /** The registration has to exist at all: an unregistered card is refused by AE2's slot filter. */
    private static void checkTerminalTakesOneCard(List<String> failures) {
        int room = Upgrades.getMaxInstallable(
                ModItems.ESSENTIA_ACCESS_CARD.get(), ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get());
        if (room != 1) {
            failures.add("the wireless arcane terminal accepts " + room
                    + " of the access card, not 1 - its upgrade slot would refuse it");
        }
    }

    /** The ME interface's registration from the previous batch must survive this one. */
    private static void checkInterfaceStillTakesCard(List<String> failures) {
        int room = Upgrades.getMaxInstallable(ModItems.ESSENTIA_ACCESS_CARD.get(), AEBlocks.INTERFACE);
        if (room != 1) {
            failures.add("the ME interface no longer takes the access card");
        }
    }

    /** The registration is per host, not global: an unrelated item must report no room for the card. */
    private static void checkUnrelatedItemTakesNone(List<String> failures) {
        int room = Upgrades.getMaxInstallable(ModItems.ESSENTIA_ACCESS_CARD.get(), Items.STONE);
        if (room != 0) {
            failures.add("an unrelated item reports room for the access card");
        }
    }

    /** The terminal item owns the slots, so the count comes from the item's own upgrade inventory. */
    private static void checkTerminalHasTwoUpgradeSlots(List<String> failures, Params params) {
        int slots = params.upgrades.size();
        if (slots != 2) {
            failures.add("the wireless arcane terminal has " + slots + " upgrade slots, expected 2");
        }
    }

    /** This is the same filter the player's click goes through, so a pass here is the slot's own rule. */
    private static void checkUpgradeSlotAcceptsCard(List<String> failures, Params params) {
        if (!params.upgrades.isItemValid(0, params.card)) {
            failures.add("the terminal's upgrade slot refuses the access card");
        }
    }

    /** A card in the slot has to be counted, or the menu's permission never sees it. */
    private static void checkInsertedCardIsCounted(List<String> failures, Params params) {
        params.upgrades.setItemDirect(0, params.card.copy());
        if (!params.upgrades.isInstalled(ModItems.ESSENTIA_ACCESS_CARD.get())) {
            failures.add("the card is in the slot but the terminal does not count it");
            return;
        }
        int counted = params.upgrades.getInstalledUpgrades(ModItems.ESSENTIA_ACCESS_CARD.get());
        if (counted != 1) {
            failures.add("the terminal counts " + counted + " access cards in one slot, expected 1");
        }
    }

    /** The headless stand-in for pulling the card: empty slot, permission gone. */
    private static void checkEmptiedSlotStopsCounting(List<String> failures, Params params) {
        params.upgrades.setItemDirect(0, ItemStack.EMPTY);
        if (params.upgrades.isInstalled(ModItems.ESSENTIA_ACCESS_CARD.get())) {
            failures.add("the terminal still counts a card after the slot was emptied");
        }
    }

    /** The card lives on the terminal's stack, so a fresh reader of the same stack still finds it. */
    private static void checkCardRidesOnTheStack(List<String> failures, Params params) {
        params.upgrades.setItemDirect(0, params.card.copy());
        IUpgradeInventory secondReader;
        try {
            secondReader = ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get().getUpgrades(params.terminal);
        } catch (Throwable thrown) {
            failures.add("the terminal's upgrade inventory could not be read a second time: "
                    + thrown.getClass().getSimpleName() + ": " + thrown.getMessage());
            return;
        }
        if (!secondReader.isInstalled(ModItems.ESSENTIA_ACCESS_CARD.get())) {
            failures.add("the card did not travel on the terminal's own stack");
        }
    }

    /** Both menus have to answer the existing payloads, which is why no new payload was needed. */
    private static void checkBothMenusAnswerTheFiller(List<String> failures) {
        // Neither menu is built here: a constructor needs a player.
        boolean essentiaAnswers = EssentiaTerminalReceiver.class.isAssignableFrom(MenuEssentiaTerminal.class);
        boolean arcaneAnswers = EssentiaTerminalReceiver.class.isAssignableFrom(MenuArcaneCraftingTerminal.class);
        if (!essentiaAnswers || !arcaneAnswers) {
            failures.add("a terminal menu does not answer the essentia filler payloads");
        }
    }

    /** Both sides of the card test have to exist: the menu asks it, the screen asks the menu. */
    private static void checkBothSidesAskForTheCard(List<String> failures) {
        // The gate cannot be driven here - it needs a menu, a player and a server level. Presence is what a
        // headless gate can hold: a rename or a widened gate would drop the check without failing to build.
        boolean menuGate;
        try {
            menuGate = MenuArcaneCraftingTerminal.class
                    .getDeclaredMethod("essentiaAccessGranted")
                    .getReturnType() == boolean.class;
        } catch (NoSuchMethodException missing) {
            menuGate = false;
        }
        if (!menuGate) {
            failures.add("the arcane terminal menu no longer declares the card check the filler calls");
            return;
        }
        boolean screenAsks = false;
        for (java.lang.reflect.Method method : MenuArcaneCraftingTerminal.class.getDeclaredMethods()) {
            if (method.getName().equals("hasEssentiaAccessCard") && method.getReturnType() == boolean.class) {
                screenAsks = true;
                break;
            }
        }
        if (!screenAsks) {
            failures.add("nothing on the menu answers the screen's question about the access card");
        }
    }
}
