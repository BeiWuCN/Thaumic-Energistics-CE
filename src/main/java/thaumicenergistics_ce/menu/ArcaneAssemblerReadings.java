package thaumicenergistics_ce.menu;

import java.util.function.IntUnaryOperator;
import net.minecraft.world.inventory.ContainerData;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;

/**
 * 奥术组装机的读数，按菜单交给界面的方式：服务端取自机器本身，
 * 客户端是服务端最后发来的值，{@code set} 保留传入内容，槽位同步会在客户端调用它。
 */
final class ArcaneAssemblerReadings implements ContainerData {

    private final @Nullable BlockEntityArcaneAssembler assembler;

    /** 读取六个按要素划分的槽位之一。条形顺序由菜单掌管，在这里作答。 */
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
        // 用两个独立的数字，不用百分比：速度卡缩短合成时间后比例仍然正确。
        return switch (index) {
            // 按四取整：broadcastChanges 只在值变化时发送，vis 列的绘制精度远粗于 1 vis。
            // tick 计数不节流。
            case MenuArcaneAssembler.DATA_BUFFERED_VIS -> (assembler.getBufferedVis() / 4) * 4;
            case MenuArcaneAssembler.DATA_ASPECT_AIR -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_WATER -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_FIRE -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_ORDER -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_ENTROPY -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_EARTH -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_CRAFTING -> assembler.isCrafting() ? 1 : 0;
            // 与 vis 池同样量化：条形会插值，每个 tick 都变的值等于每秒二十个数据包，
            // 只能说明四个包就能说明的事。
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
