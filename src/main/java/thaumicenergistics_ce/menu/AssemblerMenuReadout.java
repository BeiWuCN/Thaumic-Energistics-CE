package thaumicenergistics_ce.menu;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TCAspects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;

/** The numbers the menu shows. Each one is the larger of what the machine holds and what the data slots
 * carry: the block entity is the machine's own state, and a slot that has not caught up can only be
 * behind. The client menu's machine is null, so there its only source is the data slots, reached
 * through the position the open packet carried. */
final class AssemblerMenuReadout {

    private final MenuArcaneAssembler menu;
    private final ContainerData data;

    /**
     * Where the machine is, sent in the open packet: the client menu's only handle on it, since its
     * machine is null, so no position means no craft progress.
     */
    private @Nullable BlockPos clientPos;

    private @Nullable BlockEntityArcaneAssembler clientMachine;

    AssemblerMenuReadout(MenuArcaneAssembler menu) {
        this.menu = menu;
        this.data = new ArcaneAssemblerReadings(menu.assembler, this::aspectForSlot);
    }

    /** The table the server fills and the client reads, in the order the {@code DATA_} constants name. */
    ContainerData data() {
        return data;
    }

    /** Takes the position the open packet carried, the client's only way to the machine. */
    void setClientPos(BlockPos pos) {
        this.clientPos = pos;
    }

    int getBufferedVis() {
        return data.get(MenuArcaneAssembler.DATA_BUFFERED_VIS);
    }

    /**
     * The six bar columns in the order the art paints them, not in {@code PRIMALS} order - indexing
     * one by the other paints two columns with the wrong aspect, every bar right in height.
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
     * The vis banked for the bar column whose data slot is {@code index}, in whole vis, read through
     * {@link #BAR_ASPECTS} rather than by subtracting the slot constants.
     */
    private int aspectForSlot(int index) {
        int column = index - MenuArcaneAssembler.DATA_ASPECT_AIR;
        if (column < 0 || column >= BAR_ASPECTS.length) {
            return 0;
        }
        // Four-unit steps, as for the pool: this is broadcast every tick.
        return (menu.assembler.getAspectVis(BAR_ASPECTS[column]) / 4) * 4;
    }

    /**
     * How much one bar column holds, in art order: 0 is air, 5 is earth. Whichever channel has more, since
     * the block entity is the machine's own state and a data slot that has not caught up can only be behind.
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
     * The machine as this side can see it, or null when there is none. On the client it is looked up from
     * the position the server sent; an unloaded chunk answers null, which is what the data slots are for.
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
