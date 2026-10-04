package thaumicenergistics_ce.menu;

import appeng.core.definitions.AEItems;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TCAspects;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.gui.GuiLayout;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.inventory.GearSlots;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;
import thaumicenergistics_ce.menu.slot.PreviewSlot;
import thaumicenergistics_ce.menu.slot.ReadOnlySlot;

/**
 * The Arcane Assembler's menu: pattern mirror, knowledge core, acceleration cards, craft preview and the
 * gear slots whose vis discount applies here.
 * <ul>
 *   <li>Coordinates come from a {@link GuiLayout}, generated from the same constants as the background
 *       texture, so this class holds no client-only resource access.
 * </ul>
 */
public class MenuArcaneAssembler extends AbstractContainerMenu {

    /** Fallback geometry when the layout is unreadable: slots misplaced, menu still functional. */
    private static final int FB_PATTERN_X = 26;
    private static final int FB_PATTERN_Y = 15;
    private static final int FB_CORE_X = 175;
    private static final int FB_CORE_Y = 6;
    private static final int FB_UPGRADE_X = 175;
    private static final int FB_UPGRADE_Y = 24;
    private static final int FB_ARMOR_X = 152;
    private static final int FB_ARMOR_Y = 73;
    private static final int FB_INV_X = 8;
    private static final int FB_INV_Y = 147;
    private static final int FB_HOTBAR_Y = 205;
    private static final int FB_RESULT_X = 115;
    private static final int FB_RESULT_Y = 98;
    private static final int FB_PREVIEW_X = 26;
    private static final int FB_PREVIEW_Y = 81;

    /** Player inventory slots, which come first in this menu. */
    private static final int PLAYER_SLOTS = 36;

    // Slot indices in registration order: player, core, patterns, upgrades, gear.
    // No index for the result well: it is in the panel art, and a Slot would always highlight.
    private static final int IDX_CORE = PLAYER_SLOTS;
    private static final int IDX_PATTERN_START = IDX_CORE + 1;
    private static final int IDX_PATTERN_END = IDX_PATTERN_START + BlockEntityArcaneAssembler.PATTERN_SLOT_COUNT;
    private static final int IDX_UPGRADE_START = IDX_PATTERN_END;
    private static final int IDX_UPGRADE_END =
            IDX_UPGRADE_START + BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT;
    private static final int IDX_GEAR_START = IDX_UPGRADE_END;
    private static final int IDX_GEAR_END = IDX_GEAR_START + BlockEntityArcaneAssembler.GEAR_SLOT_COUNT;

    /** The vis pool as one number: the total the six per-aspect slots below break down. */
    public static final int DATA_BUFFERED_VIS = 0;
    /** The six primals, one slot each, in {@link #BAR_ASPECTS} order; a shared pool drew all bars equal. */
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

    private final @Nullable BlockEntityArcaneAssembler assembler;

    /** Its {@code player} is the only level the client menu has: there is no block entity there. */
    private final Inventory playerInventory;
    /**
     * The pattern wells' container on the client: the client derives them from the core slot, and the
     * machine's container has no update tag to sync.
     */
    private final SimpleContainer patternDisplay =
            new SimpleContainer(BlockEntityArcaneAssembler.PATTERN_SLOT_COUNT);

    /** Signature of the core the wells were last filled from, so they are only refilled when it changes. */
    private int mirroredCore = -1;
    /**
     * The core signature as of {@link #coreSignatureTick}: hashing walks the whole pattern store and the
     * screen asks once a frame, so it is cached per tick - a tick of staleness is invisible.
     */
    private int sampledCore = -1;
    private long coreSignatureTick = Long.MIN_VALUE;
    /** Stand-in for the acceleration cards, mirrored into the block entity on change. */
    private final SimpleContainer upgrades =
            new SimpleContainer(BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT) {
        @Override
        public void setChanged() {
            super.setChanged();
            MenuArcaneAssembler.this.onUpgradesChanged();
        }
    };
    private final int[] clientData = new int[DATA_SIZE];
    private final ContainerData data;

