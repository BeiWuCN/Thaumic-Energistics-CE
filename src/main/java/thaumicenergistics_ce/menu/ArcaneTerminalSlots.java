package thaumicenergistics_ce.menu;

import appeng.api.inventories.InternalInventory;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import java.util.function.BiFunction;
import net.minecraft.world.inventory.Slot;
import thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;
import thaumicenergistics_ce.menu.slot.CrystalSlot;
import thaumicenergistics_ce.menu.slot.WandSlot;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/** Creates the workbench slots without depending on menu lifecycle or card state. */
final class ArcaneTerminalSlots {
    private ArcaneTerminalSlots() {}

    private static final int GRID_X = 26;
    private static final int GRID_Y = 96;
    private static final int GRID_PITCH = 18;
    private static final int GRID_COLS = 3;
    private static final int WAND_X = 116;
    private static final int WAND_Y = 114;

    private static final int CRYSTALS_LEFT_X = 8;
    private static final int CRYSTALS_RIGHT_X = 80;

    static void add(BiFunction<Slot, SlotSemantic, Slot> add, InternalInventory craftingGrid,
            InternalInventory wandInventory, InternalInventory crystals) {
        for (int i = 0; i < PartArcaneCraftingTerminal.GRID_SIZE; i++) {
            int column = i % GRID_COLS;
            int row = i / GRID_COLS;
            Slot cell = add.apply(new AppEngSlot(craftingGrid, i), SlotSemantics.CRAFTING_GRID);
            cell.x = GRID_X + column * GRID_PITCH;
            cell.y = GRID_Y + row * GRID_PITCH;
        }

        Slot wandSlot = add.apply(
                new WandSlot(wandInventory, PartArcaneCraftingTerminal.WAND_SLOT),
                SlotSemantics.STORAGE);
        wandSlot.x = WAND_X;
        wandSlot.y = WAND_Y;

        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_COLUMN; i++) {
            Slot slot = add.apply(new CrystalSlot(crystals, i, TcWorkbench.primalAt(i)), MenuArcaneCraftingTerminal.CRYSTALS_LEFT);
            slot.x = CRYSTALS_LEFT_X;
            slot.y = GRID_Y + i * GRID_PITCH;
        }
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_COLUMN; i++) {
            int index = PartArcaneCraftingTerminal.CRYSTAL_COLUMN + i;
            Slot slot = add.apply(new CrystalSlot(crystals, index, TcWorkbench.primalAt(index)), MenuArcaneCraftingTerminal.CRYSTALS_RIGHT);
            slot.x = CRYSTALS_RIGHT_X;
            slot.y = GRID_Y + i * GRID_PITCH;
        }

    }

    static void positionResult(Slot slot) {
        slot.x = 134;
        slot.y = 96;
    }
}
