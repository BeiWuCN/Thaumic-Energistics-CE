package thaumicenergistics_ce.menu;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaVibrationChamber.BurnState;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaVibrationChamber;
import thaumicenergistics_ce.init.ModMenuTypes;

/**
 * The Essentia Vibration Chamber's menu: the player's inventory, and the machine's three readings.
 * <ul>
 * <li>The machine has no slots: fuel arrives by pipe or from the ME network, and power leaves by cable.
 * <li>The readings travel as {@link ContainerData} because they change every tick; a screen reading its
 * own copy of the block entity would show whatever the last block update happened to carry.
 * </ul>
 */
public class MenuEssentiaVibrationChamber extends AbstractContainerMenu {

    /** The player's inventory, which is every slot this menu has. */
    public static final int PLAYER_SLOTS = 36;

    /** The three inventory rows and the hotbar under them - the standard window's layout. */
    private static final int INV_X = 8;
    private static final int INV_Y = 84;
    private static final int HOTBAR_Y = 142;
    private static final int PITCH = 18;

    // The readings, in the order the screen reads them out of ContainerData.
    public static final int DATA_ESSENTIA = 0;
    public static final int DATA_ESSENTIA_MAX = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_ENERGY_MAX = 3;
    public static final int DATA_BURN = 4;
    public static final int DATA_BURN_TOTAL = 5;
    /** Power per tick, times ten: the reading has one decimal and ContainerData carries ints. */
    public static final int DATA_AE_PER_TICK = 6;
    /** The colour of the aspect being burned, or zero when nothing is. */
    public static final int DATA_ASPECT_COLOUR = 7;
    /** The machine's state, as the ordinal of {@link BlockEntityEssentiaVibrationChamber.BurnState}. */
    public static final int DATA_STATE = 8;

    private static final int DATA_COUNT = 9;

    private final BlockEntityEssentiaVibrationChamber chamber;
    private final ContainerData data;

    /** What the server last sent, on the client. See {@link #data}. */
    private final int[] readings = new int[DATA_COUNT];

    /** The client side: the block entity the server named when it opened the window. */
    public MenuEssentiaVibrationChamber(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, blockEntity(inventory, buf));
    }

    public MenuEssentiaVibrationChamber(
            int containerId, Inventory inventory, BlockEntityEssentiaVibrationChamber chamber) {
        super(ModMenuTypes.ESSENTIA_VIBRATION_CHAMBER.get(), containerId);
        this.chamber = chamber;

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        inventory, column + row * 9 + 9, INV_X + column * PITCH, INV_Y + row * PITCH));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INV_X + column * PITCH, HOTBAR_Y));
        }

        this.data = new ContainerData() {
            /**
             * The server reads the machine; the client reads what the server last sent it. A read-only
             * {@code set} breaks it: a slot sync calls {@code set} on the client, and no bar ever fills.
             */
            @Override
            public int get(int index) {
                if (chamber == null
                        || chamber.getLevel() == null
                        || chamber.getLevel().isClientSide()) {
                    return index >= 0 && index < readings.length ? readings[index] : 0;
                }
                return switch (index) {
                    case DATA_ESSENTIA -> chamber == null ? 0 : chamber.getStoredEssentia();
                    case DATA_ESSENTIA_MAX -> BlockEntityEssentiaVibrationChamber.MAX_ESSENTIA;
                    case DATA_ENERGY -> chamber == null ? 0 : (int) Math.round(chamber.getStoredEnergy());
                    case DATA_ENERGY_MAX -> (int) BlockEntityEssentiaVibrationChamber.MAX_ENERGY_STORAGE;
                    case DATA_BURN -> chamber == null ? 0 : chamber.getBurnTicksRemaining();
                    case DATA_BURN_TOTAL -> chamber == null ? 0 : chamber.getTotalBurnTicks();
                    case DATA_AE_PER_TICK -> chamber == null ? 0 : (int) Math.round(chamber.getAePerTick() * 10.0);
                    case DATA_ASPECT_COLOUR -> aspectColour(chamber);
                    case DATA_STATE -> chamber == null ? BurnState.IDLE.ordinal() : chamber.getBurnState().ordinal();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // What the server sent, kept for get to hand back on the client. See the note there.
                if (index >= 0 && index < readings.length) {
                    readings[index] = value;
                }
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        addDataSlots(data);
    }

    /** The colour of the aspect in the buffer, or zero. */
    private static int aspectColour(BlockEntityEssentiaVibrationChamber chamber) {
        if (chamber == null) {
            return 0;
        }
        Holder<IAspect> aspect = chamber.currentAspectHolder();
        return aspect == null ? 0 : aspect.value().color() | 0xFF000000;
    }

    @Nullable
    private static BlockEntityEssentiaVibrationChamber blockEntity(
            Inventory inventory, RegistryFriendlyByteBuf buf) {
        return inventory.player.level().getBlockEntity(buf.readBlockPos())
                instanceof BlockEntityEssentiaVibrationChamber chamber
                ? chamber
                : null;
    }

    /** A reading, for the screen. */
    public int reading(int index) {
        return data.get(index);
    }

    /** The machine's state, as the server last sent it; the screen derives nothing from the readings. */
    public BurnState state() {
        return BurnState.byOrdinal(data.get(DATA_STATE));
    }

    /** Whether the machine is converting essentia right now. */
    public boolean isBurning() {
        return state() == BurnState.BURNING;
    }

    /** How full the energy slot is, from 0 to 1. */
    public float energyFill() {
        int max = data.get(DATA_ENERGY_MAX);
        return max <= 0 ? 0.0F : Math.min(1.0F, (float) data.get(DATA_ENERGY) / max);
    }

    /** How full the essentia buffer is, from 0 to 1. */
    public float essentiaFill() {
        int max = data.get(DATA_ESSENTIA_MAX);
        return max <= 0 ? 0.0F : Math.min(1.0F, (float) data.get(DATA_ESSENTIA) / max);
    }

    /** How far through the current unit of fuel the machine is, from 0 to 1. */
    public float burnProgress() {
        int total = data.get(DATA_BURN_TOTAL);
        return total <= 0 ? 0.0F : 1.0F - (float) data.get(DATA_BURN) / total;
    }

    /**
     * Shift-clicking moves stacks between the player's inventory and the hotbar, and nothing else: the
     * machine has no slots, so this is not an omission but the most that can be done.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        moved = stack.copy();
        if (index < 27) {
            if (!moveItemStackTo(stack, 27, PLAYER_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 27, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }

    /**
     * Whether the window should stay open: the block entity is the whole test, since the distance check
     * is vanilla's own, in {@code stillValid}.
     */
    @Override
    public boolean stillValid(Player player) {
        return chamber != null
                && chamber.getLevel() != null
                && player.distanceToSqr(
                                chamber.getBlockPos().getX() + 0.5,
                                chamber.getBlockPos().getY() + 0.5,
                                chamber.getBlockPos().getZ() + 0.5)
                        <= 64.0;
    }
}
