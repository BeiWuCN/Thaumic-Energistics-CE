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
 * 知识铭刻机的菜单：核心槽、7x3 的只读样板网格、玩家的
 * 3x3 幽灵网格以及结果井。
 * 没有输出槽，因为核心就是样板存储；见
 * {@code BlockEntityKnowledgeInscriber}。
 */
public class MenuKnowledgeInscriber extends AbstractContainerMenu implements KnowledgeInscriberReceiver {

    /** 包级可见，供布局使用：它把井索引转成列与行。 */
    static final int PATTERN_COLS = 7;
    private static final int PATTERN_ROWS = 3;
    private static final int PATTERN_COUNT = PATTERN_COLS * PATTERN_ROWS;
    private static final int CRAFT_SIZE = 9;

    /** 包级可见，供布局使用：其玩家带是菜单槽位中的第一段。 */
    static final int PLAYER_SLOTS = 36;

    /** 包级可见，供读数与预览使用：二者都会问核心槽里放着什么。 */
    static final int IDX_CORE = PLAYER_SLOTS;

    /** 包级可见，供点击路由使用：它从槽 id 读出井的索引。 */
    static final int IDX_PATTERN_START = IDX_CORE + 1;

    /** 包级可见，供网格状态使用：它观察槽位的窗口就是 3x3 配方网格。 */
    static final int IDX_CRAFT_START = IDX_PATTERN_START + PATTERN_COUNT;

    /** 菜单按钮数据包只带一个 id，所以删除标志搭在它上面。 */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        runButton(player, id == 1);
        return true;
    }

    public static final int PATTERN_SLOTS = PATTERN_COUNT;
    public static final int CRAFT_SLOTS = CRAFT_SIZE;

    /** 玩家网格中某一格的菜单索引；设为 public 是因为 JEI 也会指名这些槽位。 */
    public static int gridSlotIndex(int cell) {
        return IDX_CRAFT_START + cell;
    }

    /** 包级可见，供读数使用：其核心检查在机器存在的那一侧运行。 */
    final @Nullable BlockEntityKnowledgeInscriber inscriber;

    /** 包级可见，供网格状态使用：它会一次性把整个配方写入其中。 */
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

        // 6. 按钮的输入，通过菜单的数据槽上报给客户端。解析一个
        // 配方要扫描管理器中的每一个配方，所以这里限流为每 tick 重算一次。
        this.readout = new InscriberMenuReadout(this, playerInventory);
        addDataSlots(readout.data());
    }

    /**
     * 原版读取被点击槽位里的东西，这会在第二次点击时丢掉第一次
     * 放入的配方；这里由携带的物品堆下指令，而槽位只是目标。
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

    /** 玩家是否携带了匹配的物品堆：JEI 放的是玩家实际拥有的东西。 */
    public boolean playerHas(ItemStack wanted) {
        return InscriberSlotLayout.playerHas(this, wanted);
    }

    /**
     * 按配方布局填充网格，就像 JEI 转移和点击样板那样，一次写入完成：
     * 每格一个载荷会让服务端对着一个还留着一半旧配方的网格重新解析。
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
     * 从核心填充 7x3 的井，每帧检查但只在变化时写：它们是只读槽位，
     * 所以只有绘制它们的那一侧才能写入它们显示的内容。
     */
    public void refreshMirrors() {
        preview.refreshMirrors();
    }

    /** 包级可见，供网格状态与预览使用：它们的读取都从核心物品开始。 */
    @Nullable HandlerKnowledgeCore handler() {
        return InscriberMachineAccess.handler(this);
    }

    public boolean isGridEmpty() {
        return ThEArcanePattern.isGridEmpty(grid.cells());
    }

    /**
     * 不管配方是什么，这个菜单究竟能否编码。客户端读取的是同步的数据槽，
     * 因为它自己的槽位副本并不可靠地被填充。
     */
    public boolean canEncode() {
        return readout.canEncode();
    }

    public int buttonState() {
        return readout.buttonState();
    }

    /**
     * 当按钮会执行删除而非存储时为 true，这不是玩家选定的模式：解析不出
     * 任何东西的网格就是 [Invalid]，无论核心存有多少样板。
     */
    public boolean isDelete() {
        return readout.isDelete();
    }

    public boolean isActionable() {
        return readout.isActionable();
    }

    /** 包级可见，供网格状态与预览使用：它们针对同一个 level 解析。 */
    @Nullable Level level() {
        return InscriberMachineAccess.level(this);
    }

    /** 包级可见，供三个协作者使用：它们的读取全都是槽位读取。 */
    ItemStack slotStack(int index) {
        return InscriberMachineAccess.slotStack(this, index);
    }

    /**
     * 在服务端运行该按钮，由菜单按钮数据包触发，使研究检查与物品
     * 写入发生在可信的地方。
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
