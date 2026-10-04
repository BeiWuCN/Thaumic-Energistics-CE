package thaumicenergistics_ce.menu;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.storage.ITerminalHost;
import appeng.helpers.ICraftingGridMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftCost;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;
import thaumicenergistics_ce.menu.slot.ArcaneCraftingResultSlot;
import thaumicenergistics_ce.menu.slot.CrystalSlot;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * The Arcane Crafting Terminal's menu: an ME storage terminal holding an arcane workbench.
 * <ul>
 *   <li>Nine crafting cells, six crystal slots three down each side, a result and a wand slot.
 *   <li>Modelled on AE2's {@code CraftingTermMenu}; a {@code CraftingRecipe} never matches an arcane one.
 *   <li>A crystal in a grid cell counts twice, so crystals get their own slots, past the pattern's count.
 * </ul>
 */
public class MenuArcaneCraftingTerminal extends MEStorageMenu
        implements ICraftingGridMenu, InternalInventoryHost {

    public static final int GRID_SIZE = PartArcaneCraftingTerminal.GRID_SIZE;


    public static final SlotSemantic CRYSTALS_LEFT =
            SlotSemantics.register("THAUMICENERGISTICS_CRYSTALS_LEFT", true);

    public static final SlotSemantic CRYSTALS_RIGHT =
            SlotSemantics.register("THAUMICENERGISTICS_CRYSTALS_RIGHT", true);

    // From the screen style, which draws the grid bottom-anchored at 26,158 with a 3-column break.
    private static final int GRID_X = 26;
    private static final int GRID_Y = 96;
    private static final int GRID_PITCH = 18;
    private static final int GRID_COLS = 3;
    private static final int RESULT_X = 134;
    private static final int RESULT_Y = 96;
    private static final int WAND_X = 116;
    private static final int WAND_Y = 114;

    private static final int CRYSTALS_LEFT_X = 8;
    private static final int CRYSTALS_RIGHT_X = 80;

    private final PartArcaneCraftingTerminal part;

    private final AppEngInternalInventory resultInventory =
            new AppEngInternalInventory(this, 1);

    private @Nullable ArcaneCraftingResultSlot resultSlot;

    private int craftInputSignature = -1;

    public MenuArcaneCraftingTerminal(
            MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host, false);
        this.part = host instanceof PartArcaneCraftingTerminal terminal ? terminal : null;

        if (part == null) {
            // Not our part: no grid, so build the menu empty and return.
            createPlayerInventorySlots(playerInventory);
            return;
        }

        // 1. The workbench cells.
        for (int i = 0; i < GRID_SIZE; i++) {
            int column = i % GRID_COLS;
            int row = i / GRID_COLS;
            Slot cell = addSlot(new AppEngSlot(part.craftingGrid(), i), SlotSemantics.CRAFTING_GRID);
            // AE2 replaces these from the screen style; set anyway for a slot read before then.
            cell.x = GRID_X + column * GRID_PITCH;
            cell.y = GRID_Y + row * GRID_PITCH;
        }

        // 2. The wand slot. STORAGE: AE2 has no semantic for a tool; this one positions and shift-clicks.
        Slot wandSlot = addSlot(
                new AppEngSlot(part.wandInventory(), PartArcaneCraftingTerminal.WAND_SLOT),
                SlotSemantics.STORAGE);
        wandSlot.x = WAND_X;
        wandSlot.y = WAND_Y;

        // 3. The six crystal slots, three down each side of the grid. Each is pinned to one primal aspect
        // in Thaumaturge's own order, and a crystal in the grid counts twice. See CrystalSlot.
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_COLUMN; i++) {
            Slot slot = addSlot(new CrystalSlot(part.crystalInventory(), i, aspectOf(i)), CRYSTALS_LEFT);
            slot.x = CRYSTALS_LEFT_X;
            slot.y = GRID_Y + i * GRID_PITCH;
        }
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_COLUMN; i++) {
            int index = PartArcaneCraftingTerminal.CRYSTAL_COLUMN + i;
            Slot slot = addSlot(new CrystalSlot(part.crystalInventory(), index, aspectOf(index)), CRYSTALS_RIGHT);
            slot.x = CRYSTALS_RIGHT_X;
            slot.y = GRID_Y + i * GRID_PITCH;
        }

        // 4. The result slot, after the storage, energy and grid it reads.
        this.resultSlot = new ArcaneCraftingResultSlot(
                playerInventory.player,
                getActionSource(),
                energySource,
                storage,
                part.craftingGrid(),
                resultInventory,
                this,
                part);
        addSlot(resultSlot, SlotSemantics.CRAFTING_RESULT);
        resultSlot.x = RESULT_X;
        resultSlot.y = RESULT_Y;

        // 5. The player's own slots, last, once - the super call passes createPlayerSlots = false.
        createPlayerInventorySlots(playerInventory);

        // Fill the result once, so a grid left from last time shows on open.
        resultSlot.refresh();
    }


    @Override
    public IGridNode getGridNode() {
        return part == null ? null : part.getMainNode().getNode();
    }

    @Override
    public InternalInventory getCraftingMatrix() {
        return part == null ? null : part.craftingGrid();
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (resultSlot != null) {
            resultSlot.refresh();
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (resultSlot == null || part == null) {
            return;
        }
        int signature = craftInputSignature();
        if (signature != craftInputSignature) {
            craftInputSignature = signature;
            resultSlot.refresh();
        }
    }

    private int craftInputSignature() {
        int hash = 1;
        for (int i = 0; i < PartArcaneCraftingTerminal.GRID_SIZE; i++) {
            hash = 31 * hash + StackSignatures.of(part.craftingGrid().getStackInSlot(i));
        }
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; i++) {
            hash = 31 * hash + StackSignatures.of(part.crystalInventory().getStackInSlot(i));
        }
        return 31 * hash + StackSignatures.of(part.wandInventory().getStackInSlot(PartArcaneCraftingTerminal.WAND_SLOT));
    }

    public PartArcaneCraftingTerminal part() {
        return part;
    }

    public @Nullable ArcaneCraftingResultSlot resultSlot() {
        return resultSlot;
    }

    public static ResourceKey<IAspect> aspectOf(int crystalIndex) {
        return TcWorkbench.primalAt(crystalIndex);
    }

    public List<Slot> crystalSlots() {
        List<Slot> slots = new ArrayList<>(PartArcaneCraftingTerminal.CRYSTAL_SLOTS);
        slots.addAll(getSlots(CRYSTALS_LEFT));
        slots.addAll(getSlots(CRYSTALS_RIGHT));
        return slots;
    }

    public @Nullable Slot wandSlot() {
        List<Slot> slots = getSlots(SlotSemantics.STORAGE);
        return slots.isEmpty() ? null : slots.getFirst();
    }

    public void sendCraftCost(@Nullable ArcaneCraftCost cost) {
        if (isClientSide()) {
            return;
        }
        sendPacketToClient(cost == null
                ? thaumicenergistics_ce.network.ArcaneCraftCostPayload.none(containerId)
                : thaumicenergistics_ce.network.ArcaneCraftCostPayload.of(containerId, cost.wandCentivis()));
    }


    /** Not persisted: the result is derived from the grid, and a saved one would outlive it. */
    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
        // Nothing to save: the result is derived from the grid.
    }

    @Override
    public boolean isClientSide() {
        return super.isClientSide();
    }
}
