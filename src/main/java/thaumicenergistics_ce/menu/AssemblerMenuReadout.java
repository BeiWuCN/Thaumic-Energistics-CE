package thaumicenergistics_ce.menu;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TCAspects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;

/** 菜单显示的数字。每一个都是机器持有的值与数据槽位
 * 所携带的值中较大的那个：方块实体是机器自身的状态，而尚未跟上的槽位只可能
 * 落后。客户端菜单的机器为 null，所以在那里唯一的来源是数据槽位，通过
 * 打开数据包所携带的位置到达。 */
final class AssemblerMenuReadout {

    private final MenuArcaneAssembler menu;
    private final ContainerData data;

    /**
     * 机器所在的位置，由打开数据包发送：这是客户端菜单对它唯一的抓手，因为它的
     * 机器为 null，所以没有位置就没有合成进度。
     */
    private @Nullable BlockPos clientPos;

    private @Nullable BlockEntityArcaneAssembler clientMachine;

    AssemblerMenuReadout(MenuArcaneAssembler menu) {
        this.menu = menu;
        this.data = new ArcaneAssemblerReadings(menu.assembler, this::aspectForSlot);
    }

    /** 服务端填充、客户端读取的表，顺序按 {@code DATA_} 常量所命名。 */
    ContainerData data() {
        return data;
    }

    /** 接收打开数据包所携带的位置，这是客户端通向机器的唯一途径。 */
    void setClientPos(BlockPos pos) {
        this.clientPos = pos;
    }

    int getBufferedVis() {
        return data.get(MenuArcaneAssembler.DATA_BUFFERED_VIS);
    }

    /**
     * 六个条形列，按素材绘制的顺序排列，而不是按 {@code PRIMALS} 顺序——用其中一个
     * 去索引另一个，会让两列画上错误的要素，而每根条的高度都对。
     */
    private static final int[] BAR_ASPECTS = {
        primalIndex(TCAspects.AER),
        primalIndex(TCAspects.AQUA),
        primalIndex(TCAspects.IGNIS),
        primalIndex(TCAspects.ORDO),
        primalIndex(TCAspects.PERDITIO),
        primalIndex(TCAspects.TERRA)
    };

    private static int primalIndex(ResourceKey<IAspect> aspect) {
        int index = BlockEntityArcaneAssembler.PRIMALS.indexOf(aspect);
        return Math.max(0, index);
    }

    /**
     * 数据槽位为 {@code index} 的那个条形列所蓄积的 vis，以整数 vis 计，通过
     * {@link #BAR_ASPECTS} 读取，而不是靠减去槽位常量。
     */
    private int aspectForSlot(int index) {
        int column = index - MenuArcaneAssembler.DATA_ASPECT_AIR;
        if (column < 0 || column >= BAR_ASPECTS.length) {
            return 0;
        }
        // 以四为单位取整，与 vis 池相同：这个值每个 tick 都会广播。
        return (menu.assembler.getAspectVis(BAR_ASPECTS[column]) / 4) * 4;
    }

    /**
     * 一个条形列持有多少，按素材顺序：0 是风，5 是土。取更大的一方，因为
     * 方块实体是机器自身的状态，而尚未跟上的数据槽位只可能落后。
     */
    int getBarVis(int column) {
        if (column < 0 || column >= BAR_ASPECTS.length) {
            return 0;
        }
        BlockEntityArcaneAssembler machine = machineView();
        int live = machine == null ? 0 : machine.getAspectVis(BAR_ASPECTS[column]);
        return Math.max(live, data.get(MenuArcaneAssembler.DATA_ASPECT_AIR + column));
    }

    boolean isCrafting() {
        BlockEntityArcaneAssembler machine = machineView();
        return (machine != null && machine.isCrafting()) || data.get(MenuArcaneAssembler.DATA_CRAFTING) != 0;
    }

    float getProgress() {
        BlockEntityArcaneAssembler machine = machineView();
        float live = machine != null ? machine.getCraftProgress() : 0.0F;
        int total = Math.max(1, data.get(MenuArcaneAssembler.DATA_TICKS_PER_CRAFT));
        float mirrored = Math.min(1.0F, data.get(MenuArcaneAssembler.DATA_CRAFT_TICK) / (float) total);
        return Math.max(live, mirrored);
    }

    /**
     * 这一侧所能看到的机器，没有则为 null。在客户端它由服务端发来的
     * 位置查找；未加载的区块会返回 null，数据槽位正是为此而设。
     */
    private @Nullable BlockEntityArcaneAssembler machineView() {
        if (menu.assembler != null) {
            return menu.assembler;
        }
        if (clientPos == null) {
            return null;
        }
        if (clientMachine == null || clientMachine.isRemoved()) {
            BlockEntity found = menu.playerInventory.player.level().getBlockEntity(clientPos);
            clientMachine = found instanceof BlockEntityArcaneAssembler machine ? machine : null;
        }
        return clientMachine;
    }

    int getGearDiscount() {
        return data.get(MenuArcaneAssembler.DATA_GEAR_DISCOUNT);
    }
}
