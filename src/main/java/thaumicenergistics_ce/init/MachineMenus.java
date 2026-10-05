package thaumicenergistics_ce.init;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import thaumicenergistics_ce.blockentity.BlockEntityDistillationEncoder;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber;
import thaumicenergistics_ce.menu.MenuArcaneAssembler;
import thaumicenergistics_ce.menu.MenuDistillationEncoder;
import thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench;
import thaumicenergistics_ce.menu.MenuEssentiaVibrationChamber;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;

/**
 * The machine menus, built from the machine that opens them.
 * <ul>
 *   <li>A machine that named its own menu class put {@code blockentity} and {@code menu} in a
 *       mutual pair of packages; the wiring package already knows both sides.
 *   <li>Registration stays in {@link ModMenuTypes}: these build a menu, they do not define one.
 * </ul>
 */
public final class MachineMenus {

    private MachineMenus() {}

    public static AbstractContainerMenu arcaneAssembler(
            int containerId, Inventory inventory, BlockEntityArcaneAssembler assembler) {
        return new MenuArcaneAssembler(containerId, inventory, assembler);
    }

    public static AbstractContainerMenu knowledgeInscriber(
            int containerId, Inventory inventory, BlockEntityKnowledgeInscriber inscriber) {
        return new MenuKnowledgeInscriber(containerId, inventory, inscriber);
    }

    public static AbstractContainerMenu essentiaVibrationChamber(
            int containerId, Inventory inventory, BlockEntityEssentiaVibrationChamber chamber) {
        return new MenuEssentiaVibrationChamber(containerId, inventory, chamber);
    }

    public static AbstractContainerMenu essentiaCellWorkbench(
            int containerId, Inventory inventory, BlockEntityEssentiaCellWorkbench workbench) {
        return new MenuEssentiaCellWorkbench(containerId, inventory, workbench);
    }

    public static AbstractContainerMenu distillationEncoder(
            int containerId, Inventory inventory, BlockEntityDistillationEncoder encoder) {
        return new MenuDistillationEncoder(containerId, inventory, encoder);
    }
}
