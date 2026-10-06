package thaumicenergistics_ce.selftest;

import appeng.api.implementations.menuobjects.IPortableTerminal;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.ItemMenuHostLocator;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftingTransaction;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.blockentity.BlockEntityDistillationEncoder;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.menu.MenuDistillationEncoder;
import thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench;
import thaumicenergistics_ce.menu.MenuEssentiaTerminal;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;
import thaumicenergistics_ce.menu.slot.CrystalSlot;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import thaumicenergistics_ce.part.PartEssentiaTerminal;
import thaumicenergistics_ce.util.ThELog;

/**
 * Builds every menu this mod registers, once, and reports any that throw.
 * <ul>
 *   <li>Written for a bug nothing else could see: a menu constructor threw, silently suppressed.
 *   <li>Runs at the first player login, since a menu needs a player; off unless
 *       {@code THAUMICENERGISTICS_MENU_SELFTEST=true}.
 * </ul> */
public final class MenuSelfTest {

    private static boolean hasRun;

    private MenuSelfTest() {}

    public static void run(PlayerEvent.PlayerLoggedInEvent event) {
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_MENU_SELFTEST"))) {
            return;
        }
        if (hasRun) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        hasRun = true;

        Inventory inventory = player.getInventory();
        List<String> failures = new ArrayList<>();

        check(failures, "KNOWLEDGE_INSCRIBER", () -> new MenuKnowledgeInscriber(
                0, inventory, (BlockEntityKnowledgeInscriber) null));
        check(failures, "ESSENTIA_CELL_WORKBENCH", () -> new MenuEssentiaCellWorkbench(
                0, inventory, (BlockEntityEssentiaCellWorkbench) null));
        check(failures, "DISTILLATION_ENCODER", () -> new MenuDistillationEncoder(
                0, inventory, (BlockEntityDistillationEncoder) null));
        checkEncoderWellsAreWritable(failures, inventory);

        // AE2 accepts exactly three host kinds - BlockEntity, IPart, ItemMenuHost - and rejects a stand-in
        // that merely implements ITerminalHost, so build the host the way the game does, via AE2's locator.
        ItemStack terminal = new ItemStack(ModItems.WIRELESS_ESSENTIA_TERMINAL.get());
        ItemMenuHostLocator locator = new ItemMenuHostLocator() {
            @Override
            public ItemStack locateItem(Player player) {
                return terminal;
            }

            @Override
            public @Nullable BlockHitResult hitResult() {
                return null;
            }
        };
        IPortableTerminal host = locator.locate(player, IPortableTerminal.class);
        if (host == null) {
            failures.add("WIRELESS_ESSENTIA_TERMINAL offers no IPortableTerminal menu host, so a wireless"
                    + " terminal cannot be opened at all");
        } else {
            check(failures, "ARCANE_CRAFTING_TERMINAL", () -> new MenuArcaneCraftingTerminal(
                    ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get(), 0, inventory, host));
            check(failures, "ESSENTIA_TERMINAL", () -> new MenuEssentiaTerminal(
                    ModMenuTypes.ESSENTIA_TERMINAL.get(), 0, inventory, host));
        }

        // The wireless arcane terminal is the same menu under a menu type of its own, so it gets its own
        // host and its own look at the grid: a second menu type with no slots would still open blank.
        ItemStack wirelessArcane = new ItemStack(ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get());
        ItemMenuHostLocator arcaneLocator = new ItemMenuHostLocator() {
            @Override
            public ItemStack locateItem(Player player) {
                return wirelessArcane;
            }

            @Override
            public @Nullable BlockHitResult hitResult() {
                return null;
            }
        };
        IPortableTerminal arcaneHost = arcaneLocator.locate(player, IPortableTerminal.class);
        if (arcaneHost == null) {
            failures.add("WIRELESS_ARCANE_CRAFTING_TERMINAL offers no IPortableTerminal menu host, so the"
                    + " carried terminal cannot be opened at all");
        } else {
            check(failures, "WIRELESS_ARCANE_CRAFTING_TERMINAL", () -> new MenuArcaneCraftingTerminal(
                    ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(), 0, inventory, arcaneHost));
            checkUnpairedWirelessTerminalShowsItsGrid(failures, inventory, arcaneHost);
        }

