package thaumicenergistics_ce.menu;

import appeng.core.definitions.AEItems;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityDistillationEncoder;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.menu.slot.AspectSelectSlot;
import thaumicenergistics_ce.menu.slot.MachineOutputSlot;
import thaumicenergistics_ce.menu.slot.TemplateSlot;
import thaumicenergistics_ce.network.DistillationEncoderReceiver;

/**
 * 蒸馏编码器的菜单：物品、它的要素、被选中的要素以及样板井。
 * 两侧都从同步的物品推导要素行，因此二者不会不一致。选中项
 * 不做同步：它是对服务端的指令，回映回来只为绘制高亮。
 */
public class MenuDistillationEncoder extends AbstractContainerMenu implements DistillationEncoderReceiver {

    public static final int PLAYER_SLOTS = 36;

    public static final int IDX_SOURCE = 0;

    public static final int IDX_BLANK = 1;

    public static final int IDX_ENCODED = 2;

    public static final int IDX_ASPECT_START = 3;

    public static final int ASPECT_SLOTS = BlockEntityDistillationEncoder.MAX_ASPECTS;

    public static final int IDX_SELECTED = IDX_ASPECT_START + ASPECT_SLOTS;

    /** 该行第一个井的菜单索引：比它的容器索引多 {@link #PLAYER_SLOTS}；点击与
     * {@code quickMoveStack} 对槽位的编号方式不同。 */
    public static final int MENU_ASPECT_START = PLAYER_SLOTS + IDX_ASPECT_START;
    public static final int MENU_SELECTED = PLAYER_SLOTS + IDX_SELECTED;

    public static final int MENU_SOURCE = PLAYER_SLOTS + IDX_SOURCE;

    public static final int MENU_BLANK = PLAYER_SLOTS + IDX_BLANK;
    public static final int MENU_ENCODED = PLAYER_SLOTS + IDX_ENCODED;

    // 取自参照构建的屏幕美术。
    private static final int SOURCE_X = 15;
    private static final int SOURCE_Y = 69;
    private static final int ASPECTS_X = 65;
    private static final int ASPECTS_Y = 24;
    private static final int ASPECT_PITCH = 18;
    private static final int SELECTED_X = 116;
    private static final int SELECTED_Y = 69;
    private static final int BLANK_X = 146;
    private static final int BLANK_Y = 75;
    private static final int ENCODED_X = 146;
    private static final int ENCODED_Y = 113;

    private static final int INV_X = 8;
    private static final int INV_Y = 150;
    private static final int HOTBAR_Y = 208;
    private static final int PITCH = 18;

    // 包级可见，供要素表使用：它从槽位和玩家推导该行。
    final Player owner;

    final @Nullable BlockEntityDistillationEncoder encoder;

    private final SimpleContainer aspectDisplay = new SimpleContainer(ASPECT_SLOTS);

    private final SimpleContainer selectedDisplay = new SimpleContainer(1);

    final EncoderAspectTable table;

