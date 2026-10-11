package thaumicenergistics_ce.menu;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;

final class AssemblerMenuReadout {

    private final MenuArcaneAssembler menu;
    private final ContainerData data;

    private @Nullable BlockPos clientPos;

    private @Nullable BlockEntityArcaneAssembler clientMachine;

    AssemblerMenuReadout(MenuArcaneAssembler menu) {
        this.menu = menu;
        this.data = new ArcaneAssemblerReadings(menu.assembler, this::aspectForSlot);
    }

    ContainerData data() {
        return data;
    }

    void setClientPos(BlockPos pos) {
        this.clientPos = pos;
    }

    int getBufferedVis() {
        return data.get(MenuArcaneAssembler.DATA_BUFFERED_VIS);
    }

    private static final int[] BAR_ASPECTS = {
        primalIndex(TTAspects.AER),
        primalIndex(TTAspects.AQUA),
        primalIndex(TTAspects.IGNIS),
        primalIndex(TTAspects.ORDO),
        primalIndex(TTAspects.PERDITIO),
        primalIndex(TTAspects.TERRA)
    };

    private static int primalIndex(ResourceKey<IAspect> aspect) {
        int index = BlockEntityArcaneAssembler.PRIMALS.indexOf(aspect);
        return Math.max(0, index);
    }

    private int aspectForSlot(int index) {
        int column = index - MenuArcaneAssembler.DATA_ASPECT_AIR;
        if (column < 0 || column >= BAR_ASPECTS.length) {
            return 0;
        }
        return (menu.assembler.getAspectVis(BAR_ASPECTS[column]) / 4) * 4;
    }

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

    String progressTrace() {
        BlockEntityArcaneAssembler machine = machineView();
        String live = machine == null
                ? "no block entity at " + clientPos
                : "craft=" + machine.isCrafting() + " ticks=" + machine.getCraftTicks()
                        + " prog=" + machine.getCraftProgress();
        int total = Math.max(1, data.get(MenuArcaneAssembler.DATA_TICKS_PER_CRAFT));
        String mirrored = "craft=" + (data.get(MenuArcaneAssembler.DATA_CRAFTING) != 0) + " tick="
                + data.get(MenuArcaneAssembler.DATA_CRAFT_TICK) + "/" + total + " prog="
                + Math.min(1.0F, data.get(MenuArcaneAssembler.DATA_CRAFT_TICK) / (float) total);
        return "live[" + live + "] slot[" + mirrored + "] union[craft=" + isCrafting()
                + " prog=" + getProgress() + "]";
    }

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
