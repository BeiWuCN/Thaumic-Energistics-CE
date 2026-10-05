package thaumicenergistics_ce.menu;

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
import thaumicenergistics_ce.blockentity.VibrationChamberSync;
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

    public static final int PLAYER_SLOTS = 36;

    private static final int INV_X = 8;
    private static final int INV_Y = 84;
    private static final int HOTBAR_Y = 142;
    private static final int PITCH = 18;

    // The readings, in the order the screen reads them out of ContainerData. The numbers and their
    // meanings live in VibrationChamberSync; these names stay because the screen reaches for them.
    public static final int DATA_ESSENTIA = VibrationChamberSync.ESSENTIA;
    public static final int DATA_ESSENTIA_MAX = VibrationChamberSync.ESSENTIA_MAX;
    public static final int DATA_ENERGY = VibrationChamberSync.ENERGY;
    public static final int DATA_ENERGY_MAX = VibrationChamberSync.ENERGY_MAX;
    public static final int DATA_BURN = VibrationChamberSync.BURN;
    public static final int DATA_BURN_TOTAL = VibrationChamberSync.BURN_TOTAL;
    public static final int DATA_AE_PER_TICK = VibrationChamberSync.AE_PER_TICK;
    public static final int DATA_ASPECT_COLOUR = VibrationChamberSync.ASPECT_COLOUR;
    public static final int DATA_STATE = VibrationChamberSync.STATE;

    private final BlockEntityEssentiaVibrationChamber chamber;
    private final ContainerData data;

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

        this.data = new VibrationChamberReadings(chamber);
        addDataSlots(data);
    }

    @Nullable
    private static BlockEntityEssentiaVibrationChamber blockEntity(
            Inventory inventory, RegistryFriendlyByteBuf buf) {
        return inventory.player.level().getBlockEntity(buf.readBlockPos())
                instanceof BlockEntityEssentiaVibrationChamber chamber
                ? chamber
                : null;
    }

    public int reading(int index) {
        return data.get(index);
    }

    public BurnState state() {
        return BurnState.byOrdinal(data.get(DATA_STATE));
    }

    public boolean isBurning() {
        return state() == BurnState.BURNING;
    }

    public float energyFill() {
        int max = data.get(DATA_ENERGY_MAX);
        return max <= 0 ? 0.0F : Math.min(1.0F, (float) data.get(DATA_ENERGY) / max);
    }

    public float essentiaFill() {
        int max = data.get(DATA_ESSENTIA_MAX);
        return max <= 0 ? 0.0F : Math.min(1.0F, (float) data.get(DATA_ESSENTIA) / max);
    }

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
