package thaumicenergistics_ce.menu;

import appeng.core.definitions.AEItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.layout.GuiLayout;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.inventory.GearSlots;
import thaumicenergistics_ce.menu.slot.PreviewSlot;
import thaumicenergistics_ce.menu.slot.ReadOnlySlot;

/**
 * The Arcane Assembler's menu: pattern mirror, knowledge core, acceleration cards, craft preview and the
 * gear slots whose vis discount applies here.
 * <ul>
 *   <li>Coordinates come from a {@link GuiLayout}, generated from the same constants as the background
 *       texture, so this class holds no client-only resource access and no geometry of its own.
 * </ul>
 */
public class MenuArcaneAssembler extends AbstractContainerMenu {

    private static final int PLAYER_SLOTS = 36;

    // Slot indices in registration order: player, core, patterns, upgrades, gear.
    // No index for the result well: it is in the panel art, and a Slot would always highlight.
    static final int IDX_CORE = PLAYER_SLOTS;
    private static final int IDX_PATTERN_START = IDX_CORE + 1;
    private static final int IDX_PATTERN_END = IDX_PATTERN_START + BlockEntityArcaneAssembler.PATTERN_SLOT_COUNT;
    private static final int IDX_UPGRADE_START = IDX_PATTERN_END;
    private static final int IDX_UPGRADE_END =
            IDX_UPGRADE_START + BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT;
    private static final int IDX_GEAR_START = IDX_UPGRADE_END;
    private static final int IDX_GEAR_END = IDX_GEAR_START + BlockEntityArcaneAssembler.GEAR_SLOT_COUNT;

    /** The vis pool as one number: the total the six per-aspect slots below break down. */
    public static final int DATA_BUFFERED_VIS = 0;
    /** The six primals, one slot each, in {@code BAR_ASPECTS} order; a shared pool drew all bars equal. */
    public static final int DATA_ASPECT_AIR = 1;
    public static final int DATA_ASPECT_WATER = 2;
    public static final int DATA_ASPECT_FIRE = 3;
    public static final int DATA_ASPECT_ORDER = 4;
    public static final int DATA_ASPECT_ENTROPY = 5;
    public static final int DATA_ASPECT_EARTH = 6;
    public static final int DATA_CRAFTING = 7;
    public static final int DATA_CRAFT_TICK = 8;
    public static final int DATA_TICKS_PER_CRAFT = 9;
    public static final int DATA_GEAR_DISCOUNT = 10;
    public static final int DATA_SIZE = 11;

    // Package-private for the two collaborators: they read the machine and the inventory directly.
    final @Nullable BlockEntityArcaneAssembler assembler;

    final Inventory playerInventory;

    private final AssemblerPreviewMirror mirror;

    private final AssemblerMenuReadout readout;

    public MenuArcaneAssembler(int containerId, Inventory playerInventory, BlockEntityArcaneAssembler assembler) {
        // Both sides read the layout from the mod's resources, so their slot positions cannot drift apart.
        this(containerId, playerInventory, assembler, assembler.getInventory());
        mirror.refresh();
    }

