package thaumicenergistics_ce.menu;

import appeng.api.implementations.menuobjects.IPortableTerminal;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.storage.ITerminalHost;
import appeng.helpers.ICraftingGridMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
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
import thaumicenergistics_ce.arcane.ArcaneTerminalHost;
import thaumicenergistics_ce.arcane.TerminalAuraPayment;
import thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.menu.slot.ArcaneCraftingResultSlot;
import thaumicenergistics_ce.menu.slot.CrystalSlot;
import thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * The Arcane Crafting Terminal's menu, reached through the placed part or through a paired item.
 * <ul>
 *   <li>Nine crafting cells, a result slot, six side slots where a crystal counts twice, a wand slot.
 *   <li>Modelled on AE2's {@code CraftingTermMenu}; a {@code CraftingRecipe} never matches an arcane one.
 *   <li>Its essentia gestures come from {@link MenuEssentiaTerminalBase}, gated on the access card.
 * </ul>
 */
public class MenuArcaneCraftingTerminal extends MenuEssentiaTerminalBase
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

    private final @Nullable PartArcaneCraftingTerminal part;

    /**
     * Stand-ins for the three containers while no terminal is resolved: the slots always exist, since a
     * client that cannot see the bound chunk would otherwise build a different menu from the server's.
     */
    private final AppEngInternalInventory gridFallback = new AppEngInternalInventory(this, GRID_SIZE);

    private final AppEngInternalInventory wandFallback = new AppEngInternalInventory(this, 1);

    private final AppEngInternalInventory crystalFallback =
            new AppEngInternalInventory(this, PartArcaneCraftingTerminal.CRYSTAL_SLOTS);

    private final InternalInventory craftingGrid;

    private final InternalInventory wandInventory;

    private final InternalInventory crystals;

    /** Set for a wireless terminal alone: it buys the aura, and the aura is the one around its player. */
    private final @Nullable IEnergySource auraPayer;

    private final AppEngInternalInventory resultInventory =
            new AppEngInternalInventory(this, 1);

    private @Nullable ArcaneCraftingResultSlot resultSlot;

    private int craftInputSignature = -1;

    public MenuArcaneCraftingTerminal(
            MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host, false);
        this.part = host instanceof ArcaneTerminalHost arcane ? arcane.arcaneTerminal() : null;
        this.auraPayer = host instanceof IPortableTerminal portable ? portable : null;
        this.craftingGrid = part == null ? gridFallback : part.craftingGrid();
        this.wandInventory = part == null ? wandFallback : part.wandInventory();
        this.crystals = part == null ? crystalFallback : part.crystalInventory();

        // 1. The workbench cells.
        for (int i = 0; i < GRID_SIZE; i++) {
            int column = i % GRID_COLS;
            int row = i / GRID_COLS;
            Slot cell = addSlot(new AppEngSlot(craftingGrid, i), SlotSemantics.CRAFTING_GRID);
            // AE2 replaces these from the screen style; set anyway for a slot read before then.
            cell.x = GRID_X + column * GRID_PITCH;
            cell.y = GRID_Y + row * GRID_PITCH;
        }

        // 2. The wand slot. STORAGE: AE2 has no semantic for a tool; this one positions and shift-clicks.
        Slot wandSlot = addSlot(
                new AppEngSlot(wandInventory, PartArcaneCraftingTerminal.WAND_SLOT),
                SlotSemantics.STORAGE);
        wandSlot.x = WAND_X;
        wandSlot.y = WAND_Y;

        // 3. The six crystal slots, three down each side of the grid. Each is pinned to one primal aspect
        // in Thaumaturge's own order, and a crystal in the grid counts twice. See CrystalSlot.
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_COLUMN; i++) {
            Slot slot = addSlot(new CrystalSlot(crystals, i, aspectOf(i)), CRYSTALS_LEFT);
            slot.x = CRYSTALS_LEFT_X;
            slot.y = GRID_Y + i * GRID_PITCH;
        }
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_COLUMN; i++) {
            int index = PartArcaneCraftingTerminal.CRYSTAL_COLUMN + i;
            Slot slot = addSlot(new CrystalSlot(crystals, index, aspectOf(index)), CRYSTALS_RIGHT);
            slot.x = CRYSTALS_RIGHT_X;
            slot.y = GRID_Y + i * GRID_PITCH;
        }

        // 4. The result slot, after the storage, energy and grid it reads.
        this.resultSlot = new ArcaneCraftingResultSlot(
                playerInventory.player,
                getActionSource(),
                energySource,
                storage,
                craftingGrid,
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
        return craftingGrid;
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
        if (resultSlot == null) {
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
            hash = 31 * hash + StackSignatures.of(craftingGrid.getStackInSlot(i));
        }
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; i++) {
            hash = 31 * hash + StackSignatures.of(crystals.getStackInSlot(i));
        }
        return 31 * hash + StackSignatures.of(wandInventory.getStackInSlot(PartArcaneCraftingTerminal.WAND_SLOT));
    }

    public @Nullable PartArcaneCraftingTerminal part() {
        return part;
    }

    public @Nullable ArcaneCraftingResultSlot resultSlot() {
        return resultSlot;
    }

    /** The wireless terminal's battery, or {@code null} for a placed one, whose vis the network buys. */
    public @Nullable IEnergySource auraPayer() {
        return auraPayer;
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
                ? ArcaneCraftCostPayload.none(containerId)
                : ArcaneCraftCostPayload.of(containerId, cost.wandCentivis()));
    }


    /** The card in the terminal item's own upgrade slot is the whole permission; nothing else is read. */
    @Override
    protected boolean essentiaAccessGranted() {
        // AE2's menu is built from the host's upgrade inventory, so this is the very slot the player sees.
        return getHost().getUpgrades().isInstalled(ModItems.ESSENTIA_ACCESS_CARD.get());
    }

    /** The screen asks this only to hide the gesture; the server asks again before it moves anything. */
    public boolean hasEssentiaAccessCard() {
        return essentiaAccessGranted();
    }

    /** Whether the vis card rides in the terminal the player opened, asked of that very stack, so that a
     * second terminal in the bag cannot answer for the one being used. */
    public boolean hasVisConnectionCard() {
        return getHost() instanceof ItemMenuHost<?> itemHost
                && TerminalAuraPayment.visConnectionInstalled(itemHost.getItemStack());
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