    public MenuDistillationEncoder(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, (BlockEntityDistillationEncoder) null);
    }

    public MenuDistillationEncoder(
            int containerId, Inventory playerInventory, @Nullable BlockEntityDistillationEncoder encoder) {
        super(ModMenuTypes.DISTILLATION_ENCODER.get(), containerId);
        this.encoder = encoder;
        this.owner = playerInventory.player;
        this.table = new EncoderAspectTable(this, encoder, aspectDisplay, selectedDisplay);
        Container source = encoder == null ? new SimpleContainer(BlockEntityDistillationEncoder.SLOT_COUNT) : encoder.getInventory();

        // 1. 玩家物品栏，和本 mod 其它地方一样放在最前。
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, INV_X + column * PITCH, INV_Y + row * PITCH));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, INV_X + column * PITCH, HOTBAR_Y));
        }

        // 2. 机器。源井是模板，不是存放处 —— 见 [TemplateSlot]。
        addSlot(new TemplateSlot(source, BlockEntityDistillationEncoder.SLOT_SOURCE, SOURCE_X, SOURCE_Y));
        addSlot(new Slot(source, BlockEntityDistillationEncoder.SLOT_BLANK, BLANK_X, BLANK_Y));
        // 由机器写入，由玩家取走，永不放入 —— 见 [MachineOutputSlot]。
        addSlot(new MachineOutputSlot(source, BlockEntityDistillationEncoder.SLOT_ENCODED, ENCODED_X, ENCODED_Y));

        // 3. 要素行与选中的要素：由要素表写入的视图，玩家永远写不了。
        for (int i = 0; i < ASPECT_SLOTS; i++) {
            // 沿面板纵向排列，而非横向。
            addSlot(new AspectSelectSlot(
                    aspectDisplay,
                    i,
                    ASPECTS_X,
                    ASPECTS_Y + i * ASPECT_PITCH,
                    i,
                    this::aspectCount,
                    this::localSelection));
        }
        addSlot(new AspectSelectSlot(
                selectedDisplay, 0, SELECTED_X, SELECTED_Y, -1, this::aspectCount, this::localSelection));

        table.refresh();
    }

    public void ensureAspects() {
        table.ensure();
    }

    public boolean sourceRevealsNothing() {
        return table.revealsNothing();
    }

    public int aspectAmountFor(int index) {
        return table.amountFor(index);
    }

    public boolean isAspectRevealed(int index) {
        return table.isRevealed(index);
    }

    public int revealedAspectCount() {
        return table.revealedCount();
    }

    public @Nullable Holder<IAspect> pickedAspect() {
        return table.pickedAspect();
    }

    public int pickedAmount() {
        return table.pickedAmount();
    }

    public List<Holder<IAspect>> aspects() {
        return table.aspects();
    }

    public int aspectCount() {
        return table.aspectCount();
    }

    public int localSelection() {
        return table.localSelection();
    }

    @Override
    public void selectAspect(int index) { EncoderActions.selectAspect(this, index); }

    @Override
    public void encode() { EncoderActions.encode(this); }

    @Override
    public void applySourceTemplate(ItemStack stack) { EncoderActions.applySourceTemplate(this, stack); }

    public void requestSourceTemplate(ItemStack stack) { EncoderActions.requestSourceTemplate(this, stack); }

    @Override
    public void insertBlankFromInventory(Player player) { EncoderActions.insertBlank(this, player); }

    @Override
    public int containerId() { return containerId; }

    public boolean canEncode() { return EncoderActions.canEncode(this); }

    public void sendAction(int action, int value) {
        MenuNetwork.sendEncoderAction(containerId, action, value);
    }

    @Override
    public void broadcastChanges() {
        // 只在服务端运行 —— 客户端从不调用它；由 [ensureAspects] 保持其副本最新，
        // 而不是在这里。
        table.ensure();
        super.broadcastChanges();
    }

    /** 拦截对要素行和选中要素显示区的点击：点击其一意为「用这个
     * 要素」，而落到原版处理会让玩家从显示区里拽出一个幻影物品。 */
    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (EncoderClicks.handles(this, slotId, dragType, clickType, player)) {
            return;
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    private static final int MACHINE_START = PLAYER_SLOTS;
    private static final int MACHINE_END = PLAYER_SLOTS + 3;

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index >= MACHINE_START && index < MACHINE_END) {
            if (!moveItemStackTo(stack, 0, PLAYER_SLOTS, true)) {
                return ItemStack.EMPTY;
            }
        } else if (AEItems.BLANK_PATTERN.is(stack)) {
            if (!moveItemStackTo(stack, PLAYER_SLOTS + IDX_BLANK, PLAYER_SLOTS + IDX_BLANK + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!moveItemStackTo(stack, PLAYER_SLOTS + IDX_SOURCE, PLAYER_SLOTS + IDX_SOURCE + 1, false)) {
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
        if (encoder == null) {
            return true;
        }
        var level = encoder.getLevel();
        var pos = encoder.getBlockPos();
        return level != null
                && level.getBlockEntity(pos) == encoder
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