    public MenuArcaneAssembler(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, null, new SimpleContainer(BlockEntityArcaneAssembler.SLOT_COUNT));
        // The open packet's only reader: the menu type hands the buffer to this constructor.
        if (buf.readableBytes() >= Long.BYTES) {
            readout.setClientPos(buf.readBlockPos());
        }
    }

    /**
     * The geometry both sides place their slots from, from the file the art generator writes.
     */
    private static GuiLayout layout() {
        return GuiLayout.load();
    }

    private MenuArcaneAssembler(
            int containerId,
            Inventory playerInventory,
            @Nullable BlockEntityArcaneAssembler assembler,
            Container machine) {
        super(ModMenuTypes.ARCANE_ASSEMBLER.get(), containerId);
        this.assembler = assembler;
        this.playerInventory = playerInventory;
        this.mirror = new AssemblerPreviewMirror(this);
        this.readout = new AssemblerMenuReadout(this);

        GuiLayout layout = layout();
        GuiLayout.Anchor inventory = layout.playerInventory();
        GuiLayout.Anchor core = layout.coreSlot();
        GuiLayout.Grid patterns = layout.patternGrid();
        GuiLayout.Grid upgrades = layout.upgradeSlots();
        GuiLayout.Grid gear = layout.armorSlots();
        GuiLayout.Grid preview = layout.previewGrid();
        GuiLayout.Anchor result = layout.previewResult();

        // 1. Player inventory, three rows then the hotbar.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        inventory.x() + col * GuiLayout.PITCH, inventory.y() + row * GuiLayout.PITCH));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, inventory.x() + col * GuiLayout.PITCH,
                    layout.hotbar().y()));
        }

        // 2. Knowledge core.
        addSlot(new Slot(machine, BlockEntityArcaneAssembler.CORE_SLOT, core.x(), core.y()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.KNOWLEDGE_CORE.get());
            }

            @Override
            public void setChanged() {
                super.setChanged();
                MenuArcaneAssembler.this.mirror.refresh();
            }
        });

        // 3. Pattern mirror, display only, derived from the core - see AssemblerPreviewMirror.refresh.
        for (int i = 0; i < BlockEntityArcaneAssembler.PATTERN_SLOT_COUNT; i++) {
            addSlot(new ReadOnlySlot(
                    assembler == null ? mirror.display() : machine,
                    assembler == null ? i : BlockEntityArcaneAssembler.PATTERN_SLOT_START + i,
                    patterns.slotX(i),
                    patterns.slotY(i)));
        }

        // 4. Acceleration cards, in the machine's own slots. A menu-local container held them once, and
        // closing the menu took them with it; these are saved with the machine, so they come back.
        for (int i = 0; i < BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT; i++) {
            addSlot(new Slot(machine, BlockEntityArcaneAssembler.UPGRADE_SLOT_START + i, upgrades.x(),
                    upgrades.columnY(i)) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return AEItems.SPEED_CARD.is(stack);
                }

                @Override
                public int getMaxStackSize() {
                    // One card per slot: four slots, four cards, four steps of speed.
                    return 1;
                }
            });
        }

        // 5. Gear slots whose vis discount applies to this assembler.
        // mayPlace is what a player click goes through; the block's container only sees automation.
        for (int i = 0; i < BlockEntityArcaneAssembler.GEAR_SLOT_COUNT; i++) {
            int gearIndex = i;
            addSlot(new Slot(machine, BlockEntityArcaneAssembler.GEAR_SLOT_START + i, gear.x(),
                    gear.columnY(i)) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return GearSlots.accepts(gearIndex, stack);
                }
            });
        }

        // 6. The craft preview's product, while a craft is running. Added last, as quickMoveStack routes by
        // IDX_GEAR_START. A slot, as the block sends no update tag; +1, so the well does not highlight.
        Slot target = addSlot(new PreviewSlot(
                machine,
                BlockEntityArcaneAssembler.TARGET_SLOT,
                result.x() + 1,
                result.y() + 1));

        // 7. The running craft's 3x3, shown in the preview wells.
        // Added last like the result well; PreviewSlot, so the wells do not highlight under the cursor.
        Slot[] wells = new Slot[BlockEntityArcaneAssembler.PREVIEW_SLOT_COUNT];
        for (int i = 0; i < wells.length; i++) {
            // No +1 offset here, unlike the result well: the wells sit exactly on the grid cells.
            wells[i] = addSlot(new PreviewSlot(
                    machine,
                    BlockEntityArcaneAssembler.PREVIEW_SLOT_START + i,
                    preview.slotX(i),
                    preview.slotY(i)));
        }
        mirror.watch(target, wells);

        // The numbers the menu shows live in AssemblerMenuReadout, which owns the table they travel in.
        addDataSlots(readout.data());
    }

    public void refreshPatternView() {
        mirror.refresh();
    }

    public ItemStack getPreviewSlot(int index) {
        return mirror.getPreviewSlot(index);
    }

    public ItemStack getPreviewResult() {
        return mirror.getPreviewResult();
    }

    public Slot getUpgradeSlot(int index) {
        int slot = IDX_UPGRADE_START + index;
        if (index < 0 || slot >= IDX_UPGRADE_END || slot >= slots.size()) {
            throw new IndexOutOfBoundsException("upgrade slot " + index);
        }
        return slots.get(slot);
    }

    public int getBufferedVis() {
        return readout.getBufferedVis();
    }

    public int getBarVis(int column) {
        return readout.getBarVis(column);
    }

    public boolean isCrafting() {
        return readout.isCrafting();
    }

    public float getProgress() {
        return readout.getProgress();
    }

    public String progressTrace() {
        return readout.progressTrace();
    }

    public int getGearDiscount() {
        return readout.getGearDiscount();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < PLAYER_SLOTS) {
            boolean merged = false;

            if (stack.is(ModItems.KNOWLEDGE_CORE.get())) {
                merged = moveItemStackTo(stack, IDX_CORE, IDX_CORE + 1, false);
            }
            if (!merged && AEItems.SPEED_CARD.is(stack)) {
                merged = moveItemStackTo(stack, IDX_UPGRADE_START, IDX_UPGRADE_END, false);
            }
            if (!merged && !GearSlots.isGear(stack)) {
                // Fall through to the normal inventory shuffle.
            }
            if (!merged) {
                merged = moveItemStackTo(stack, IDX_GEAR_START, IDX_GEAR_END, false);
            }
            if (!merged) {
                if (index < 27) {
                    merged = moveItemStackTo(stack, 27, PLAYER_SLOTS, false);
                } else {
                    merged = moveItemStackTo(stack, 0, 27, false);
                }
            }
            if (!merged) {
                return ItemStack.EMPTY;
            }
        } else {
            // Machine slots back into the player inventory; the read-only ones refuse this already.
            if (!moveItemStackTo(stack, 0, PLAYER_SLOTS, true)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (assembler == null) {
            return true;
        }
        var level = assembler.getLevel();
        var pos = assembler.getBlockPos();
        return level != null
                && level.getBlockEntity(pos) == assembler
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
