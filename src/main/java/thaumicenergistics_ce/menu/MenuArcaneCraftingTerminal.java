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
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.menu.slot.ArcaneCraftingResultSlot;
import thaumicenergistics_ce.menu.slot.CrystalSlot;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;

/**
 * The Arcane Crafting Terminal's menu: an ME storage terminal holding an arcane workbench.
 * <ul>
 *   <li>Nine crafting cells, six crystal slots three down each side of the grid, a result and a wand slot.
 *   <li>Modelled on AE2's {@code CraftingTermMenu}, but crafting runs through Thaumaturge's arcane
 *       transaction, as a {@code CraftingRecipe} never matches an arcane recipe.
 *   <li>A crystal in a grid cell counts as an ingredient and as payment, pushing the cell count past what
 *       the pattern allows, so crystals get their own slots. See {@code PartArcaneCraftingTerminal.INV_CRYSTALS}.
 * </ul>
 */
public class MenuArcaneCraftingTerminal extends MEStorageMenu
        implements ICraftingGridMenu, InternalInventoryHost {

    /** The nine workbench cells. */
    public static final int GRID_SIZE = PartArcaneCraftingTerminal.GRID_SIZE;

    /**
     * No index constants deliberately: AE2 adds five upgrade slots first, so a wrong index names another
     * slot instead of throwing. Ask for a semantic instead.
     */

    /** The two crystal columns. An AE2 semantic has one anchor, so both circles need their own. */
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

    /** Measured off the screen texture: cell centres at x=35, 53 and 71 give circle centres of 17 and 89,
     * and a slot 18 wide starts nine pixels left of its centre. */
    private static final int CRYSTALS_LEFT_X = 8;
    private static final int CRYSTALS_RIGHT_X = 80;

    private final PartArcaneCraftingTerminal part;

    /**
     * The menu's own inventory, not the part's: the result is derived from the grid and must not be saved.
     */
    private final AppEngInternalInventory resultInventory =
            new AppEngInternalInventory(this, 1);

    private @Nullable ArcaneCraftingResultSlot resultSlot;

    /** The craft inputs as they stood when the result was last worked out. Nothing tells this menu when
     * the grid changes, so it polls instead - cheap once a tick, and it cannot miss an update. */
    private int craftInputSignature = -1;

    /** The super call passes {@code createPlayerSlots = false} so our slots come first, and
     * {@code createPlayerInventorySlots} runs once at the end; calling both throws out of this
     * constructor, which AE2 logs and suppresses - the terminal simply will not open. */
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

        // 3. The six crystal slots, three down each side of the grid.
        //    A crystal in the grid counts twice, as ingredient and as payment.
        //    Each slot is pinned to one primal aspect in Thaumaturge's own order. See CrystalSlot.
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

    // ------------------------------------------------------------------
    // ICraftingGridMenu - what AE2 needs to treat this as a crafting terminal
    // ------------------------------------------------------------------

    @Override
    public IGridNode getGridNode() {
        return part == null ? null : part.getMainNode().getNode();
    }

    @Override
    public InternalInventory getCraftingMatrix() {
        return part == null ? null : part.craftingGrid();
    }

    /** AE2's version re-runs its own vanilla recipe lookup, which never matches an arcane recipe; it does
     * not fire for AE2's inventories, see {@link #craftInputSignature}. */
    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (resultSlot != null) {
            resultSlot.refresh();
        }
    }

    /** Signature compare needed, as a refresh is a full recipe scan and this runs every tick. The grid and
     * crystal slots belong to the part, so neither {@code slotsChanged} nor {@code onChangeInventory} fires. */
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

    /** The nine cells, six crystals and wand as a key that changes when any of them does. An int, because
     * this runs every tick. See {@link StackSignatures}. */
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

    /** The part behind this menu, or {@code null} when the host was not ours. */
    public PartArcaneCraftingTerminal part() {
        return part;
    }

    /** The result slot, or {@code null} when the host was not our part. Accessor, because the index is not ours. */
    public @Nullable ArcaneCraftingResultSlot resultSlot() {
        return resultSlot;
    }

    /** The primal aspect a crystal slot holds, by container index - left column first, then right. Read from
     * Thaumaturge's own order rather than copied, so a reorder there carries over. */
    public static ResourceKey<IAspect> aspectOf(int crystalIndex) {
        return TcWorkbench.primalAt(crystalIndex);
    }

    /** The six crystal slots, left column first. Empty when the host was not our part. */
    public List<Slot> crystalSlots() {
        List<Slot> slots = new ArrayList<>(PartArcaneCraftingTerminal.CRYSTAL_SLOTS);
        slots.addAll(getSlots(CRYSTALS_LEFT));
        slots.addAll(getSlots(CRYSTALS_RIGHT));
        return slots;
    }

    /** The wand slot, or {@code null} when the host was not our part. */
    public @Nullable Slot wandSlot() {
        List<Slot> slots = getSlots(SlotSemantics.STORAGE);
        return slots.isEmpty() ? null : slots.getFirst();
    }

    /** The cost depends on the matched recipe and the wand discounts, so only the server can work it out.
     * Only the vis is sent; the rest is AE2's business.
     * @param cost what a craft would charge, or {@code null} for a grid that matches nothing */
    public void sendCraftCost(@Nullable ArcaneCraftCost cost) {
        if (isClientSide()) {
            return;
        }
        sendPacketToClient(cost == null
                ? thaumicenergistics_ce.network.ArcaneCraftCostPayload.none(containerId)
                : thaumicenergistics_ce.network.ArcaneCraftCostPayload.of(containerId, cost.wandCentivis()));
    }

    // ------------------------------------------------------------------
    // InternalInventoryHost - the result inventory needs an owner
    // ------------------------------------------------------------------

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
