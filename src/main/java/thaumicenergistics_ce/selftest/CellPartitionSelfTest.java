package thaumicenergistics_ce.selftest;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.definitions.AEItems;
import appeng.me.cells.BasicCellInventory;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.CellPartitionSlot;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;
import thaumicenergistics_ce.init.ModBlocks;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench;
import thaumicenergistics_ce.net.PartitionWellPayload;
import thaumicenergistics_ce.item.ItemEssentiaCell;
import thaumicenergistics_ce.util.ThELog;

/**
 * Checks that a partition mark reaches the cell and does something there; off unless its env switch is set.
 * <ul>
 *   <li>The half a client cannot check: a fake slot's {@code set} writes into the menu's own copy of the
 *       grid and sends nothing, so {@code PartitionWellPayload} carries a mark to the server.
 *   <li>Puts nothing into the world: the workbench it builds is never added to a level.
 * </ul>
 */
public final class CellPartitionSelfTest {

    /** The well the checks mark. The grid is 63 wells, all alike. */
    private static final int WELL = 0;

    /** One run per server: the checks do not depend on the player and are cheap. */
    private static boolean hasRun;

    private CellPartitionSelfTest() {}

    public static void onServerStarted(ServerStartedEvent event) {
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_CELLPARTITION_SELFTEST"))) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        if (hasRun || level == null) {
            return;
        }
        hasRun = true;

