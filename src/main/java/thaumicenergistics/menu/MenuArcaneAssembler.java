package thaumicenergistics.menu;

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
import thaumicenergistics.blockentity.BlockEntityArcaneAssembler;
import thaumicenergistics.gui.GuiLayout;
import thaumicenergistics.init.ModItems;
import thaumicenergistics.init.ModMenuTypes;
import thaumicenergistics.inventory.GearSlots;
import thaumicenergistics.inventory.HandlerKnowledgeCore;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.slot.PreviewSlot;
import thaumicenergistics.menu.slot.ReadOnlySlot;

/**
 * The Arcane Assembler's menu: a 7x3 read-only pattern mirror, the knowledge core and four acceleration
 * cards in the right-hand column, a target-output preview, and four gear slots whose vis discount
 * applies to this assembler's crafts.
 *
 * <p>Coordinates come from a {@link GuiLayout} generated from the same constants as the background
 * texture, handed in by the screen so this class stays free of client-only resource access; the
 * fallback below covers a missing file.
 */
public class MenuArcaneAssembler extends AbstractContainerMenu {

    /** Fallback geometry, copied from the reference container, for when the generated layout is
     * unreadable: the slots would be misplaced but the menu still functional. */
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

    // Slot indices, in registration order: player, core, patterns, upgrades, gear.
    //
    // There is no index for the craft preview's result well: it is part of the panel art and gets no
    // slot, because a real Slot always draws its hover highlight.
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
    /**
     * The six primals, one slot each, starting here and running in {@link #BAR_ASPECTS} order. Six slots
     * rather than one because a single pool cannot draw six bars: the screen used to read
     * {@code DATA_BUFFERED_VIS} for every column and all six drew the same height.
     */
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

    /** The player's inventory, kept for its {@code player}: the client has no block entity, so the
     * player's own level is the only registry this menu is guaranteed to have. */
    private final Inventory playerInventory;
    /**
     * The 7x3 pattern wells' container, on the client. Client-owned like the Knowledge Inscriber's: the
     * wells show the knowledge core, which is a slot, so the client can work them out itself - the
     * machine's own container has no update tag to sync.
     */
    private final SimpleContainer patternDisplay =
            new SimpleContainer(BlockEntityArcaneAssembler.PATTERN_SLOT_COUNT);

    /** Signature of the core the wells were last filled from, so they are only refilled when it changes. */
    private int mirroredCore = -1;
    /**
     * The core signature as of {@link #coreSignatureTick}: hashing a core walks its whole stored pattern
     * store and the screen asks once a frame, so it is taken at most once a tick - one tick of staleness
     * changes nothing, because the screen polls its own state once a tick anyway.
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
     * Where the machine is, sent by the server in the open packet and read on the client. The client
     * menu's only handle on the machine: {@link #assembler} is null there, so without the position there
     * is nothing to read a craft's progress from.
     */
    private @Nullable BlockPos clientPos;

    /** The client's own view of the machine, resolved from {@link #clientPos} and cached. */
    private @Nullable BlockEntityArcaneAssembler clientMachine;

    /**
     * The mirrored slot the craft's product is shown in, or {@code null} before the layout is built. Held
     * as the slot rather than an index, so adding or reordering slots cannot make it name the wrong one.
     */
    private @Nullable Slot targetSlot;

    /** The nine preview wells, held as slots for the same reason as {@link #targetSlot}: the index
     * constants here are positional. */
    private final Slot[] previewSlots = new Slot[BlockEntityArcaneAssembler.PREVIEW_SLOT_COUNT];

    /** Server-side constructor, opened from the block. */
    public MenuArcaneAssembler(int containerId, Inventory playerInventory, BlockEntityArcaneAssembler assembler) {
        // Both sides read the layout from the mod's resources, so their slot positions cannot drift apart.
        this(containerId, playerInventory, assembler, assembler.getInventory(), GuiLayout.load());
        refreshPatternView();
        onUpgradesChanged();
    }

    /** Client-side constructor, opened from the network. Loads the layout itself so slot coordinates and
     * the background art come from the same source. */
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
        //
        // mayPlace is not optional: a Slot's mayPlace is what the player's click goes through, while the
        // block's own container is only consulted by hopper-style automation.
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
        //
        //    Mirrored through a slot rather than painted by the screen: the block has no update tag that
        //    carries items, so this slot is the only path for the product to reach the client. Before it,
        //    getPreviewResult() read from nothing and the well drew a blank for every craft.
        //
        //    Both sides use `machine`, unlike the pattern mirror: the product is not derivable on the
        //    client, so it has to be sent, and `machine` on the client is the container the sync lands in.
        //
        //    Added last because the index constants are positional and IDX_GEAR_START is used by
        //    quickMoveStack - a slot inserted earlier would route shift-clicked gear wrongly.
        //
        //    PreviewSlot, not ReadOnlySlot, so the well does not highlight under the cursor. Its +1 offset
        //    is because the well is bigger than a grid cell: the interior runs 113..134 by 96..117.
        this.targetSlot = addSlot(new PreviewSlot(
                machine,
                BlockEntityArcaneAssembler.TARGET_SLOT,
                (layout != null ? layout.previewResult().x() : FB_RESULT_X) + 1,
                (layout != null ? layout.previewResult().y() : FB_RESULT_Y) + 1));

