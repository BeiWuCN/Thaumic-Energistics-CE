package thaumicenergistics_ce.init.capability;

import net.minecraft.core.Direction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.block.BlockArcaneAssembler;
import thaumicenergistics_ce.blockentity.BlockEntityDistillationEncoder;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.init.ModBlockEntities;

/**
 * 本 mod 的机器以物品处理器身份露面，供漏斗或管道查看。
 * 每台机器内部是一个扁平容器，下面各区段都是它的索引；JEI 拖进来的内容
 * 和机器自己写入的内容被排除在每个区段之外。
 * 奥术组装机只在自身朝向上作答，成排的组装机不会从正面互相取料；
 * 概率之箱只用大脑槽位作答，那是自成一体的容器，不算区段。
 */
public final class ThEItemCapabilities {

    /** 处理器类型，只写一处：机器自己的查询用的是同一个。 */
    private static final BlockCapability<ResourceHandler<ItemResource>, @Nullable Direction> ITEM =
            Capabilities.Item.BLOCK;

    private ThEItemCapabilities() {}

    /** 由 mod 构造函数里的能力监听器调用。 */
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

    /** 只含样板井：来源井指名的物品 JEI 从不交出，管道够得到就等于凭空造物。
     * 管道能拿回的只有破坏方块时掉落的东西，不会更多。 */
    static SlotRangeItemHandler encoder(BlockEntityDistillationEncoder machine) {
        return new SlotRangeItemHandler(machine.getInventory(),
                BlockEntityDistillationEncoder.SLOT_BLANK,
                BlockEntityDistillationEncoder.SLOT_COUNT - BlockEntityDistillationEncoder.SLOT_BLANK);
    }

    /** 只含核心。旁边的井只是镜像核心内容，网格是玩家的草稿区，
     * 管道够到任何一个都会交出副本或弄丢配方。 */
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

    /** 只含大脑槽位，且只可输入：管道能填它，清空不了。 */
    private static SlotRangeItemHandler brain(SimpleContainer brainSlot) {
        return SlotRangeItemHandler.inputOnly(brainSlot, 0, 1);
    }

    static SlotRangeItemHandler assembler(BlockEntityArcaneAssembler machine, @Nullable Direction side) {
        if (side != null && !isFront(machine, side)) {
            return null;
        }
        // 一个四十槽的容器，机器自己写镜像、目标、预览三个区段，装的是副本。
        // 管道只够得到破坏方块时会掉落的那些槽位。
        return new SlotRangeItemHandler(machine.getInventory(), 0, BlockEntityArcaneAssembler.SLOT_COUNT,
                BlockEntityArcaneAssembler::isPlayerOwned);
    }

    /** 朝向是方块自身的属性；不带面的查询放行：
     * 工作台上或线缆查询里的机器问的就是方块本身。 */
    private static boolean isFront(BlockEntityArcaneAssembler machine, Direction side) {
        BlockState state = machine.getBlockState();
        EnumProperty<Direction> facing = BlockArcaneAssembler.FACING;
        return !state.hasProperty(facing) || state.getValue(facing) == side;
    }
}