        PartArcaneCraftingTerminal actPart =
                new PartArcaneCraftingTerminal(ModItems.ARCANE_CRAFTING_TERMINAL.get());
        check(failures, "ARCANE_CRAFTING_TERMINAL (with its part)", () -> new MenuArcaneCraftingTerminal(
                ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get(), 0, inventory, actPart));

        PartEssentiaTerminal terminalPart = new PartEssentiaTerminal(ModItems.ESSENTIA_TERMINAL.get());
        check(failures, "ESSENTIA_TERMINAL (with its part)", () -> new MenuEssentiaTerminal(
                ModMenuTypes.ESSENTIA_TERMINAL.get(), 0, inventory, terminalPart));


        checkCraftReachesTheResult(player.serverLevel(), player, inventory, failures);

        report(failures);
    }

    private static void checkCraftReachesTheResult(            ServerLevel level, ServerPlayer player, Inventory inventory, List<String> failures) {
        ThEArcanePattern pattern = null;
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            if (output.isEmpty() || arcane.getCrystals().isEmpty()) {
                continue;
            }
            ThEArcanePattern candidate = ThEArcanePattern.fromRecipe(arcane, output);
            if (candidate != null && !candidate.primalCrystals().isEmpty()) {
                pattern = candidate;
                break;
            }
        }
        if (pattern == null) {
            System.out.println("[menu] no crystal-costing arcane recipe, so the craft path is unchecked");
            return;
        }

        PartArcaneCraftingTerminal part =
                new PartArcaneCraftingTerminal(ModItems.ARCANE_CRAFTING_TERMINAL.get());
        MenuArcaneCraftingTerminal menu = new MenuArcaneCraftingTerminal(
                ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get(), 0, inventory, part);

        // Found by semantic, not by index: AE2's base class adds five slots before ours.
        if (menu.resultSlot() == null) {
            failures.add("the ACT menu offers no result slot after being built with its part");
            dumpSlots(menu);
            return;
        }
        if (menu.crystalSlots().size() != PartArcaneCraftingTerminal.CRYSTAL_SLOTS) {
            failures.add("the ACT menu has " + menu.crystalSlots().size() + " crystal slots, expected "
                    + PartArcaneCraftingTerminal.CRYSTAL_SLOTS);
            dumpSlots(menu);
            return;
        }
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; i++) {
            if (menu.crystalSlots().get(i).getContainerSlot() != i) {
                failures.add("crystal slot " + i + " of the menu does not name container slot " + i);
                dumpSlots(menu);
                return;
            }
        }

        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; i++) {
            var slot = menu.crystalSlots().get(i);
            if (!(slot instanceof CrystalSlot crystalSlot)) {
                failures.add("crystal slot " + i + " is not aspect-pinned, so it accepts any crystal");
                continue;
            }
            ResourceKey<IAspect> aspect = MenuArcaneCraftingTerminal.aspectOf(i);
            if (!crystalSlot.requiredAspect().equals(aspect)) {
                failures.add("crystal slot " + i + " is pinned to " + crystalSlot.requiredAspect()
                        + ", expected " + aspect);
            }
            if (!slot.mayPlace(TcRegistry.crystalFor(
                    Aspects.resolve(level.registryAccess(), aspect), 1))) {
                failures.add("crystal slot " + i + " refuses a crystal of its own aspect " + aspect);
            }
            ResourceKey<IAspect> other = MenuArcaneCraftingTerminal.aspectOf(
                    (i + 1) % PartArcaneCraftingTerminal.CRYSTAL_SLOTS);
            if (slot.mayPlace(TcRegistry.crystalFor(
                    Aspects.resolve(level.registryAccess(), other), 1))) {
                failures.add("crystal slot " + i + " accepts a " + other
                        + " crystal - the six slots are not pinned to their aspects");
            }
        }
        System.out.println("[menu] crystal slots: " + PartArcaneCraftingTerminal.CRYSTAL_SLOTS
                + " pinned, one primal aspect each");

        if (menu.wandSlot() == null) {
            failures.add("the ACT menu offers no wand slot after being built with its part");
            dumpSlots(menu);
            return;
        }

        for (int i = 0; i < PartArcaneCraftingTerminal.GRID_SIZE; i++) {
            part.craftingGrid()
                    .setItemDirect(i, i < pattern.grid().size() ? pattern.grid().get(i) : ItemStack.EMPTY);
        }
        ItemStack[] crystals = new ItemStack[PartArcaneCraftingTerminal.CRYSTAL_SLOTS];
        int written = 0;
        for (AspectInstance entry :
                pattern.primalCrystals().entries()) {
            if (written >= crystals.length) {
                break;
            }
            crystals[written] = TcRegistry.crystalFor(entry.aspect(), entry.amount());
            part.crystalInventory().setItemDirect(written, crystals[written]);
            written++;
        }

        menu.broadcastChanges();

        var resultSlot = menu.resultSlot();
        ItemStack result = resultSlot == null ? ItemStack.EMPTY : resultSlot.getItem();
        var failure = resultSlot == null
                ? ArcaneCraftingTransaction.Failure.NO_RECIPE
                : resultSlot.lastFailure();

        if (failure == ArcaneCraftingTransaction.Failure.NO_RECIPE) {
            failures.add("a grid laid out for " + pattern.result()
                    + " does not resolve in the terminal - the recipe is not being matched");
            return;
        }
        if (failure != ArcaneCraftingTransaction.Failure.NONE
                && failure != ArcaneCraftingTransaction.Failure.PAYMENT_UNAVAILABLE
                && failure != ArcaneCraftingTransaction.Failure.RESEARCH_LOCKED) {
            failures.add("the terminal refused " + pattern.result() + " with " + failure);
            return;
        }
        if (result.isEmpty()) {
            System.out.println("[menu] craft path: " + pattern.result()
                    + " matches and is refused only for " + failure
                    + (failure == ArcaneCraftingTransaction.Failure.RESEARCH_LOCKED
                            ? ", which is this player's research and not the grid"
                            : ", which is expected for a part that is in no world"));
            return;
        }
        if (!ItemStack.isSameItemSameComponents(result, pattern.result())) {
            failures.add("the terminal offered " + result + " for a grid laid out for " + pattern.result());
        }
        System.out.println("[menu] craft path: " + pattern.result() + " is offered with its crystals in the"
                + " slots and none in the grid");

        for (int i = 0; i < crystals.length; i++) {
            part.crystalInventory().setItemDirect(i, ItemStack.EMPTY);
        }
        menu.broadcastChanges();
        if (menu.resultSlot() != null && !menu.resultSlot().getItem().isEmpty()) {
            System.out.println("[menu] note: " + pattern.result()
                    + " is craftable without its crystals (a wand or a vis source is covering the cost)");
        }
    }

    /**
     * A wireless terminal with nothing paired still shows the whole workbench. A menu that decided its
     * slots from whether the placed part resolved would drop them on a client that cannot see that chunk,
     * which is every client that is far enough away for a wireless terminal to be worth carrying.
     */
    private static void checkUnpairedWirelessTerminalShowsItsGrid(
            List<String> failures, Inventory inventory, IPortableTerminal host) {
        MenuArcaneCraftingTerminal menu = new MenuArcaneCraftingTerminal(
                ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(), 0, inventory, host);
        if (menu.part() != null) {
            failures.add("the wireless arcane terminal resolved a placed terminal with nothing paired, so the"
                    + " grid it shows is not the one an unpaired client would get");
        }
        int grid = menu.getSlots(SlotSemantics.CRAFTING_GRID).size();
        if (grid != PartArcaneCraftingTerminal.GRID_SIZE) {
            failures.add("the unpaired wireless arcane terminal has " + grid + " grid slots, expected "
                    + PartArcaneCraftingTerminal.GRID_SIZE);
            dumpSlots(menu);
            return;
        }
        if (menu.crystalSlots().size() != PartArcaneCraftingTerminal.CRYSTAL_SLOTS) {
            failures.add("the unpaired wireless arcane terminal has " + menu.crystalSlots().size()
                    + " crystal slots, expected " + PartArcaneCraftingTerminal.CRYSTAL_SLOTS);
            dumpSlots(menu);
            return;
        }
        if (menu.wandSlot() == null || menu.resultSlot() == null) {
            failures.add("the unpaired wireless arcane terminal is missing its wand slot or its result slot");
            dumpSlots(menu);
            return;
        }
        ThELog.LOG.info("[menu] unpaired wireless terminal: {} grid slots, {} crystal slots, wand and result",
                grid, menu.crystalSlots().size());
    }

    private static void dumpSlots(AbstractContainerMenu menu) {
        System.out.println("[menu] actual slot layout (" + menu.slots.size() + " slots):");
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            System.out.println("  [" + i + "] " + slot.getClass().getSimpleName()
                    + " containerSlot=" + slot.getContainerSlot()
                    + " pos=" + slot.x + "," + slot.y);
        }
    }

    private interface Builder {
        AbstractContainerMenu build();
    }

    private static void checkEncoderWellsAreWritable(List<String> failures, Inventory inventory) {
        MenuDistillationEncoder menu = new MenuDistillationEncoder(
                0, inventory, (BlockEntityDistillationEncoder) null);
        ItemStack written = new ItemStack(ModItems.DISTILLATION_ENCODER.get());
        for (int index : new int[] {MenuDistillationEncoder.MENU_SOURCE, MenuDistillationEncoder.MENU_ENCODED}) {
            menu.slots.get(index).set(written.copy());
            if (!ItemStack.matches(menu.slots.get(index).getItem(), written)) {
                failures.add("DISTILLATION_ENCODER well " + index + " dropped a server write: set() is a no-op,"
                        + " so nothing the server writes can ever reach the screen");
                return;
            }
        }
        ThELog.LOG.info("[menu] DISTILLATION_ENCODER wells accept a server write");
    }

    private static void check(List<String> failures, String name, Builder builder) {
        AbstractContainerMenu menu;
        try {
            menu = builder.build();
        } catch (RuntimeException | LinkageError e) {
            failures.add(name + " threw from its constructor: " + e);
            return;
        }
        if (menu == null) {
            failures.add(name + " returned null");
            return;
        }

        int playerSlots = 0;
        for (var slot : menu.slots) {
            if (slot.container instanceof Inventory) {
                playerSlots++;
            }
        }
        if (playerSlots != 36) {
            failures.add(name + " has " + playerSlots + " slot(s) backed by the player inventory, expected 36 "
                    + "- createPlayerInventorySlots must run exactly once");
            return;
        }
        if (menu.slots.size() == playerSlots) {
            failures.add(name + " has the player's slots and none of its own");
            return;
        }
        ThELog.LOG.info("[menu] {} built with {} slot(s)", name, menu.slots.size());
    }

    private static void report(List<String> failures) {
        if (failures.isEmpty()) {
            ThELog.LOG.info("[menu] self-test passed");
            return;
        }
        for (String failure : failures) {
            ThELog.LOG.error("[menu] FAIL {}", failure);
        }
        ThELog.LOG.error("[menu] self-test failed with {} problem(s)", failures.size());
    }
}