        // 7. The running craft's 3x3, shown in the preview wells.
        //
        //    Added last for the same reason the result slot is - the index constants are positional.
        //    PreviewSlot rather than ReadOnlySlot, so the wells do not highlight under the cursor.
        for (int i = 0; i < BlockEntityArcaneAssembler.PREVIEW_SLOT_COUNT; i++) {
            // At the layout's own coordinates, with no nudge: copying the screen's old one-pixel paint
            // offset here put every preview a pixel off.
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
                // The tick count and the craft's length as two separate numbers rather than a percentage, so
                // the ratio stays right when speed cards shorten the craft.
                return switch (index) {
                    // Four-unit steps: broadcastChanges sends a value only when it changed, and a vis column
                    // is drawn far coarser than one vis. The tick count is deliberately not throttled.
                    case DATA_BUFFERED_VIS -> (assembler.getBufferedVis() / 4) * 4;
                    case DATA_ASPECT_AIR -> aspectForSlot(index);
                    case DATA_ASPECT_WATER -> aspectForSlot(index);
                    case DATA_ASPECT_FIRE -> aspectForSlot(index);
                    case DATA_ASPECT_ORDER -> aspectForSlot(index);
                    case DATA_ASPECT_ENTROPY -> aspectForSlot(index);
                    case DATA_ASPECT_EARTH -> aspectForSlot(index);
                    case DATA_CRAFTING -> assembler.isCrafting() ? 1 : 0;
                    // Quantised like the vis pool: the bar interpolates, and a value that changes every tick
                    // is twenty packets a second to say what four already say.
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

    private void syncUpgradesFromAssembler() {
        // The cards live in the menu; only the count is mirrored into the assembler, for the craft timing.
        onUpgradesChanged();
    }

    /**
     * Rebuilds the read-only pattern mirror from the knowledge core currently installed. Derived on
     * whichever side is drawing and skipped when the core has not changed; called every frame from the
     * screen.
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
     * The 3x3 ingredient preview, as nine stacks in row-major order, read from the slots the server syncs
     * the machine's preview band into. It used to return {@link ItemStack#EMPTY} unconditionally, which is
     * why the band stayed blank for every craft.
     */
    public ItemStack getPreviewSlot(int index) {
        if (index < 0 || index >= previewSlots.length || previewSlots[index] == null) {
            return ItemStack.EMPTY;
        }
        return previewSlots[index].getItem();
    }

    /**
     * The item to paint into the craft preview's result well, or empty for none, read from the mirrored
     * slot the server syncs the machine's {@code TARGET_SLOT} into. It used to return
     * {@link ItemStack#EMPTY} unconditionally, which left the well blank for every craft.
     */
    public ItemStack getPreviewResult() {
        return targetSlot == null ? ItemStack.EMPTY : targetSlot.getItem();
    }

    /**
     * One of the acceleration-card slots. The screen needs these to know which are still empty, so it can
     * draw AE2's empty-upgrade icon into those.
     */
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
     * from the player otherwise, the same split the Knowledge Inscriber's menu makes.
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
     * The six bar columns in the order the art paints them.
     *
     * <p><b>Not {@code BlockEntityArcaneAssembler.PRIMALS} order, and this is the trap worth naming:</b>
     * the texture runs air, water, fire, order, entropy, earth while {@code TCAspects.PRIMALS} is declared
     * aer, ignis, aqua, terra, ordo, perditio - indexing one by the other paints two columns with the
     * wrong aspect, every bar right in height.
     */
    private static final int[] BAR_ASPECTS = {
        primalIndex(TCAspects.AER),
        primalIndex(TCAspects.AQUA),
        primalIndex(TCAspects.IGNIS),
        primalIndex(TCAspects.ORDO),
        primalIndex(TCAspects.PERDITIO),
        primalIndex(TCAspects.TERRA)
    };

    /** Where an aspect sits in the block entity's own storage order, or 0 if it is not a primal at all. */
    private static int primalIndex(ResourceKey<IAspect> aspect) {
        int index = BlockEntityArcaneAssembler.PRIMALS.indexOf(aspect);
        return Math.max(0, index);
    }

    /**
     * The vis banked for the bar column whose data slot is {@code index}, in whole vis. Read through
     * {@link #BAR_ASPECTS} rather than by subtracting the slot constants, which only coincide by
     * construction.
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
     * How much one bar column is holding, by the column's position in the art: 0 is air and 5 is earth.
     * By column rather than by aspect, so the screen needs no knowledge of {@code TCAspects.PRIMALS}.
     */
    public int getBarVis(int column) {
        if (column < 0 || column >= BAR_ASPECTS.length) {
            return 0;
        }
        return data.get(DATA_ASPECT_AIR + column);
    }

    /**
     * Whether a craft is running, true if either channel says so. The block entity is the machine's own
     * state, mirrored by its update tag, and the data slot is the server's copy; a channel that has not
     * been updated can only contribute 'idle', so the union cannot hide a craft. Reading one channel alone
     * is what left the bar reported as never moving.
     */
    public boolean isCrafting() {
        BlockEntityArcaneAssembler machine = machineView();
        return (machine != null && machine.isCrafting()) || data.get(DATA_CRAFTING) != 0;
    }

    /**
     * Craft progress as a 0..1 fraction, whichever channel has got further through the craft. Where they
     * disagree it is because one has not caught up, so the larger is the one that has.
     */
    public float getProgress() {
        BlockEntityArcaneAssembler machine = machineView();
        float live = machine != null ? machine.getCraftProgress() : 0.0F;
        int total = Math.max(1, data.get(DATA_TICKS_PER_CRAFT));
        float mirrored = Math.min(1.0F, data.get(DATA_CRAFT_TICK) / (float) total);
        return Math.max(live, mirrored);
    }

    /**
     * Both channels' own view of the craft, for the assembler's self-test. This display is the one thing
     * that cannot be checked from the server, and with the two values side by side a failure says which
     * channel went quiet.
     */
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
     * The machine as this side can see it, or null when there is none to read. On the client it is looked
     * up from the position the server sent, in the player's level - the one level the client always has.
     * A chunk that is not loaded yet answers null, which is what the data slots are for.
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
