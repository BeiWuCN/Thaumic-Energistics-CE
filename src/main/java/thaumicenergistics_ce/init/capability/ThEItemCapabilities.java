package thaumicenergistics_ce.init.capability;

import net.minecraft.core.Direction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.block.BlockArcaneAssembler;
import thaumicenergistics_ce.blockentity.BlockEntityDistillationEncoder;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.init.ModBlockEntities;

/**
 * 本 mod 的机器作为物品处理器呈现，供漏斗或管道查看。每台机器在内部都是一个
 * 扁平容器，下面的各个区段都是它的索引，而 JEI 拖进来的内容
 * 以及机器自己写入的内容被排除在每个区段之外。奥术组装机只在
 * 自身朝向上作答，因此成排的组装机不会从正面互相取料；而概率之箱只用
 * 大脑槽位作答，那是一个自成一体的容器，而不是一个区段。
 */
public final class ThEItemCapabilities {

    /** 处理器类型，只写一次：机器自身的查询用的是同一个。 */
    private static final BlockCapability<IItemHandler, @Nullable Direction> ITEM = Capabilities.ItemHandler.BLOCK;

    private ThEItemCapabilities() {}

    /** 由 mod 构造函数中的能力监听器调用。 */
    public static void install(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(ITEM, ModBlockEntities.DISTILLATION_ENCODER.get(), (machine, side) -> encoder(machine));
        event.registerBlockEntity(ITEM, ModBlockEntities.KNOWLEDGE_INSCRIBER.get(),
                (machine, side) -> inscriber(machine));
        event.registerBlockEntity(ITEM, ModBlockEntities.ESSENTIA_CELL_WORKBENCH.get(), (machine, side) -> workbench(machine));
        event.registerBlockEntity(ITEM, ModBlockEntities.OCCULT_MONITOR.get(), (machine, side) -> monitor(machine.getInventory()));
        event.registerBlockEntity(ITEM, ModBlockEntities.ARCANE_ASSEMBLER.get(), (machine, side) -> assembler(machine, side));
        event.registerBlockEntity(ITEM, ModBlockEntities.GACHA_BOX.get(),
                (machine, side) -> brain(machine.brainSlot()));
    }

    /** 只包含样板井：来源井指名的物品 JEI 从不会交出，管道若够得到它
     * 就等于凭空造出一个。管道能拿回的只有破坏方块时掉落的东西，不会更多。 */
    static SlotRangeItemHandler encoder(BlockEntityDistillationEncoder machine) {
        return new SlotRangeItemHandler(machine.getInventory(),
                BlockEntityDistillationEncoder.SLOT_BLANK,
                BlockEntityDistillationEncoder.SLOT_COUNT - BlockEntityDistillationEncoder.SLOT_BLANK);
    }

    /** 只包含核心。它旁边的各个井只是镜像核心所存的内容，而网格是
     * 玩家的草稿区，因此管道够到其中任何一个都会交出副本或弄丢配方。 */
    static SlotRangeItemHandler inscriber(BlockEntityKnowledgeInscriber machine) {
        return new SlotRangeItemHandler(
                machine.getInventory(), BlockEntityKnowledgeInscriber.CORE_SLOT, 1);
    }

    private static SlotRangeItemHandler workbench(BlockEntityEssentiaCellWorkbench machine) {
        return new SlotRangeItemHandler(machine.getInventory(), BlockEntityEssentiaCellWorkbench.CELL_SLOT, 1);
    }

    private static SlotRangeItemHandler monitor(SimpleContainer bookSlot) {
        return new SlotRangeItemHandler(bookSlot, BlockEntityOccultMonitor.BOOK_SLOT, 1);
    }

    /** 只包含大脑槽位，且仅可输入：管道可以填充它，绝不能把它清空。 */
    private static SlotRangeItemHandler brain(SimpleContainer brainSlot) {
        return SlotRangeItemHandler.inputOnly(brainSlot, 0, 1);
    }

    static SlotRangeItemHandler assembler(BlockEntityArcaneAssembler machine, @Nullable Direction side) {
        if (side != null && !isFront(machine, side)) {
            return null;
        }
        // 一个四十槽的容器，但机器自己写入镜像、目标与预览这三个区段，
        // 其中存放的是副本，因此管道只能够到破坏方块时会掉落的那些槽位。
        return new SlotRangeItemHandler(machine.getInventory(), 0, BlockEntityArcaneAssembler.SLOT_COUNT,
                BlockEntityArcaneAssembler::isPlayerOwned);
    }

    /** 朝向是方块自身的属性；机器读的就是它，而不带面的查询被放行，
     * 因为工作台上或线缆自身查询中的机器问的是方块本身。 */
    private static boolean isFront(BlockEntityArcaneAssembler machine, Direction side) {
        BlockState state = machine.getBlockState();
        DirectionProperty facing = BlockArcaneAssembler.FACING;
        return !state.hasProperty(facing) || state.getValue(facing) == side;
    }
}