        List<String> failures = new ArrayList<>();
        run(level, failures);
        report(failures);
    }

    private static void run(ServerLevel level, List<String> failures) {
        // A named aspect rather than whatever comes first: two of them are needed, and the check reads
        // better when the log says which.
        Holder<IAspect> aer = AEssentiaKeyType.aspectOf(level, ResourceLocation.fromNamespaceAndPath("thaumaturge", "aer"));
        Holder<IAspect> ignis = AEssentiaKeyType.aspectOf(level, ResourceLocation.fromNamespaceAndPath("thaumaturge", "ignis"));
        if (aer == null || ignis == null) {
            failures.add("aspects thaumaturge:aer and thaumaturge:ignis did not resolve -"
                    + " is the aspect registry populated?");
            return;
        }
        AEssentiaKey marked = AEssentiaKey.of(aer);
        AEssentiaKey other = AEssentiaKey.of(ignis);

        // What PartitionWellPayload's handler does once its guards have passed: the cell is in the
        // slot, and the well is written through to the workbench's own grid.
        BlockEntityEssentiaCellWorkbench bench = newWorkbench();
        bench.setLevel(level);
        bench.getInventory().setItem(0, new ItemStack(ModItems.ESSENTIA_CELL_1K.get()));
        bench.getPartition().setStack(WELL, new GenericStack(marked, 1));

        checkMarkReachedTheCell(bench, marked, failures);
        checkTheMarkSurvivesASave(bench, level, marked, failures);
        checkTheMarkFiltersTheCell(bench, marked, other, failures);
        checkTheInverterCardFlipsTheGrid(level, marked, other, failures);
        checkTheCardSlotTakesTheCard(level, failures);
        checkTheMenuCarriesTheGrid(level, bench, other, failures);
        checkOneMarkFillsOneWell(level, bench, marked, failures);
        checkTakingTheCellOutEmptiesTheWells(level, bench, failures);
    }

    /**
     * One type, one well: marking an aspect that already has a well moves it there. Two identical marks
     * in one grid were the report, and a whitelist of types has no use for the same type twice.
     */
    private static void checkOneMarkFillsOneWell(
            ServerLevel level, BlockEntityEssentiaCellWorkbench bench, AEssentiaKey marked, List<String> failures) {
        try {
            var player = FakePlayerFactory.getMinecraft(level);
            MenuEssentiaCellWorkbench menu = new MenuEssentiaCellWorkbench(2, player.getInventory(), bench);
            if (!(menu.getSlots(SlotSemantics.CONFIG).get(WELL) instanceof CellPartitionSlot)) {
                failures.add("the wells are not AE2's partition slots, so a disabled one cannot draw itself faint");
                return;
            }
            if (!menu.isPartitionSlotEnabled(WELL)) {
                failures.add("a workbench holding a cell says its wells are disabled");
                return;
            }
            menu.setPartitionWell(WELL, marked.getId(), player);
            menu.setPartitionWell(1, marked.getId(), player);
            GenericStack first = bench.getPartition().getStack(WELL);
            GenericStack second = bench.getPartition().getStack(1);
            if (first != null || second == null || !marked.equals(second.what())) {
                failures.add("marking " + marked + " in a second well left well " + WELL + " holding "
                        + describe(first) + " and well 1 holding " + describe(second) + " - one type, one well");
                return;
            }
            menu.setPartitionWell(1, PartitionWellPayload.CLEAR, player);
            GenericStack afterTheClick = bench.getPartition().getStack(1);
            if (afterTheClick != null) {
                failures.add("taking the mark out of well 1 left " + describe(afterTheClick) + " in it");
                return;
            }
            ThELog.LOG.info("[cellpartition] one type fills one well, and a click takes the mark back out");
        } catch (RuntimeException | LinkageError e) {
            failures.add("marking two wells through the menu threw " + e);
        }
    }

    /**
     * The cell out and the wells are empty and disabled: the marks live on the cell item, so a workbench
     * with no cell has nothing to show and AE2 draws the wells faint. Run last: it leaves a fresh cell in.
     */
    private static void checkTakingTheCellOutEmptiesTheWells(
            ServerLevel level, BlockEntityEssentiaCellWorkbench bench, List<String> failures) {
        bench.getInventory().setItem(0, ItemStack.EMPTY);
        for (int well = 0; well < BlockEntityEssentiaCellWorkbench.PARTITION_SLOTS; well++) {
            GenericStack left = bench.getPartition().getStack(well);
            if (left != null) {
                failures.add("with the cell out, well " + well + " still holds " + describe(left));
                return;
            }
        }
        try {
            MenuEssentiaCellWorkbench menu = new MenuEssentiaCellWorkbench(
                    3, FakePlayerFactory.getMinecraft(level).getInventory(), bench);
            if (menu.isPartitionSlotEnabled(WELL)) {
                failures.add("with no cell the wells still say they are enabled, so they would not go faint");
                return;
            }
            // AE2 draws the upgrade panel, tooltip and all, only while a card slot says it is enabled, so
            // the panel goes with the cell: this is what makes the right-hand column appear only with one.
            for (var slot : menu.getSlots(SlotSemantics.UPGRADE)) {
                if (!(slot instanceof AppEngSlot cardSlot) || cardSlot.isSlotEnabled()) {
                    failures.add("with no cell an upgrade slot still reports itself enabled, so AE2 would keep"
                            + " drawing the workbench's upgrade panel");
                    return;
                }
            }
            ThELog.LOG.info("[cellpartition] with no cell the wells and the upgrade panel are both disabled");
        } catch (RuntimeException | LinkageError e) {
            failures.add("opening a cell-less menu threw " + e);
            return;
        }
        bench.getInventory().setItem(0, new ItemStack(ModItems.ESSENTIA_CELL_1K.get()));
        ThELog.LOG.info("[cellpartition] taking the cell out empties the wells and disables them");
    }

    /**
     * The mark has to be on the cell item itself, because that is what a drive reads. A mark that only
     * ever lived in the workbench would go with the workbench.
     */
    private static void checkMarkReachedTheCell(
            BlockEntityEssentiaCellWorkbench bench, AEssentiaKey marked, List<String> failures) {
        GenericStack onTheCell = cellPartition(bench, WELL);
        if (onTheCell == null || !marked.equals(onTheCell.what())) {
            failures.add("a mark written to well " + WELL + " never reached the cell: the cell holds "
                    + describe(onTheCell) + ", the well holds "
                    + describe(bench.getPartition().getStack(WELL)));
            return;
        }
        ThELog.LOG.info(
                "[cellpartition] the mark reached the cell: well {} = {}", WELL, describe(onTheCell));
    }

    /**
     * Take the cell out and put it back and the wells are rebuilt from it. That is the reported symptom:
     * the marks went away. Here it is the same save and load the game does.
     */
    private static void checkTheMarkSurvivesASave(
            BlockEntityEssentiaCellWorkbench bench,
            ServerLevel level,
            AEssentiaKey marked,
            List<String> failures) {
        CompoundTag saved = bench.saveWithoutMetadata(level.registryAccess());
        BlockEntityEssentiaCellWorkbench reloaded = newWorkbench();
        reloaded.setLevel(level);
        reloaded.loadWithComponents(saved, level.registryAccess());

        GenericStack onTheCell = cellPartition(reloaded, WELL);
        GenericStack inTheWell = reloaded.getPartition().getStack(WELL);
        if (onTheCell == null || inTheWell == null || !marked.equals(inTheWell.what())) {
            failures.add("after a save and load the cell holds " + describe(onTheCell) + " and well "
                    + WELL + " holds " + describe(inTheWell) + " - the marks do not come back");
            return;
        }
        ThELog.LOG.info(
                "[cellpartition] the mark came back out of a save: cell {}, well {}",
                describe(onTheCell),
                describe(inTheWell));
    }

    /**
     * And it has to do something: a well marked aer means the cell takes aer and refuses anything else.
     * A mark that is stored but ignored is the "标记了但没作用" report.
     */
    private static void checkTheMarkFiltersTheCell(
            BlockEntityEssentiaCellWorkbench bench, AEssentiaKey marked, AEssentiaKey other, List<String> failures) {
        StorageCell inventory = BasicCellInventory.createInventory(bench.getCell(), null);
        if (inventory == null) {
            failures.add("AE2 did not build a cell inventory from the marked cell");
            return;
        }
        long allowed = inventory.insert(marked, 100L, Actionable.MODULATE, source());
        long refused = inventory.insert(other, 100L, Actionable.MODULATE, source());
        if (allowed != 100L || refused != 0L) {
            failures.add("with well " + WELL + " marked " + marked + ", the cell took " + allowed + " of it"
                    + " and " + refused + " of " + other + " - expected 100 and 0");
            return;
        }
        ThELog.LOG.info("[cellpartition] the mark filters: the cell took {} {} and refused {}", allowed, marked, other);
    }

    /**
     * The menu the player opens, built the way the server builds it. The counts have to match the style
     * document, or the screen throws while it lays its slots out; a mark is then sent the way a client does.
     */
    private static void checkTheMenuCarriesTheGrid(
            ServerLevel level, BlockEntityEssentiaCellWorkbench bench, AEssentiaKey aspect, List<String> failures) {
        try {
            var player = FakePlayerFactory.getMinecraft(level);
            MenuEssentiaCellWorkbench menu = new MenuEssentiaCellWorkbench(1, player.getInventory(), bench);
            int cells = menu.getSlots(SlotSemantics.STORAGE_CELL).size();
            int wells = menu.getSlots(SlotSemantics.CONFIG).size();
            int upgrades = menu.getSlots(SlotSemantics.UPGRADE).size();
            int playerSlots = menu.getSlots(SlotSemantics.PLAYER_INVENTORY).size()
                    + menu.getSlots(SlotSemantics.PLAYER_HOTBAR).size();
            if (cells != 1
                    || wells != MenuEssentiaCellWorkbench.partitionSlotCount()
                    || upgrades != ItemEssentiaCell.UPGRADE_SLOTS
                    || playerSlots != 36) {
                failures.add("the workbench menu has " + cells + " cell slot(s), " + wells + " well(s), "
                        + upgrades + " upgrade slot(s) and " + playerSlots + " player slot(s) - expected 1, "
                        + MenuEssentiaCellWorkbench.partitionSlotCount() + ", "
                        + ItemEssentiaCell.UPGRADE_SLOTS + ", 36");
                return;
            }
            // The other half of the same contract: with a cell in, the card slots have to read as usable,
            // or the panel that should come back would stay hidden.
            for (var slot : menu.getSlots(SlotSemantics.UPGRADE)) {
                if (!(slot instanceof AppEngSlot cardSlot) || !cardSlot.isSlotEnabled()) {
                    failures.add("with a cell in, an upgrade slot reports itself disabled, so the workbench's"
                            + " upgrade panel would stay hidden");
                    return;
                }
            }
            // What PartitionWellPayload's handler calls once its guards have passed.
            menu.setPartitionWell(1, aspect.getId(), player);
            GenericStack throughTheMenu = cellPartition(bench, 1);
            if (throughTheMenu == null || !aspect.equals(throughTheMenu.what())) {
                failures.add("a mark through the menu left well 1 holding " + describe(throughTheMenu));
                return;
            }
            ThELog.LOG.info(
                    "[cellpartition] the menu carries the grid: {} well(s), {} upgrade slot(s), and a mark"
                            + " through the menu reached the cell",
                    wells,
                    upgrades);
        } catch (RuntimeException | LinkageError e) {
            failures.add("building the workbench menu threw " + e);
        }
    }

    /**
     * The inverter card flips the grid from a whitelist to a blacklist, so the cell then takes everything
     * the wells do not name. AE2 works that out in BasicCellInventory, from the card on the cell itself.
     */
    private static void checkTheInverterCardFlipsTheGrid(
            ServerLevel level, AEssentiaKey marked, AEssentiaKey other, List<String> failures) {
        BlockEntityEssentiaCellWorkbench bench = newWorkbench();
        bench.setLevel(level);
        bench.getInventory().setItem(0, new ItemStack(ModItems.ESSENTIA_CELL_1K.get()));
        bench.getPartition().setStack(WELL, new GenericStack(marked, 1));
        ItemStack card = new ItemStack(AEItems.INVERTER_CARD.get());
        IUpgradeInventory upgrades = bench.getUpgrades();
        if (!upgrades.isItemValid(0, card)) {
            failures.add("the workbench refuses an inverter card in a cell's upgrade slot");
            return;
        }
        upgrades.setItemDirect(0, card);
        if (upgrades.getInstalledUpgrades(AEItems.INVERTER_CARD) != 1) {
            failures.add("the card went into the cell's upgrade slot but the cell does not count it");
            return;
        }
        // The drive's own entry point, not a shortcut: this is what mounts the cell when it is inserted.
        StorageCell inventory = StorageCells.getCellInventory(bench.getCell(), null);
        if (inventory == null) {
            failures.add("AE2 did not build a cell inventory from the inverted cell");
            return;
        }
        long refused = inventory.insert(marked, 100L, Actionable.MODULATE, source());
        long allowed = inventory.insert(other, 100L, Actionable.MODULATE, source());
        if (refused != 0L || allowed != 100L) {
            failures.add("with an inverter card on the cell, the cell took " + allowed + " of " + other
                    + " and " + refused + " of " + marked + " - expected 100 and 0");
            return;
        }
        // The card travels with the cell: a second workbench sees it on a copy of the stack.
        BlockEntityEssentiaCellWorkbench second = newWorkbench();
        second.setLevel(level);
        second.getInventory().setItem(0, bench.getCell().copy());
        if (second.getUpgrades().getInstalledUpgrades(AEItems.INVERTER_CARD) != 1) {
            failures.add("the inverter card did not travel with the cell out of the workbench");
            return;
        }
        checkAMarkStillReachesTheCell(level, bench, other, failures);
        checkAnInvertedCellWithNoMarks(level, marked);
        ThELog.LOG.info(
                "[cellpartition] the inverter card flips the grid: it refused the marked {} and took {}",
                marked,
                other);
    }

    /**
     * The player's own way in: a click that carries an inverter card onto a card slot. A slot that
     * refuses the card would leave the cell unable to wear one, which reads as the card doing nothing.
     */
    private static void checkTheCardSlotTakesTheCard(ServerLevel level, List<String> failures) {
        BlockEntityEssentiaCellWorkbench bench = newWorkbench();
        bench.setLevel(level);
        bench.getInventory().setItem(0, new ItemStack(ModItems.ESSENTIA_CELL_1K.get()));
        ItemStack card = new ItemStack(AEItems.INVERTER_CARD.get());
        String where = "the click never happened";
        try {
            var player = FakePlayerFactory.getMinecraft(level);
            MenuEssentiaCellWorkbench menu = new MenuEssentiaCellWorkbench(6, player.getInventory(), bench);
            Slot slot = menu.getSlots(SlotSemantics.UPGRADE).get(0);
            menu.setCarried(card);
            menu.clicked(menu.slots.indexOf(slot), 0, ClickType.PICKUP, player);
            where = "the card slot holds " + slot.getItem() + " and the cursor holds " + menu.getCarried();
        } catch (RuntimeException | LinkageError e) {
            failures.add("a click on the card slot threw " + e);
            return;
        }
        if (bench.getUpgrades().getInstalledUpgrades(AEItems.INVERTER_CARD) != 1) {
            failures.add("a click on the card slot did not put the card on the cell: " + where);
            return;
        }
        ThELog.LOG.info("[cellpartition] a click on a card slot puts the inverter card on the cell");
    }

    /** With a card in, the wells still have to take a mark: one half of the report was that they did not. */
    private static void checkAMarkStillReachesTheCell(
            ServerLevel level, BlockEntityEssentiaCellWorkbench bench, AEssentiaKey aspect, List<String> failures) {
        try {
            var player = FakePlayerFactory.getMinecraft(level);
            MenuEssentiaCellWorkbench menu = new MenuEssentiaCellWorkbench(5, player.getInventory(), bench);
            if (!menu.isPartitionSlotEnabled(WELL)) {
                failures.add("a cell wearing an inverter card leaves the wells disabled");
                return;
            }
            menu.setPartitionWell(1, aspect.getId(), player);
        } catch (RuntimeException | LinkageError e) {
            failures.add("marking a well with an inverter card installed threw " + e);
            return;
        }
        GenericStack throughTheMenu = cellPartition(bench, 1);
        if (throughTheMenu == null || !aspect.equals(throughTheMenu.what())) {
            failures.add("with an inverter card, a mark through the menu left well 1 holding "
                    + describe(throughTheMenu));
            return;
        }
        ThELog.LOG.info(
                "[cellpartition] the wells still take a mark with a card in: well 1 = {}",
                describe(throughTheMenu));
    }

    /**
     * The other reading of the report, logged rather than asserted: an inverted cell with an empty grid.
     * AE2's own cells answer this the same way, since the same constructor decides it for all of them.
     */
    private static void checkAnInvertedCellWithNoMarks(ServerLevel level, AEssentiaKey marked) {
        BlockEntityEssentiaCellWorkbench bare = newWorkbench();
        bare.setLevel(level);
        bare.getInventory().setItem(0, new ItemStack(ModItems.ESSENTIA_CELL_1K.get()));
        bare.getUpgrades().setItemDirect(0, new ItemStack(AEItems.INVERTER_CARD.get()));
        StorageCell inventory = BasicCellInventory.createInventory(bare.getCell(), null);
        long taken = inventory == null ? -1L : inventory.insert(marked, 100L, Actionable.MODULATE, source());
        ThELog.LOG.info(
                "[cellpartition] an inverted cell with no marks took {} of {} (AE2 decides this, not us)",
                taken,
                marked);
    }

    /** The cell's own config grid, as AE2 reads it: a mark has to be in there or a drive never sees it. */
    private static @Nullable GenericStack cellPartition(BlockEntityEssentiaCellWorkbench bench, int well) {
        var onTheCell = ItemEssentiaCell.partitionOf(bench.getCell());
        return onTheCell == null ? null : onTheCell.getStack(well);
    }

    /** A position of its own: the checks never put the machine into a level, they only hand it one. */
    private static BlockEntityEssentiaCellWorkbench newWorkbench() {
        return new BlockEntityEssentiaCellWorkbench(
                BlockPos.ZERO, ModBlocks.ESSENTIA_CELL_WORKBENCH.get().defaultBlockState());
    }

    private static String describe(@Nullable GenericStack stack) {
        return stack == null ? "nothing" : stack.what() + " x" + stack.amount();
    }

    private static IActionSource source() {
        return IActionSource.empty();
    }

    private static void report(List<String> failures) {
        if (failures.isEmpty()) {
            ThELog.LOG.info("[cellpartition] self-test passed");
            return;
        }
        for (String failure : failures) {
            ThELog.LOG.error("[cellpartition] FAIL {}", failure);
        }
        ThELog.LOG.error("[cellpartition] self-test failed with {} problem(s)", failures.size());
    }
}
