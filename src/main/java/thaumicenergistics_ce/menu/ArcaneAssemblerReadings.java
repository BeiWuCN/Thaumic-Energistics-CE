package thaumicenergistics_ce.menu;

import java.util.function.IntUnaryOperator;
import net.minecraft.world.inventory.ContainerData;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;

/**
 * The arcane assembler's readings, as the menu hands them to the screen: on the server they come off
 * the machine, on the client they are what the server last sent, and {@code set} keeps what it is
 * given because a slot sync calls it on the client.
 */
final class ArcaneAssemblerReadings implements ContainerData {

    private final @Nullable BlockEntityArcaneAssembler assembler;

    /** Reads one of the six per-aspect slots. The menu owns the bar order, so it answers here. */
    private final IntUnaryOperator aspectForSlot;

    private final int[] mirrored = new int[MenuArcaneAssembler.DATA_SIZE];

    ArcaneAssemblerReadings(
            @Nullable BlockEntityArcaneAssembler assembler, IntUnaryOperator aspectForSlot) {
        this.assembler = assembler;
        this.aspectForSlot = aspectForSlot;
    }

    @Override
    public int get(int index) {
        if (assembler == null) {
            return index >= 0 && index < mirrored.length ? mirrored[index] : 0;
        }
        // Two separate numbers rather than a percentage, so the ratio stays right when speed cards
        // shorten the craft.
        return switch (index) {
            // Four-unit steps: broadcastChanges sends a value only when it changed, and a vis column
            // is drawn far coarser than one vis. The tick count is not throttled.
            case MenuArcaneAssembler.DATA_BUFFERED_VIS -> (assembler.getBufferedVis() / 4) * 4;
            case MenuArcaneAssembler.DATA_ASPECT_AIR -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_WATER -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_FIRE -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_ORDER -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_ENTROPY -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_EARTH -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_CRAFTING -> assembler.isCrafting() ? 1 : 0;
            // Quantised like the vis pool: the bar interpolates, and a value that changes every tick
            // is twenty packets a second to say what four do.
            case MenuArcaneAssembler.DATA_CRAFT_TICK -> (assembler.getCraftTicks() / 4) * 4;
            case MenuArcaneAssembler.DATA_TICKS_PER_CRAFT -> assembler.getTicksPerCraft();
            case MenuArcaneAssembler.DATA_GEAR_DISCOUNT -> assembler.upgrades().getGearDiscount();
            default -> 0;
        };
    }

    @Override
    public void set(int index, int value) {
        if (index >= 0 && index < mirrored.length) {
            mirrored[index] = value;
        }
    }

    @Override
    public int getCount() {
        return MenuArcaneAssembler.DATA_SIZE;
    }
}
