package thaumicenergistics_ce.menu;

import net.minecraft.world.inventory.ContainerData;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber;
import thaumicenergistics_ce.blockentity.vibrationchamber.VibrationChamberSync;

/**
 * 振动室的读数，由菜单交给屏幕：服务端上它们经 {@link VibrationChamberSync#reading}
 * 取自机器，客户端上它们就是服务端最后发来的值，而 {@code set} 原样保留传入的值，
 * 因为客户端上的槽位同步会调用它。
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
