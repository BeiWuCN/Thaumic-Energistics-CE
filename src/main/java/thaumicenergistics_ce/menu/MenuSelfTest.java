package thaumicenergistics_ce.menu;

import appeng.api.implementations.menuobjects.IPortableTerminal;
import appeng.menu.locator.ItemMenuHostLocator;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftingTransaction;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.leclowndu93150.thaumaturge.content.taint.item.EssentiaCrystalFactory;
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
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.blockentity.BlockEntityDistillationEncoder;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;
import thaumicenergistics_ce.blockentity.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.menu.slot.CrystalSlot;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import thaumicenergistics_ce.part.PartEssentiaTerminal;

/**
 * Builds every menu this mod registers, once, and reports any that throw.
 *
 * <p>Written for a bug nothing else could see: {@code MenuArcaneCraftingTerminal} called
 * {@code createPlayerInventorySlots} twice, the constructor threw, and the server suppressed it. Runs on
 * the first player login, because a menu needs an {@code Inventory} and an {@code Inventory} needs a
 * player; off unless {@code THAUMICENERGISTICS_MENU_SELFTEST=true}.
 */
public final class MenuSelfTest {

    /** One run per server: the menus do not change between players. */
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

        // Every machine menu here accepts a null host, which is what makes the client half work. The casts
        // are needed: these classes also have a RegistryFriendlyByteBuf constructor, and null fits both.
        check(failures, "KNOWLEDGE_INSCRIBER", () -> new MenuKnowledgeInscriber(
                0, inventory, (BlockEntityKnowledgeInscriber) null));
        check(failures, "ESSENTIA_CELL_WORKBENCH", () -> new MenuEssentiaCellWorkbench(
                0, inventory, (BlockEntityEssentiaCellWorkbench) null));
        check(failures, "DISTILLATION_ENCODER", () -> new MenuDistillationEncoder(
                0, inventory, (BlockEntityDistillationEncoder) null));
        checkEncoderWellsAreWritable(failures, inventory);

        // AE2 accepts exactly three host kinds - BlockEntity, IPart, ItemMenuHost - and rejects a stand-in
        // that merely implements ITerminalHost. Build the host the way the game does, through AE2's locator.
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

        // The wired path is where the layout is: the ACT menu takes its grid from
        // `host instanceof PartArcaneCraftingTerminal`, so the wireless run above never reaches the nine
        // cells, the result well or the wand slot. A part can be built without a cable.
        PartArcaneCraftingTerminal actPart =
                new PartArcaneCraftingTerminal(ModItems.ARCANE_CRAFTING_TERMINAL.get());
        check(failures, "ARCANE_CRAFTING_TERMINAL (with its part)", () -> new MenuArcaneCraftingTerminal(
                ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get(), 0, inventory, actPart));

        PartEssentiaTerminal terminalPart = new PartEssentiaTerminal(ModItems.ESSENTIA_TERMINAL.get());
        check(failures, "ESSENTIA_TERMINAL (with its part)", () -> new MenuEssentiaTerminal(
                ModMenuTypes.ESSENTIA_TERMINAL.get(), 0, inventory, terminalPart));

        // NOT covered: MenuArcaneAssembler needs a level and a loaded chunk for its concrete block entity.

        checkCraftReachesTheResult(player.serverLevel(), player, inventory, failures);

        report(failures);
    }

    /** Lays a recipe out in the terminal and checks the product appears. The result well is filled by
     * {@code ArcaneCraftingResultSlot.refresh()}, which nothing used to call - the grid and crystal slots
     * are AE2 inventories the part owns, so no container change callback reaches the menu for them. */
    private static void checkCraftReachesTheResult(
            ServerLevel level, ServerPlayer player, Inventory inventory, List<String> failures) {
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
            // A *primal* crystal cost is the case to exercise: it can be paid from the slots or from a
            // wand's vis. Non-primal crystals are unused - see crystalItems.
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

        // Found by semantic, not by index, because AE2's base class adds five slots before ours.
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

        // Each crystal slot is pinned to one primal aspect, the way the arcane workbench's six are. Asked of
        // the slot rather than read off a field: the defect was that the slots were plain AppEngSlots and the
        // rule existed only in the shape of the data, so anything short of mayPlace() would have passed while
        // the slot went on accepting six Aer crystals.
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
            if (!slot.mayPlace(EssentiaCrystalFactory.of(
                    Aspects.resolve(level.registryAccess(), aspect), 1))) {
                failures.add("crystal slot " + i + " refuses a crystal of its own aspect " + aspect);
            }
            ResourceKey<IAspect> other = MenuArcaneCraftingTerminal.aspectOf(
                    (i + 1) % PartArcaneCraftingTerminal.CRYSTAL_SLOTS);
            if (slot.mayPlace(EssentiaCrystalFactory.of(
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
        for (com.leclowndu93150.thaumaturge.api.aspect.AspectInstance entry :
                pattern.primalCrystals().entries()) {
            if (written >= crystals.length) {
                break;
            }
            crystals[written] = EssentiaCrystalFactory.of(entry.aspect(), entry.amount());
            part.crystalInventory().setItemDirect(written, crystals[written]);
            written++;
        }

        menu.broadcastChanges();

        var resultSlot = menu.resultSlot();
        ItemStack result = resultSlot == null ? ItemStack.EMPTY : resultSlot.getItem();
        var failure = resultSlot == null
                ? ArcaneCraftingTransaction.Failure.NO_RECIPE
                : resultSlot.lastFailure();

        // NOT_RECIPE is the bug this check exists for: a grid laid out as the recipe asks that the terminal
        // cannot see. PAYMENT_UNAVAILABLE is expected - the vis comes from the aura of the block it is on.
        //
        // RESEARCH_LOCKED is acceptance too, and it has to be taken on Thaumaturge's word: match() in
        // ArcaneCraftingTransactions finds the recipe through matches(...) first and only then asks
        // doesPassGate(player), so that failure is only reachable from a grid that already resolved. It means
        // the check ran and the player has not unlocked the output - usually a fresh dev player, who has no
        // research at all - and reporting it as a refusal would make this test about the player's progress.
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

        // Without the crystals the same grid is refused, which proves they paid for it and not a wand.
        for (int i = 0; i < crystals.length; i++) {
            part.crystalInventory().setItemDirect(i, ItemStack.EMPTY);
        }
        menu.broadcastChanges();
        if (menu.resultSlot() != null && !menu.resultSlot().getItem().isEmpty()) {
            System.out.println("[menu] note: " + pattern.result()
                    + " is craftable without its crystals (a wand or a vis source is covering the cost)");
        }
    }

    /** Prints a menu's slots in index order with the slot type and container slot it actually holds. */
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

    /** Checks that the Distillation Encoder's written-pattern well can actually be written to: it was a
     * display-only slot whose {@code set} was a no-op, so server writes never reached the screen. */
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
        ThaumicEnergistics.LOG.info("[menu] DISTILLATION_ENCODER wells accept a server write");
    }

    /** Runs a menu constructor, then checks for the player's 36 slots and at least one slot of its own. */
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
        ThaumicEnergistics.LOG.info("[menu] {} built with {} slot(s)", name, menu.slots.size());
    }

    private static void report(List<String> failures) {
        if (failures.isEmpty()) {
            ThaumicEnergistics.LOG.info("[menu] self-test passed");
            return;
        }
        for (String failure : failures) {
            ThaumicEnergistics.LOG.error("[menu] FAIL {}", failure);
        }
        ThaumicEnergistics.LOG.error("[menu] self-test failed with {} problem(s)", failures.size());
    }
}
