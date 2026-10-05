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
 * The menu's slot bands and the geometry they are laid out on, taken from the reference container:
 * a well's interior, not its frame.
 * <ul>
 *   <li>Band order is the menu's slot order, which both sides match by index.
 *   <li>The add itself stays in the menu: {@code addSlot} is protected, so only the menu can call it.
 * </ul>
 */
final class InscriberSlotLayout {

    /** A move for {@code moveItemStackTo}: the range to fill and which end of it to fill first. */
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

    /** The core is slot 0 of the machine's own container. */
    private static final int MACHINE_CORE = 0;

    private InscriberSlotLayout() {}

    /** The container the machine slots read, or a stand-in of the same size when there is no machine. */
    static Container machine(@Nullable BlockEntityKnowledgeInscriber inscriber) {
        return inscriber == null
                ? new SimpleContainer(BlockEntityKnowledgeInscriber.SLOT_COUNT)
                : inscriber.getInventory();
    }

    /**
     * Adds every slot in the order the two sides match by index. The writes go through the menu's own
     * {@code addSlot}, handed in because it is protected and cannot be reached from here.
     */
    static void addSlots(
            MenuKnowledgeInscriber menu, Inventory playerInventory, InscriberPreview preview,
            Container machine, @Nullable BlockEntityKnowledgeInscriber inscriber, Consumer<Slot> addSlot) {
        // 1. Player inventory, three rows then the hotbar.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot.accept(
                        new Slot(playerInventory, col + row * 9 + 9, FB_INV_X + col * PITCH, FB_INV_Y + row * PITCH));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot.accept(new Slot(playerInventory, col, FB_INV_X + col * PITCH, FB_HOTBAR_Y));
        }

        // 2. Knowledge core. The only machine slot that holds an item.
        addSlot.accept(new Slot(machine, MACHINE_CORE, FB_CORE_X, FB_CORE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.KNOWLEDGE_CORE.get());
            }
        });

        // 3. The core's stored patterns, read-only, derived from the core item - see refreshMirrors.
        for (int i = 0; i < MenuKnowledgeInscriber.PATTERN_SLOTS; i++) {
            addSlot.accept(new ReadOnlySlot(
                    inscriber == null ? preview.mirrors() : machine,
                    inscriber == null ? i : BlockEntityKnowledgeInscriber.MIRROR_SLOT_START + i,
                    FB_PATTERN_X + (i % MenuKnowledgeInscriber.PATTERN_COLS) * PITCH,
                    FB_PATTERN_Y + (i / MenuKnowledgeInscriber.PATTERN_COLS) * PITCH));
        }

        // 4. The 3x3 recipe to encode: a ghost grid on the client, the machine's own container on the
        // server. See GhostGridSlot for why the write is a payload rather than a slot sync.
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

        // 5. The result well. The machine's own container, so vanilla syncs what the server resolved;
        // the client resolving for itself drew nothing. Only this well, not the player's input grid.
        addSlot.accept(new ReadOnlySlot(preview.well(), 0, FB_RESULT_X, FB_RESULT_Y));
    }

    /** Whether the player carries a stack that matches: JEI places what the player actually has. */
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

    /** The move a shift-click on {@code index} means: the core is the only machine slot taking items. */
    static Move moveFor(int index) {
        if (index < MenuKnowledgeInscriber.PLAYER_SLOTS) {
            return new Move(MenuKnowledgeInscriber.IDX_CORE, MenuKnowledgeInscriber.IDX_CORE + 1, false);
        }
        return new Move(0, MenuKnowledgeInscriber.PLAYER_SLOTS, true);
    }
}
