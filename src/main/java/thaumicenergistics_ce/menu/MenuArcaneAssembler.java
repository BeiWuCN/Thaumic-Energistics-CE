package thaumicenergistics_ce.menu;

import appeng.core.definitions.AEItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.layout.GuiLayout;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.inventory.GearSlots;
import thaumicenergistics_ce.menu.slot.PreviewSlot;
import thaumicenergistics_ce.menu.slot.ReadOnlySlot;

/**
 * 奥术组装机的菜单：样板镜像、知识核心、加速卡、合成预览，
 * 以及 vis 减免在此生效的装备槽。坐标来自 {@link GuiLayout}，
 * 它由与背景纹理相同的常量生成，因此本类不含仅客户端的
 * 资源访问，也没有自己的几何。
 */
public class MenuArcaneAssembler extends AbstractContainerMenu {

    private static final int PLAYER_SLOTS = 36;

    // 槽位索引按注册顺序：玩家、核心、样板、升级、装备。
    // 结果井没有索引：它画在面板图里，而 [Slot] 会始终高亮。
    static final int IDX_CORE = PLAYER_SLOTS;
    private static final int IDX_PATTERN_START = IDX_CORE + 1;
    private static final int IDX_PATTERN_END = IDX_PATTERN_START + BlockEntityArcaneAssembler.PATTERN_SLOT_COUNT;
    private static final int IDX_UPGRADE_START = IDX_PATTERN_END;
    private static final int IDX_UPGRADE_END =
            IDX_UPGRADE_START + BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT;
    private static final int IDX_GEAR_START = IDX_UPGRADE_END;
    private static final int IDX_GEAR_END = IDX_GEAR_START + BlockEntityArcaneAssembler.GEAR_SLOT_COUNT;

    /** 把 vis 池当作一个数字：下面六个按要素划分的槽位所细分的总量。 */
    public static final int DATA_BUFFERED_VIS = 0;
    /** 六个元要素各占一个槽，按 {@code BAR_ASPECTS} 顺序；共用池会让所有条画得一样长。 */
    public static final int DATA_ASPECT_AIR = 1;
    public static final int DATA_ASPECT_WATER = 2;
    public static final int DATA_ASPECT_FIRE = 3;
    public static final int DATA_ASPECT_ORDER = 4;
    public static final int DATA_ASPECT_ENTROPY = 5;
    public static final int DATA_ASPECT_EARTH = 6;
    public static final int DATA_CRAFTING = 7;
    public static final int DATA_CRAFT_TICK = 8;
    public static final int DATA_TICKS_PER_CRAFT = 9;
    public static final int DATA_GEAR_DISCOUNT = 10;
    public static final int DATA_SIZE = 11;

    // 包级可见，供两个协作者使用：它们直接读机器和物品栏。
    final @Nullable BlockEntityArcaneAssembler assembler;

    final Inventory playerInventory;

    private final AssemblerPreviewMirror mirror;

    private final AssemblerMenuReadout readout;

    public MenuArcaneAssembler(int containerId, Inventory playerInventory, BlockEntityArcaneAssembler assembler) {
        // 两侧都从 mod 的资源里读布局，所以它们的槽位坐标不会彼此漂移。
        this(containerId, playerInventory, assembler, assembler.getInventory());
        mirror.refresh();
    }

