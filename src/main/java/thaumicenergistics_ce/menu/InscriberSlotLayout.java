package thaumicenergistics_ce.menu;

import java.util.function.Consumer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.menu.slot.GhostGridSlot;
import thaumicenergistics_ce.menu.slot.MachineGridSlot;
import thaumicenergistics_ce.menu.slot.ReadOnlySlot;

/**
 * 菜单的槽位带和它们的排布几何取自参照容器：井的内部，不是边框。
 * 带的顺序就是菜单的槽位顺序，两侧按索引对应。
 * 添加动作留在菜单里：[addSlot] 是 protected，只有菜单能调。
 */
final class InscriberSlotLayout {

    /** {@code moveItemStackTo} 的一次搬运：填充区间，以及先从哪一端填起。 */
    record Move(int from, int to, boolean reverse) {}

    private static final int FB_CORE_X = 186;
    private static final int FB_CORE_Y = 8;
    private static final int FB_PATTERN_X = 26;
    private static final int FB_PATTERN_Y = 18;
    private static final int FB_CRAFT_X = 26;
    private static final int FB_CRAFT_Y = 90;
    private static final int FB_RESULT_X = 116;
    private static final int FB_RESULT_Y = 108;
    private static final int FB_INV_X = 8;
    private static final int FB_INV_Y = 160;
    private static final int FB_HOTBAR_Y = 218;
    private static final int PITCH = 18;

    /** 核心是机器容器里的 0 号槽。 */
    private static final int MACHINE_CORE = 0;

    private InscriberSlotLayout() {}

    /** 机器槽位读的容器；没有机器时用同样大小的替身。 */
    static Container machine(@Nullable BlockEntityKnowledgeInscriber inscriber) {
        return inscriber == null
                ? new SimpleContainer(BlockEntityKnowledgeInscriber.SLOT_COUNT)
                : inscriber.getInventory();
    }

    /**
     * 按两侧逐索引对应依次加每个槽位。
     * 写入走菜单自己的 {@code addSlot}：它是 protected，在辅助类里够不到，才传进来。
     */
    static void addSlots(
            MenuKnowledgeInscriber menu, Inventory playerInventory, InscriberPreview preview,
            Container machine, @Nullable BlockEntityKnowledgeInscriber inscriber, Consumer<Slot> addSlot) {
        // 1. 玩家物品栏，先三行，再快捷栏。
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot.accept(
                        new Slot(playerInventory, col + row * 9 + 9, FB_INV_X + col * PITCH, FB_INV_Y + row * PITCH));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot.accept(new Slot(playerInventory, col, FB_INV_X + col * PITCH, FB_HOTBAR_Y));
        }

        // 2. 知识核心：唯一会装物品的机器槽位。
        addSlot.accept(new Slot(machine, MACHINE_CORE, FB_CORE_X, FB_CORE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.KNOWLEDGE_CORE.get());
            }
        });

        // 3. 核心存的样板，只读，从核心物品推导，见 [refreshMirrors]。
        for (int i = 0; i < MenuKnowledgeInscriber.PATTERN_SLOTS; i++) {
            addSlot.accept(new ReadOnlySlot(
                    inscriber == null ? preview.mirrors() : machine,
                    inscriber == null ? i : BlockEntityKnowledgeInscriber.MIRROR_SLOT_START + i,
                    FB_PATTERN_X + (i % MenuKnowledgeInscriber.PATTERN_COLS) * PITCH,
                    FB_PATTERN_Y + (i / MenuKnowledgeInscriber.PATTERN_COLS) * PITCH));
        }

        // 4. 待编码的 3x3 配方：客户端上是幽灵网格，服务端上是机器自己的容器。
        // 写入走载荷不走槽位同步，见 [GhostGridSlot]。
        for (int i = 0; i < MenuKnowledgeInscriber.CRAFT_SLOTS; i++) {
            int x = FB_CRAFT_X + (i % 3) * PITCH;
            int y = FB_CRAFT_Y + (i / 3) * PITCH;
            addSlot.accept(inscriber == null
                    ? new GhostGridSlot(
                            machine,
                            BlockEntityKnowledgeInscriber.GRID_SLOT_START + i,
                            x,
                            y,
                            (cell, stack) -> MenuNetwork.sendInscriberGrid(menu.containerId, cell, stack))
                    : new MachineGridSlot(machine, BlockEntityKnowledgeInscriber.GRID_SLOT_START + i, x, y));
        }

        // 5. 结果井。用机器自己的容器，原版才会同步服务端解析出的结果；
        // 客户端自己解析什么都画不出来。只有这口井，不含玩家的输入网格。
        addSlot.accept(new ReadOnlySlot(preview.well(), 0, FB_RESULT_X, FB_RESULT_Y));
    }

    /** 玩家身上有没有匹配的物品堆：JEI 放的是玩家实际有的东西。 */
    static boolean playerHas(MenuKnowledgeInscriber menu, ItemStack wanted) {
        if (wanted.isEmpty()) {
            return false;
        }
        for (int i = 0; i < MenuKnowledgeInscriber.PLAYER_SLOTS; i++) {
            ItemStack stack = menu.slotStack(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, wanted)) {
                return true;
            }
        }
        return false;
    }

    /** {@code index} 上 shift 点击对应的搬运：核心是唯一收物品的机器槽位。 */
    static Move moveFor(int index) {
        if (index < MenuKnowledgeInscriber.PLAYER_SLOTS) {
            return new Move(MenuKnowledgeInscriber.IDX_CORE, MenuKnowledgeInscriber.IDX_CORE + 1, false);
        }
        return new Move(0, MenuKnowledgeInscriber.PLAYER_SLOTS, true);
    }
}
