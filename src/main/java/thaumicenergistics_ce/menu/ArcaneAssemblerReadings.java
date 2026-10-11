package thaumicenergistics_ce.menu;

import java.util.function.IntUnaryOperator;
import net.minecraft.world.inventory.ContainerData;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;

final class ArcaneAssemblerReadings implements ContainerData {

    private final @Nullable BlockEntityArcaneAssembler assembler;

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
        return switch (index) {
            case MenuArcaneAssembler.DATA_BUFFERED_VIS -> (assembler.getBufferedVis() / 4) * 4;
            case MenuArcaneAssembler.DATA_ASPECT_AIR -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_WATER -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_FIRE -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_ORDER -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_ENTROPY -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_EARTH -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_CRAFTING -> assembler.isCrafting() ? 1 : 0;
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
