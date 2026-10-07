package thaumicenergistics_ce.menu;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;
import thaumicenergistics_ce.network.KnowledgeInscriberReceiver;

/**
 * 知识铭刻机菜单：核心槽、7x3 只读样板网格、玩家 3x3 幽灵网格、结果井。
 * 没有输出槽，样板存在核心那边。
 */
public class MenuKnowledgeInscriber extends AbstractContainerMenu implements KnowledgeInscriberReceiver {

    static final int PATTERN_COLS = 7;
    private static final int PATTERN_ROWS = 3;
    private static final int PATTERN_COUNT = PATTERN_COLS * PATTERN_ROWS;
    private static final int CRAFT_SIZE = 9;

    static final int PLAYER_SLOTS = 36;

    static final int IDX_CORE = PLAYER_SLOTS;

    static final int IDX_PATTERN_START = IDX_CORE + 1;

    static final int IDX_CRAFT_START = IDX_PATTERN_START + PATTERN_COUNT;

    /** 菜单按钮包只带一个 id，删除标志搭在它上面。 */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        runButton(player, id == 1);
        return true;
    }

    public static final int PATTERN_SLOTS = PATTERN_COUNT;
    public static final int CRAFT_SLOTS = CRAFT_SIZE;

    /** 玩家网格某一格的菜单索引；JEI 也指名这些槽位。 */
    public static int gridSlotIndex(int cell) {
        return IDX_CRAFT_START + cell;
    }

    final @Nullable BlockEntityKnowledgeInscriber inscriber;

    final Container machine;

    final Inventory playerInventory;

    private final InscriberGridState grid;

    private final InscriberPreview preview;

    private final InscriberMenuReadout readout;

    public static final int DATA_HAS_CORE = 0;
    public static final int DATA_STATE = 1;

    public MenuKnowledgeInscriber(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, (BlockEntityKnowledgeInscriber) null);
    }

    public MenuKnowledgeInscriber(
            int containerId, Inventory playerInventory, @Nullable BlockEntityKnowledgeInscriber inscriber) {
        super(ModMenuTypes.KNOWLEDGE_INSCRIBER.get(), containerId);
        this.inscriber = inscriber;
        this.playerInventory = playerInventory;

        this.machine = InscriberSlotLayout.machine(inscriber);

        this.grid = new InscriberGridState(this);
        this.preview = new InscriberPreview(this, grid);

        InscriberSlotLayout.addSlots(this, playerInventory, preview, machine, inscriber, this::addSlot);

        // 按钮输入走菜单数据槽上报客户端。解析配方要扫全部配方，重算限流为每 tick 一次。
        this.readout = new InscriberMenuReadout(this, playerInventory);
        addDataSlots(readout.data());
    }

    /**
     * 原版读被点击槽位里的东西，第二次点击会丢掉第一次放的配方。
     * 这里改看携带的物品堆，槽位只是目标。
     */
    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (InscriberGridWrites.route(this, grid, slotId, clickType)) {
            return;
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public int gridSlotStart() {
        return BlockEntityKnowledgeInscriber.GRID_SLOT_START;
    }

    @Override
    public int gridSlotCount() {
        return BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT;
    }

    @Override
    public void setGridCell(Player player, int cell, ItemStack stack) {
        InscriberGridWrites.setCell(this, cell, stack);
    }

    @Override
    public void applyGridFill(Player player, List<ItemStack> cells, int count) {
        InscriberGridWrites.fill(this, cells, count);
    }

    @Override
    public int containerId() {
        return containerId;
    }

    /** 玩家是否携带了匹配的物品堆；JEI 转移的是玩家真有的东西。 */
    public boolean playerHas(ItemStack wanted) {
        return InscriberSlotLayout.playerHas(this, wanted);
    }

    /**
     * 按配方布局填网格，一次写入。
     * 逐格发载荷会让服务端对着半旧的配方重新解析。
     */
    public void fillGridFromRecipe(List<ItemStack> cells) {
        grid.fillFromRecipe(cells);
        preview.update();
    }

    public void updatePreview() {
        preview.update();
    }

    public boolean hasCore() {
        return readout.hasCore();
    }

    /**
     * 从核心填 7x3 井：每帧检查，只在变化时写。
     */
    public void refreshMirrors() {
        preview.refreshMirrors();
    }

    @Nullable HandlerKnowledgeCore handler() {
        return InscriberMachineAccess.handler(this);
    }

    public boolean isGridEmpty() {
        return ThEArcanePattern.isGridEmpty(grid.cells());
    }

    /**
     * 能否编码。客户端读同步的数据槽，自己的槽位副本不保证填好。
     */
    public boolean canEncode() {
        return readout.canEncode();
    }

    public int buttonState() {
        return readout.buttonState();
    }

    /**
     * 为 true 时按钮执行删除。解析不出东西的网格一律是 [Invalid]，与核心存了多少样板无关。
     */
    public boolean isDelete() {
        return readout.isDelete();
    }

    public boolean isActionable() {
        return readout.isActionable();
    }

    @Nullable Level level() {
        return InscriberMachineAccess.level(this);
    }

    ItemStack slotStack(int index) {
        return InscriberMachineAccess.slotStack(this, index);
    }

    /**
     * 在服务端执行按钮；研究检查与物品写入都在这一侧。
     */
    public void runButton(Player player, boolean delete) {
        InscriberButtonAction.run(this, player, delete);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        InscriberSlotLayout.Move move = InscriberSlotLayout.moveFor(index);
        if (move == null || !moveItemStackTo(stack, move.from(), move.to(), move.reverse())) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return InscriberMachineAccess.stillValid(this, player);
    }

    public @Nullable BlockEntityKnowledgeInscriber inscriber() {
        return inscriber;
    }
}