    /**
     * Where the machine is, sent in the open packet: the client menu's only handle on it, since its
     * {@link #assembler} is null, so no position means no craft progress.
     */
    private @Nullable BlockPos clientPos;

    /** The client's own view of the machine, resolved from {@link #clientPos} and cached. */
    private @Nullable BlockEntityArcaneAssembler clientMachine;

    /**
     * The mirrored slot the craft's product is shown in. Held as the slot, not an index, so reordering
     * slots cannot make it name the wrong one.
     */
    private @Nullable Slot targetSlot;

    /** The nine preview wells, held as slots for the same reason as {@link #targetSlot}. */
    private final Slot[] previewSlots = new Slot[BlockEntityArcaneAssembler.PREVIEW_SLOT_COUNT];

    /** Server-side constructor, opened from the block. */
    public MenuArcaneAssembler(int containerId, Inventory playerInventory, BlockEntityArcaneAssembler assembler) {
        // Both sides read the layout from the mod's resources, so their slot positions cannot drift apart.
        this(containerId, playerInventory, assembler, assembler.getInventory(), GuiLayout.load());
        refreshPatternView();
        onUpgradesChanged();
    }

    /** Client-side constructor, opened from the network; loads the layout itself. */
    public MenuArcaneAssembler(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(
                containerId,
                playerInventory,
                null,
                new SimpleContainer(BlockEntityArcaneAssembler.SLOT_COUNT),
                GuiLayout.load());
        // The open packet's only reader: the menu type hands the buffer to this constructor.
        if (buf.readableBytes() >= Long.BYTES) {
            this.clientPos = buf.readBlockPos();
        }
    }

    private MenuArcaneAssembler(
            int containerId,
            Inventory playerInventory,
            @Nullable BlockEntityArcaneAssembler assembler,
            Container machine,
            @Nullable GuiLayout layout) {
        super(ModMenuTypes.ARCANE_ASSEMBLER.get(), containerId);
        this.assembler = assembler;
        this.playerInventory = playerInventory;

        int patternX = layout != null ? layout.patternGrid().x() : FB_PATTERN_X;
        int patternY = layout != null ? layout.patternGrid().y() : FB_PATTERN_Y;
        int patternCols = layout != null ? layout.patternGrid().cols() : 7;
        int coreX = layout != null ? layout.coreSlot().x() : FB_CORE_X;
        int coreY = layout != null ? layout.coreSlot().y() : FB_CORE_Y;
        int upgradeX = layout != null ? layout.upgradeSlots().x() : FB_UPGRADE_X;
        int upgradeY = layout != null ? layout.upgradeSlots().y() : FB_UPGRADE_Y;
        int gearX = layout != null ? layout.armorSlots().x() : FB_ARMOR_X;
        int gearY = layout != null ? layout.armorSlots().y() : FB_ARMOR_Y;
        int previewX = layout != null ? layout.previewGrid().x() : FB_PREVIEW_X;
        int previewY = layout != null ? layout.previewGrid().y() : FB_PREVIEW_Y;
        int invX = layout != null ? layout.playerInventory().x() : FB_INV_X;
        int invY = layout != null ? layout.playerInventory().y() : FB_INV_Y;
        int hotbarY = layout != null ? layout.hotbar().y() : FB_HOTBAR_Y;

        // 1. Player inventory, three rows then the hotbar.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, invX + col * 18, invY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, invX + col * 18, hotbarY));
        }

