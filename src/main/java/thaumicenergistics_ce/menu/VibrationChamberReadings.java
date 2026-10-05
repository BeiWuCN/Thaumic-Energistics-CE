package thaumicenergistics_ce.menu;

import net.minecraft.world.inventory.ContainerData;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber;
import thaumicenergistics_ce.blockentity.vibrationchamber.VibrationChamberSync;

/**
 * The vibration chamber's readings, as the menu hands them to the screen: on the server they come off
 * the machine through {@link VibrationChamberSync#reading}, on the client they are what the server
 * last sent, and {@code set} keeps what it is given because a slot sync calls it on the client.
 */
final class VibrationChamberReadings implements ContainerData {

    private final @Nullable BlockEntityEssentiaVibrationChamber chamber;

    private final int[] readings = new int[VibrationChamberSync.COUNT];

    VibrationChamberReadings(@Nullable BlockEntityEssentiaVibrationChamber chamber) {
        this.chamber = chamber;
    }

    @Override
    public int get(int index) {
        if (chamber == null || chamber.getLevel() == null || chamber.getLevel().isClientSide()) {
            return index >= 0 && index < readings.length ? readings[index] : 0;
        }
        return VibrationChamberSync.reading(chamber, index);
    }

    @Override
    public void set(int index, int value) {
        if (index >= 0 && index < readings.length) {
            readings[index] = value;
        }
    }

    @Override
    public int getCount() {
        return VibrationChamberSync.COUNT;
    }
}
