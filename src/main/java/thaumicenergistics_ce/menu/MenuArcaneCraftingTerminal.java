package thaumicenergistics_ce.menu;

import appeng.api.storage.ITerminalHost;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftCost;
import com.leclowndu93150.thaumaturge.content.workbench.MenuArcaneWorkbench;
import appeng.menu.me.common.MEStorageMenu;
import appeng.helpers.ICraftingGridMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.SlotSemantics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import java.util.List;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.menu.slot.ArcaneCraftingResultSlot;
import thaumicenergistics_ce.menu.slot.CrystalSlot;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * The Arcane Crafting Terminal's menu: an ME storage terminal with an arcane workbench in it - nine crafting
 * cells, six crystal slots three down each side of the grid, a result and a wand slot.
 *
 * <p>Modelled on AE2's {@code CraftingTermMenu}, but the craft goes through Thaumaturge's arcane crafting
 * transaction: an arcane recipe is not a vanilla recipe and cannot be matched by a {@code CraftingRecipe}.
 *
 * <p>The crystal slots are what make a crystal-cost recipe craftable here - a crystal in a grid cell counts
 * as a grid ingredient too, and pushes the cell count past what the pattern allows. See
 * {@code PartArcaneCraftingTerminal.INV_CRYSTALS}.
 */
