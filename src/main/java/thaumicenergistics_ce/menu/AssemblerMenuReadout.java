package thaumicenergistics_ce.menu;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.compat.thaumaturge.TcAspects;

/** 菜单显示的数字。每个都取机器持有的值与数据槽携带的值中较大的那个：
 * 方块实体是机器自身状态，没跟上的槽只可能落后。客户端菜单的机器为 null，
 * 那里唯一来源是数据槽，靠打开数据包带的位置找到。 */
final class AssemblerMenuReadout {

    private final MenuArcaneAssembler menu;
    private final ContainerData data;

    /**
     * 机器位置，由打开数据包发送：客户端菜单唯一的抓手，它的机器为 null，没有位置就没有合成进度。
     */
    private @Nullable BlockPos clientPos;

    private @Nullable BlockEntityArcaneAssembler clientMachine;

    AssemblerMenuReadout(MenuArcaneAssembler menu) {
        this.menu = menu;
        this.data = new ArcaneAssemblerReadings(menu.assembler, this::aspectForSlot);
    }

    /** 服务端填、客户端读的表，顺序按 {@code DATA_} 常量命名。 */
    ContainerData data() {
        return data;
    }

    /** 收下打开数据包带的位置，客户端通向机器的唯一途径。 */
    void setClientPos(BlockPos pos) {
        this.clientPos = pos;
    }

    int getBufferedVis() {
        return data.get(MenuArcaneAssembler.DATA_BUFFERED_VIS);
    }

    /**
     * 六个条形列，按素材绘制顺序排，不按 {@code PRIMALS} 顺序：拿一个去索引另一个，
     * 会让两列画上错的要素，而每根条的高度都对。
     */
    private static final int[] BAR_ASPECTS = {
        primalIndex(TcAspects.AER),
        primalIndex(TcAspects.AQUA),
        primalIndex(TcAspects.IGNIS),
        primalIndex(TcAspects.ORDO),
        primalIndex(TcAspects.PERDITIO),
        primalIndex(TcAspects.TERRA)
    };

    private static int primalIndex(ResourceKey<IAspect> aspect) {
        int index = BlockEntityArcaneAssembler.PRIMALS.indexOf(aspect);
        return Math.max(0, index);
    }

    /**
     * 数据槽位是 {@code index} 的那一列蓄的 vis，单位整数 vis，经 {@link #BAR_ASPECTS} 读，
     * 不用槽位常量相减。
     */
    private int aspectForSlot(int index) {
        int column = index - MenuArcaneAssembler.DATA_ASPECT_AIR;
        if (column < 0 || column >= BAR_ASPECTS.length) {
            return 0;
        }
        // 四格一取整，跟 vis 池一样：这个值每 tick 广播。
        return (menu.assembler.getAspectVis(BAR_ASPECTS[column]) / 4) * 4;
    }

    /**
     * 一列持有多少，按素材顺序：0 是风，5 是土。
     * 取更大的一方：方块实体是机器自身状态，没跟上的槽只可能落后。
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
     * 这一侧能看到的机器，没有则为 null。客户端按服务端发的位置查；区块未加载返回 null，
     * 数据槽就是为此而设。
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