        // 2. Knowledge core.
        addSlot(new Slot(machine, BlockEntityArcaneAssembler.CORE_SLOT, coreX, coreY) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.KNOWLEDGE_CORE.get());
            }

            @Override
            public void setChanged() {
                super.setChanged();
                MenuArcaneAssembler.this.refreshPatternView();
            }
        });

        // 3. Pattern mirror, display only, derived from the core - see refreshPatternView.
        for (int i = 0; i < BlockEntityArcaneAssembler.PATTERN_SLOT_COUNT; i++) {
            addSlot(new ReadOnlySlot(
                    assembler == null ? patternDisplay : machine,
                    assembler == null ? i : BlockEntityArcaneAssembler.PATTERN_SLOT_START + i,
                    patternX + (i % patternCols) * 18,
                    patternY + (i / patternCols) * 18));
        }

        // 4. Acceleration cards.
        for (int i = 0; i < BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT; i++) {
            addSlot(new Slot(upgrades, i, upgradeX, upgradeY + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return AEItems.SPEED_CARD.is(stack);
                }
            });
        }

        // 5. Gear slots whose vis discount applies to this assembler.
        // mayPlace is what a player click goes through; the block's container only sees automation.
        for (int i = 0; i < BlockEntityArcaneAssembler.GEAR_SLOT_COUNT; i++) {
            int gearIndex = i;
            addSlot(new Slot(machine, BlockEntityArcaneAssembler.GEAR_SLOT_START + i, gearX, gearY + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return GearSlots.accepts(gearIndex, stack);
                }
            });
        }

        // 6. The craft preview's product, while a craft is running.
        // Mirrored through a slot: the block sends no item update tag, so this is the product's only
        // path to the client, and both sides use `machine` - the product is not derivable there.
        // Added last, as quickMoveStack routes by IDX_GEAR_START. PreviewSlot, at +1 (the well's interior
        // runs 113..134 by 96..117), so the well does not highlight under the cursor.
        this.targetSlot = addSlot(new PreviewSlot(
                machine,
                BlockEntityArcaneAssembler.TARGET_SLOT,
                (layout != null ? layout.previewResult().x() : FB_RESULT_X) + 1,
                (layout != null ? layout.previewResult().y() : FB_RESULT_Y) + 1));

        // 7. The running craft's 3x3, shown in the preview wells.
        // Added last like the result well; PreviewSlot, so the wells do not highlight under the cursor.
        for (int i = 0; i < BlockEntityArcaneAssembler.PREVIEW_SLOT_COUNT; i++) {
            // No +1 offset here, unlike the result well: the wells sit exactly on the grid cells.
            this.previewSlots[i] = addSlot(new PreviewSlot(
                    machine,
                    BlockEntityArcaneAssembler.PREVIEW_SLOT_START + i,
                    previewX + (i % 3) * 18,
                    previewY + (i / 3) * 18));
        }

        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                if (assembler == null) {
                    return index >= 0 && index < clientData.length ? clientData[index] : 0;
                }
                // Two separate numbers rather than a percentage, so the ratio stays right when speed cards
                // shorten the craft.
                return switch (index) {
                    // Four-unit steps: broadcastChanges sends a value only when it changed, and a vis column
                    // is drawn far coarser than one vis. The tick count is not throttled.
                    case DATA_BUFFERED_VIS -> (assembler.getBufferedVis() / 4) * 4;
                    case DATA_ASPECT_AIR -> aspectForSlot(index);
                    case DATA_ASPECT_WATER -> aspectForSlot(index);
                    case DATA_ASPECT_FIRE -> aspectForSlot(index);
                    case DATA_ASPECT_ORDER -> aspectForSlot(index);
                    case DATA_ASPECT_ENTROPY -> aspectForSlot(index);
                    case DATA_ASPECT_EARTH -> aspectForSlot(index);
                    case DATA_CRAFTING -> assembler.isCrafting() ? 1 : 0;
                    // Quantised like the vis pool: the bar interpolates, and a value that changes every tick
                    // is twenty packets a second to say what four do.
                    case DATA_CRAFT_TICK -> (assembler.getCraftTicks() / 4) * 4;
                    case DATA_TICKS_PER_CRAFT -> assembler.getTicksPerCraft();
                    case DATA_GEAR_DISCOUNT -> assembler.getGearDiscount();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index >= 0 && index < clientData.length) {
                    clientData[index] = value;
                }
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        };
        addDataSlots(data);
    }

    private void onUpgradesChanged() {
        if (assembler == null) {
            return;
        }
        int count = 0;
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            if (!upgrades.getItem(i).isEmpty()) {
                count++;
            }
        }
        assembler.setSpeedUpgrades(count);
    }

    /**
     * Rebuilds the read-only pattern mirror from the installed knowledge core; derived on whichever side
     * is drawing, and called every frame from the screen.
     */
    public void refreshPatternView() {
        ItemStack core = coreStack();
        // Taken at most once a tick: this is a per-frame call and the hash walks the whole pattern store.
        long now = playerInventory.player.level().getGameTime();
        if (now != coreSignatureTick) {
            coreSignatureTick = now;
            sampledCore = StackSignatures.of(core);
        }
        if (sampledCore == mirroredCore) {
            return;
        }
        mirroredCore = sampledCore;
        HandlerKnowledgeCore handler = coreHandler();
        List<ItemStack> outputs = handler == null ? List.of() : handler.storedOutputs();
        for (int i = 0; i < BlockEntityArcaneAssembler.PATTERN_SLOT_COUNT; i++) {
            ItemStack stack = i < outputs.size() ? outputs.get(i) : ItemStack.EMPTY;
            if (assembler != null) {
                writeMirrorSlot(BlockEntityArcaneAssembler.PATTERN_SLOT_START + i, stack);
            } else if (!ItemStack.matches(patternDisplay.getItem(i), stack)) {
                patternDisplay.setItem(i, stack.copy());
            }
        }
    }

    /**
     * The 3x3 ingredient preview, nine stacks in row-major order, read from the slots the server syncs the
     * machine's preview band into.
     */
    public ItemStack getPreviewSlot(int index) {
        if (index < 0 || index >= previewSlots.length || previewSlots[index] == null) {
            return ItemStack.EMPTY;
        }
        return previewSlots[index].getItem();
    }

    /**
     * The item to paint into the craft preview's result well, read from the mirrored slot the server syncs
     * the machine's {@code TARGET_SLOT} into.
     */
    public ItemStack getPreviewResult() {
        return targetSlot == null ? ItemStack.EMPTY : targetSlot.getItem();
    }

    /** An acceleration-card slot; the screen reads these to know which are empty. */
    public Slot getUpgradeSlot(int index) {
        int slot = IDX_UPGRADE_START + index;
        if (index < 0 || slot >= IDX_UPGRADE_END || slot >= slots.size()) {
            throw new IndexOutOfBoundsException("upgrade slot " + index);
        }
        return slots.get(slot);
    }

    /** Writes a slot of the display-only mirror on the machine's own inventory. Server side only. */
    private void writeMirrorSlot(int machineSlot, ItemStack stack) {
        if (assembler != null) {
            assembler.getInventory().setItem(machineSlot, stack);
        }
    }

    /** The knowledge core as this menu can see it, which is the slot on both sides. */
    private ItemStack coreStack() {
        int index = IDX_CORE;
        return index < slots.size() ? slots.get(index).getItem() : ItemStack.EMPTY;
    }

    /**
     * The core's patterns, on either side. The level comes from the block entity where there is one and
     * from the player otherwise, the split the Knowledge Inscriber's menu makes.
     */
    private @Nullable HandlerKnowledgeCore coreHandler() {
        Level level = assembler != null ? assembler.getLevel() : playerInventory.player.level();
        if (level == null) {
            return null;
        }
        return HandlerKnowledgeCore.of(coreStack(), level.registryAccess());
    }

    public int getBufferedVis() {
        return data.get(DATA_BUFFERED_VIS);
    }

    /**
     * The six bar columns in the order the art paints them, <b>not</b> in {@code PRIMALS} order - indexing
     * one by the other paints two columns with the wrong aspect, every bar right in height.
     */
    private static final int[] BAR_ASPECTS = {
        primalIndex(TCAspects.AER),
        primalIndex(TCAspects.AQUA),
        primalIndex(TCAspects.IGNIS),
        primalIndex(TCAspects.ORDO),
        primalIndex(TCAspects.PERDITIO),
        primalIndex(TCAspects.TERRA)
    };

    /** Where an aspect sits in the block entity's storage order, else 0 when it is not a primal. */
    private static int primalIndex(ResourceKey<IAspect> aspect) {
        int index = BlockEntityArcaneAssembler.PRIMALS.indexOf(aspect);
        return Math.max(0, index);
    }

    /**
     * The vis banked for the bar column whose data slot is {@code index}, in whole vis, read through
     * {@link #BAR_ASPECTS} rather than by subtracting the slot constants.
     */
    private int aspectForSlot(int index) {
        int column = index - DATA_ASPECT_AIR;
        if (column < 0 || column >= BAR_ASPECTS.length) {
            return 0;
        }
        // Four-unit steps, as for the pool: this is broadcast every tick.
        return (assembler.getAspectVis(BAR_ASPECTS[column]) / 4) * 4;
    }

    /**
     * How much one bar column holds, in art order: 0 is air, 5 is earth. Whichever channel has more, as
     * for the craft progress: the block entity is the machine's own state and the data slot the server's
     * copy of it, and a channel that has not caught up can only be behind.
     */
    public int getBarVis(int column) {
        if (column < 0 || column >= BAR_ASPECTS.length) {
            return 0;
        }
        BlockEntityArcaneAssembler machine = machineView();
        int live = machine == null ? 0 : machine.getAspectVis(BAR_ASPECTS[column]);
        return Math.max(live, data.get(DATA_ASPECT_AIR + column));
    }

    /**
     * Whether a craft is running, true if either channel says so: the block entity is the machine's own
     * state and the data slot the server's copy, and a stale channel can only say 'idle'.
     */
    public boolean isCrafting() {
        BlockEntityArcaneAssembler machine = machineView();
        return (machine != null && machine.isCrafting()) || data.get(DATA_CRAFTING) != 0;
    }

    /**
     * Craft progress as a 0..1 fraction, whichever channel has got further; where they disagree, one has
     * not caught up.
     */
    public float getProgress() {
        BlockEntityArcaneAssembler machine = machineView();
        float live = machine != null ? machine.getCraftProgress() : 0.0F;
        int total = Math.max(1, data.get(DATA_TICKS_PER_CRAFT));
        float mirrored = Math.min(1.0F, data.get(DATA_CRAFT_TICK) / (float) total);
        return Math.max(live, mirrored);
    }

    /** Both channels' own view of the craft, for the self-test: a failure says which one went quiet. */
    public String progressForTest() {
        BlockEntityArcaneAssembler machine = machineView();
        String live = machine == null
                ? "no block entity at " + clientPos
                : "craft=" + machine.isCrafting() + " ticks=" + machine.getCraftTicks()
                        + " prog=" + machine.getCraftProgress();
        int total = Math.max(1, data.get(DATA_TICKS_PER_CRAFT));
        String mirrored = "craft=" + (data.get(DATA_CRAFTING) != 0) + " tick=" + data.get(DATA_CRAFT_TICK)
                + "/" + total + " prog=" + Math.min(1.0F, data.get(DATA_CRAFT_TICK) / (float) total);
        return "live[" + live + "] slot[" + mirrored + "] union[craft=" + isCrafting()
                + " prog=" + getProgress() + "]";
    }

    /**
     * The machine as this side can see it, or null when there is none. On the client it is looked up from
     * the position the server sent; an unloaded chunk answers null, which is what the data slots are for.
     */
    private @Nullable BlockEntityArcaneAssembler machineView() {
        if (assembler != null) {
            return assembler;
        }
        if (clientPos == null) {
            return null;
        }
        if (clientMachine == null || clientMachine.isRemoved()) {
            BlockEntity found = playerInventory.player.level().getBlockEntity(clientPos);
            clientMachine = found instanceof BlockEntityArcaneAssembler machine ? machine : null;
        }
        return clientMachine;
    }

    public int getGearDiscount() {
        return data.get(DATA_GEAR_DISCOUNT);
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
            if (!merged && !BlockEntityArcaneAssembler.isGearItem(stack)) {
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