public class MenuArcaneCraftingTerminal extends MEStorageMenu
        implements ICraftingGridMenu, appeng.util.inv.InternalInventoryHost {

    /** The nine workbench cells. */
    public static final int GRID_SIZE = PartArcaneCraftingTerminal.GRID_SIZE;

    /**
     * Deliberately no index constants: slot indices are not a property of this menu - AE2's base class adds
     * five upgrade slots first, so the grid starts at 5, and constants written in screen order were wrong
     * twice. A wrong index does not throw, it silently names a different slot.
     *
     * <p>Ask for a semantic instead; {@link #resultSlot()} answers the one question that needed an index.
     */

    /**
     * The two crystal columns, as semantics of their own: three slots run down each side of the grid, and AE2
     * gives a semantic a single anchor, so one group cannot reach both circles.
     */
    public static final appeng.menu.SlotSemantic CRYSTALS_LEFT =
            SlotSemantics.register("THAUMICENERGISTICS_CRYSTALS_LEFT", true);

    public static final appeng.menu.SlotSemantic CRYSTALS_RIGHT =
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

    /**
     * Measured off the screen texture: the cells' centres are at x=35, 53 and 71, so the circles' are at 17
     * and 89, and a slot 18 wide has its left edge nine left of its centre.
     *
     * <p>These were 7 and 78, one and two pixels off. check_arcane_terminal_alignment.js measures the columns
     * against the art.
     */
    private static final int CRYSTALS_LEFT_X = 8;
    private static final int CRYSTALS_RIGHT_X = 80;

    private final PartArcaneCraftingTerminal part;

    /**
     * The menu's own inventory rather than the part's - a result is what the grid would produce right now, and
     * keeping it here stops it being saved and reappearing on a fresh grid. An {@code AppEngInternalInventory}
     * because AE2's result slot is typed to {@code InternalInventory}.
     */
    private final appeng.util.inv.AppEngInternalInventory resultInventory =
            new appeng.util.inv.AppEngInternalInventory(this, 1);

    private @Nullable ArcaneCraftingResultSlot resultSlot;

    /**
     * The craft inputs as they stood when the result was last worked out.
     *
     * <p>Nothing tells this menu when the grid changes: it and the crystal slots are
     * {@code AppEngInternalInventory} owned by the part, so neither {@link #slotsChanged} nor
     * {@link #onChangeInventory} fires. The menu polls instead - cheap once a tick, and it cannot miss an
     * update or recurse.
     */
    private int craftInputSignature = -1;

    /**
     * One constructor: AE2's menu type builder sends the host with the open packet, so both sides arrive here
     * with one and there is no separate client path.
     *
     * <p>The super call passes {@code createPlayerSlots = false} so our own slots come before the player's, and
     * {@code createPlayerInventorySlots} runs once at the end; the four-argument super would have added them
     * already, and that method asserts otherwise. Calling both throws out of this constructor while a
     * right-click is handled, which AE2 logs and suppresses - the terminal simply will not open.
     * {@code MenuSelfTest} constructs every menu once so this surfaces at startup.
     */
    public MenuArcaneCraftingTerminal(
            MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host, false);
        this.part = host instanceof PartArcaneCraftingTerminal terminal ? terminal : null;

        if (part == null) {
            // Not our part: there is no grid, so the menu is built empty rather than half-built around
            // slots that do not exist. Called once, and only here - see the constructor javadoc.
            createPlayerInventorySlots(playerInventory);
            return;
        }

        // 1. The workbench cells.
        for (int i = 0; i < GRID_SIZE; i++) {
            int column = i % GRID_COLS;
            int row = i / GRID_COLS;
            Slot cell = addSlot(new AppEngSlot(part.craftingGrid(), i), SlotSemantics.CRAFTING_GRID);
            // AE2 replaces these from the screen style once the screen runs; set anyway so a slot read
            // without a screen is not stacked at 0,0.
            cell.x = GRID_X + column * GRID_PITCH;
            cell.y = GRID_Y + row * GRID_PITCH;
        }

        // 2. The wand slot.
        //
        // STORAGE because AE2 has no semantic for "a tool this machine uses"; it is what the screen style
        // can position and what shift-clicking moves items through.
        Slot wandSlot = addSlot(
                new AppEngSlot(part.wandInventory(), PartArcaneCraftingTerminal.WAND_SLOT),
                SlotSemantics.STORAGE);
        wandSlot.x = WAND_X;
        wandSlot.y = WAND_Y;

        // 3. The six crystal slots, three down each side of the grid.
        //
        //    What makes a crystal-cost recipe craftable at all: a crystal in the grid counts twice, as an
        //    ingredient and as payment, which breaks ArcaneShapedRecipePattern.matches. Positions here are
        //    placeholders - the screen style lays them out from the json, each column VERTICAL.
        //
        //    Each slot is pinned to one primal aspect, in Thaumaturge's own order, because that is what the
        //    arcane workbench these six slots stand in for does. See CrystalSlot.
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

        // 5. The player's own slots, last, as AE2's WirelessCraftingTermMenu does it - once, which is why
        //    the super call passes createPlayerSlots = false.
        createPlayerInventorySlots(playerInventory);

        // Fill the result once, so a grid left from last time shows on open.
        resultSlot.refresh();
    }

    // ------------------------------------------------------------------
    // ICraftingGridMenu - what AE2 needs to treat this as a crafting terminal
    // ------------------------------------------------------------------

    @Override
    public appeng.api.networking.IGridNode getGridNode() {
        return part == null ? null : part.getMainNode().getNode();
    }

    @Override
    public appeng.api.inventories.InternalInventory getCraftingMatrix() {
        return part == null ? null : part.craftingGrid();
    }

    /**
     * AE2's version re-runs its own vanilla recipe lookup, which never matches an arcane recipe. Kept for
     * containers that really are vanilla ones; it does not fire for AE2's inventories, see
     * {@link #craftInputSignature}.
     */
    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        super.slotsChanged(container);
        if (resultSlot != null) {
            resultSlot.refresh();
        }
    }

    /**
     * The poll that actually makes the result appear: the grid and crystal slots belong to the part, so
     * neither {@code slotsChanged} nor {@code onChangeInventory} is ever reached for them. The signature
     * covers the grid, the crystals and the wand - everything the price and the match depend on - and
     * comparing it matters, because a refresh is a full recipe scan and this runs every tick.
     */
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

    /**
     * The nine cells, six crystals and wand as a key that changes when any of them does. An int because this
     * runs every tick - the string version serialised each stack's components to SNBT. See {@link StackSignatures}.
     */
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

    /**
     * The result slot, or {@code null} when the host was not our part. An accessor because the index is not
     * ours to know - see the note where the index constants used to be.
     */
    public @Nullable ArcaneCraftingResultSlot resultSlot() {
        return resultSlot;
    }

    /**
     * The primal aspect a crystal slot holds, by container index - left column first, then right.
     *
     * <p>Thaumaturge's own order, read from the workbench rather than copied into a second list: if their
     * six ever reorder, these reorder with them and the two machines keep agreeing about which slot is Aer.
     */
    public static ResourceKey<IAspect> aspectOf(int crystalIndex) {
        return MenuArcaneWorkbench.PRIMAL_ORDER.get(crystalIndex);
    }

    /** The six crystal slots, left column first. Empty when the host was not our part. */
    public List<Slot> crystalSlots() {
        List<Slot> slots = new java.util.ArrayList<>(PartArcaneCraftingTerminal.CRYSTAL_SLOTS);
        slots.addAll(getSlots(CRYSTALS_LEFT));
        slots.addAll(getSlots(CRYSTALS_RIGHT));
        return slots;
    }

    /** The wand slot, or {@code null} when the host was not our part. */
    public @Nullable Slot wandSlot() {
        List<Slot> slots = getSlots(SlotSemantics.STORAGE);
        return slots.isEmpty() ? null : slots.getFirst();
    }

    /**
     * Server to client, and the only thing this menu sends that way: the cost depends on the matched recipe
     * and the player's wand discounts, so only the server can work it out. Only the vis is sent; the crystals
     * and the rest of the payment are AE2's business.
     *
     * @param cost what a craft would charge, or {@code null} for a grid that matches nothing
     */
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

    /**
     * Deliberately not persisted: the result is derivable from the grid at any moment, and a saved one would
     * survive a restart and be taken for an empty grid.
     */
    @Override
    public void saveChangedInventory(appeng.util.inv.AppEngInternalInventory inventory) {
        // Nothing to save: see above.
    }

    @Override
    public boolean isClientSide() {
        return super.isClientSide();
    }
}
