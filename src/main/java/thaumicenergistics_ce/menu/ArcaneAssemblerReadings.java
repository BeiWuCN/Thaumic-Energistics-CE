package thaumicenergistics_ce.menu;

import java.util.function.IntUnaryOperator;
import net.minecraft.world.inventory.ContainerData;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;

/**
 * 奥术组装机的读数，按菜单交给界面的方式：在服务端它们取自
 * 机器本身，在客户端它们是服务端最后发来的值，而 {@code set} 会保留传入的
 * 内容，因为槽位同步会在客户端调用它。
 */
final class ArcaneAssemblerReadings implements ContainerData {

    private final @Nullable BlockEntityArcaneAssembler assembler;

    /** 读取六个按要素划分的槽位之一。条形顺序由菜单掌管，所以在这里作答。 */
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
        // 用两个独立的数字而不是百分比，这样在速度卡缩短合成时间时
        // 比例仍然正确。
        return switch (index) {
            // 以四为单位取整：broadcastChanges 只在值变化时才发送，而 vis 列
            // 的绘制精度远粗于一个 vis。tick 计数不做节流。
            case MenuArcaneAssembler.DATA_BUFFERED_VIS -> (assembler.getBufferedVis() / 4) * 4;
            case MenuArcaneAssembler.DATA_ASPECT_AIR -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_WATER -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_FIRE -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_ORDER -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_ENTROPY -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_ASPECT_EARTH -> aspectForSlot.applyAsInt(index);
            case MenuArcaneAssembler.DATA_CRAFTING -> assembler.isCrafting() ? 1 : 0;
            // 与 vis 池同样量化：条形会插值，而一个每个 tick 都变化的值等于
            // 每秒二十个数据包，只为说明四个包就能说明的事。
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
