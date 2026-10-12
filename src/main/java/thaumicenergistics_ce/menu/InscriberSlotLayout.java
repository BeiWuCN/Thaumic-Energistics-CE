package thaumicenergistics_ce.menu;

import java.util.function.Consumer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.registry.TECEItems;
import thaumicenergistics_ce.menu.slot.GhostGridSlot;
import thaumicenergistics_ce.menu.slot.MachineGridSlot;
import thaumicenergistics_ce.menu.slot.ReadOnlySlot;

final class InscriberSlotLayout {

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

    private static final int MACHINE_CORE = 0;

    private InscriberSlotLayout() {}

    static Container machine(@Nullable BlockEntityKnowledgeInscriber inscriber) {
        return inscriber == null
                ? new SimpleContainer(BlockEntityKnowledgeInscriber.SLOT_COUNT)
                : inscriber.getInventory();
    }

    static void addSlots(
            MenuKnowledgeInscriber menu, Inventory playerInventory, InscriberPreview preview,
            Container machine, @Nullable BlockEntityKnowledgeInscriber inscriber, Consumer<Slot> addSlot) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot.accept(
                        new Slot(playerInventory, col + row * 9 + 9, FB_INV_X + col * PITCH, FB_INV_Y + row * PITCH));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot.accept(new Slot(playerInventory, col, FB_INV_X + col * PITCH, FB_HOTBAR_Y));
        }

        addSlot.accept(new Slot(machine, MACHINE_CORE, FB_CORE_X, FB_CORE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(TECEItems.KNOWLEDGE_CORE.get());
            }
        });

        for (int i = 0; i < MenuKnowledgeInscriber.PATTERN_SLOTS; i++) {
            addSlot.accept(new ReadOnlySlot(
                    inscriber == null ? preview.mirrors() : machine,
                    inscriber == null ? i : BlockEntityKnowledgeInscriber.MIRROR_SLOT_START + i,
                    FB_PATTERN_X + (i % MenuKnowledgeInscriber.PATTERN_COLS) * PITCH,
                    FB_PATTERN_Y + (i / MenuKnowledgeInscriber.PATTERN_COLS) * PITCH));
        }

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

        addSlot.accept(new ReadOnlySlot(preview.well(), 0, FB_RESULT_X, FB_RESULT_Y));
    }

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

    static Move moveFor(int index) {
        if (index < MenuKnowledgeInscriber.PLAYER_SLOTS) {
            return new Move(MenuKnowledgeInscriber.IDX_CORE, MenuKnowledgeInscriber.IDX_CORE + 1, false);
        }
        return new Move(0, MenuKnowledgeInscriber.PLAYER_SLOTS, true);
    }
}
