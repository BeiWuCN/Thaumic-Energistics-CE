package com.beiwu.thaumicenergistics_ce.menu;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.ContainerData;
import com.beiwu.thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;

final class AssemblerMenuReadout {

    private final MenuArcaneAssembler menu;
    private final ContainerData data;

    AssemblerMenuReadout(MenuArcaneAssembler menu) {
        this.menu = menu;
        this.data = new ArcaneAssemblerReadings(menu.assembler, this::aspectForSlot);
    }

    ContainerData data() {
        return data;
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
        return menu.assembler.getAspectVis(BAR_ASPECTS[column]);
    }

    int getBarVis(int column) {
        if (column < 0 || column >= BAR_ASPECTS.length) {
            return 0;
        }
        return data.get(MenuArcaneAssembler.DATA_ASPECT_AIR + column);
    }

    boolean isCrafting() {
        return data.get(MenuArcaneAssembler.DATA_CRAFTING) != 0;
    }

    float getProgress() {
        int total = Math.max(1, data.get(MenuArcaneAssembler.DATA_TICKS_PER_CRAFT));
        float mirrored = Math.min(1.0F, data.get(MenuArcaneAssembler.DATA_CRAFT_TICK) / (float) total);
        return isCrafting() ? mirrored : 0.0F;
    }

    int getGearDiscount() {
        return data.get(MenuArcaneAssembler.DATA_GEAR_DISCOUNT);
    }
}