    public MenuArcaneAssembler(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, null, new SimpleContainer(BlockEntityArcaneAssembler.SLOT_COUNT));
        // 打开数据包的唯一读取者：菜单类型把缓冲区交给这个构造器。
        if (buf.readableBytes() >= Long.BYTES) {
            readout.setClientPos(buf.readBlockPos());
        }
    }

    /** 两侧据此摆放槽位的几何，来自美术生成器写出的文件。 */
    private static GuiLayout layout() {
        return GuiLayout.load();
    }

    private MenuArcaneAssembler(
            int containerId,
            Inventory playerInventory,
            @Nullable BlockEntityArcaneAssembler assembler,
            Container machine) {
        super(ModMenuTypes.ARCANE_ASSEMBLER.get(), containerId);
        this.assembler = assembler;
        this.playerInventory = playerInventory;
        this.mirror = new AssemblerPreviewMirror(this);
        this.readout = new AssemblerMenuReadout(this);

        GuiLayout layout = layout();
        GuiLayout.Anchor inventory = layout.playerInventory();
        GuiLayout.Anchor core = layout.coreSlot();
        GuiLayout.Grid patterns = layout.patternGrid();
        GuiLayout.Grid upgrades = layout.upgradeSlots();
        GuiLayout.Grid gear = layout.armorSlots();
        GuiLayout.Grid preview = layout.previewGrid();
        GuiLayout.Anchor result = layout.previewResult();

        // 1. 玩家物品栏，先三行，再快捷栏。
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        inventory.x() + col * GuiLayout.PITCH, inventory.y() + row * GuiLayout.PITCH));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, inventory.x() + col * GuiLayout.PITCH,
                    layout.hotbar().y()));
        }

        // 2. 知识核心。
        addSlot(new Slot(machine, BlockEntityArcaneAssembler.CORE_SLOT, core.x(), core.y()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.KNOWLEDGE_CORE.get());
            }

            @Override
            public void setChanged() {
                super.setChanged();
                MenuArcaneAssembler.this.mirror.refresh();
            }
        });

        // 3. 样板镜像，仅显示，从核心推导而来 —— 见 [AssemblerPreviewMirror.refresh]。
        for (int i = 0; i < BlockEntityArcaneAssembler.PATTERN_SLOT_COUNT; i++) {
            addSlot(new ReadOnlySlot(
                    assembler == null ? mirror.display() : machine,
                    assembler == null ? i : BlockEntityArcaneAssembler.PATTERN_SLOT_START + i,
                    patterns.slotX(i),
                    patterns.slotY(i)));
        }

        // 4. 加速卡，放在机器自身的槽位里。以前由一个菜单本地容器持有，
        // 关掉菜单就把卡一起带走了；这些随机器保存，所以会回来。
        for (int i = 0; i < BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT; i++) {
            addSlot(new Slot(machine, BlockEntityArcaneAssembler.UPGRADE_SLOT_START + i, upgrades.x(),
                    upgrades.columnY(i)) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return AEItems.SPEED_CARD.is(stack);
                }

                @Override
                public int getMaxStackSize() {
                    // 每个槽一张卡：四个槽、四张卡、四档速度。
                    return 1;
                }
            });
        }

        // 5. vis 减免对本组装机生效的装备槽。
        // 玩家点击走的是 [mayPlace]；方块的容器只能看到自动化。
        for (int i = 0; i < BlockEntityArcaneAssembler.GEAR_SLOT_COUNT; i++) {
            int gearIndex = i;
            addSlot(new Slot(machine, BlockEntityArcaneAssembler.GEAR_SLOT_START + i, gear.x(),
                    gear.columnY(i)) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return GearSlots.accepts(gearIndex, stack);
                }
            });
        }

        // 6. 合成进行中时，合成预览的产物。最后添加，因为 [quickMoveStack] 按
        // [IDX_GEAR_START] 路由。用普通槽，因为方块不发更新标签；偏移 +1，使该井不高亮。
        Slot target = addSlot(new PreviewSlot(
                machine,
                BlockEntityArcaneAssembler.TARGET_SLOT,
                result.x() + 1,
                result.y() + 1));

        // 7. 正在进行的合成的 3x3，显示在预览井中。
        // 像结果井一样最后添加；用 [PreviewSlot]，使这些井在光标下不高亮。
        Slot[] wells = new Slot[BlockEntityArcaneAssembler.PREVIEW_SLOT_COUNT];
        for (int i = 0; i < wells.length; i++) {
            // 这里不像结果井那样偏移 +1：这些井正好落在网格单元上。
            wells[i] = addSlot(new PreviewSlot(
                    machine,
                    BlockEntityArcaneAssembler.PREVIEW_SLOT_START + i,
                    preview.slotX(i),
                    preview.slotY(i)));
        }
        mirror.watch(target, wells);

        // 菜单显示的数字存放在 [AssemblerMenuReadout] 里，它持有这些数字传输所用的表。
        addDataSlots(readout.data());
    }

    public void refreshPatternView() {
        mirror.refresh();
    }

    public ItemStack getPreviewSlot(int index) {
        return mirror.getPreviewSlot(index);
    }

    public ItemStack getPreviewResult() {
        return mirror.getPreviewResult();
    }

    public Slot getUpgradeSlot(int index) {
        int slot = IDX_UPGRADE_START + index;
        if (index < 0 || slot >= IDX_UPGRADE_END || slot >= slots.size()) {
            throw new IndexOutOfBoundsException("upgrade slot " + index);
        }
        return slots.get(slot);
    }

    public int getBufferedVis() {
        return readout.getBufferedVis();
    }

    public int getBarVis(int column) {
        return readout.getBarVis(column);
    }

    public boolean isCrafting() {
        return readout.isCrafting();
    }

    public float getProgress() {
        return readout.getProgress();
    }

    public int getGearDiscount() {
        return readout.getGearDiscount();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < PLAYER_SLOTS) {
            boolean merged = false;

            if (stack.is(ModItems.KNOWLEDGE_CORE.get())) {
                merged = moveItemStackTo(stack, IDX_CORE, IDX_CORE + 1, false);
            }
            if (!merged && AEItems.SPEED_CARD.is(stack)) {
                merged = moveItemStackTo(stack, IDX_UPGRADE_START, IDX_UPGRADE_END, false);
            }
            if (!merged && !GearSlots.isGear(stack)) {
                // 落到常规的物品栏搬移。
            }
            if (!merged) {
                merged = moveItemStackTo(stack, IDX_GEAR_START, IDX_GEAR_END, false);
            }
            if (!merged) {
                if (index < 27) {
                    merged = moveItemStackTo(stack, 27, PLAYER_SLOTS, false);
                } else {
                    merged = moveItemStackTo(stack, 0, 27, false);
                }
            }
            if (!merged) {
                return ItemStack.EMPTY;
            }
        } else {
            // 机器槽位搬回玩家物品栏；只读槽位本身已经拒绝这样做。
            if (!moveItemStackTo(stack, 0, PLAYER_SLOTS, true)) {
                return ItemStack.EMPTY;
            }
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
        if (assembler == null) {
            return true;
        }
        var level = assembler.getLevel();
        var pos = assembler.getBlockPos();
        return level != null
                && level.getBlockEntity(pos) == assembler
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
