package thaumicenergistics_ce.client.gui;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
import appeng.client.gui.style.ScreenStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics_ce.essentia.EssentiaFillHelper;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.menu.MenuEssentiaTerminalBase;
import thaumicenergistics_ce.menu.slot.ContainerSlot;
import thaumicenergistics_ce.network.EssentiaDepositPayload;
import thaumicenergistics_ce.network.EssentiaFillPayload;
import thaumicenergistics_ce.util.ThELog;

/**
 * 凡是带罐与药瓶手势的终端界面都用的这一套，由需要它的两个界面继承而不是复制，
 * 因为这些手势要读 [hoveredSlot]，而任何辅助类都看不到它。
 * 光标下的那一行说明从哪里取，容器本身说明往哪个方向：空容器
 * 从该行装入，满容器既能从该行、也能从空存储元件倒空进网络。
 * shift 保持 AE2 的含义——两种点击都作用于整叠手持物品，而在
 * 玩家自己的槽位上 shift 右键则在容器所在处操作。这里不绘制任何东西。
 */
public abstract class ScreenEssentiaTerminalBase<M extends MenuEssentiaTerminalBase>
        extends MEStorageScreen<M> {

    /** 两个界面都用这个日志标签输出，用到它的手势在两边是同一份代码。 */
    protected static final String TAG = "[essentia-terminal] ";

    protected ScreenEssentiaTerminalBase(
            M menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    /** 本界面是否提供罐与药瓶手势；只有装了卡的终端才提供。 */
    protected abstract boolean essentiaGesturesAtAll();

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!essentiaGesturesAtAll()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 1 && handleRightClick()) {
            return true;
        }
        if (button == 0 && handleLeftClick()) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        if (essentiaGesturesAtAll() && slot instanceof RepoSlot repoSlot && cursorIsContainer()) {
            var entry = repoSlot.getEntry();
            if (entry != null && entry.getWhat() instanceof AEssentiaKey) {
                // 光标上有容器且属于我们：手势是唯一的入口，所以 AE2 自己的槽位点击
                // 不运行。静默处理——光标每经过一格它就会触发一次。
                return;
            }
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    private boolean handleRightClick() {
        // 在玩家槽位上 shift 右键：在该容器所在处把它倒空。
        if (hasShiftDown() && hoveredSlot != null && menu.isPlayerSideSlot(hoveredSlot)) {
            ItemStack inSlot = hoveredSlot.getItem();
            if (EssentiaFillHelper.isSupportedContainer(inSlot)) {
                // 用菜单的槽位 id，而不是物品栏下标：AE2 把视图元件和升级槽排在玩家槽位
                // 之前，而服务端是按自己的槽位表解析这个 id 的。
                log("deposit requested: {} from menu slot {}", inSlot.getHoverName().getString(),
                        menu.slots.indexOf(hoveredSlot));
                PacketDistributor.sendToServer(new EssentiaDepositPayload(
                        menu.containerId, menu.slots.indexOf(hoveredSlot)));
                return true;
            }
            return false;
        }

        ItemStack container = heldContainer();
        if (container == null) {
            return false;
        }
        AEssentiaKey overKey = null;
        if (hoveredSlot instanceof RepoSlot repoSlot) {
            var entry = repoSlot.getEntry();
            if (entry != null && entry.getWhat() instanceof AEssentiaKey key) {
                overKey = key;
            }
        }
        if (overKey != null && EssentiaFillHelper.isContainerEmpty(container)) {
            // 在有可给予之物的行上，空容器就是一次装入，两个键都是：方向由容器决定，
            // 所以按键只在单个物品和整叠之间做选择。
            log("fill requested: {} into the {} of {} from {}", overKey.getId(),
                    container.getHoverName().getString(),
                    heldName(), hasShiftDown() ? "the whole held stack" : "one item");
            PacketDistributor.sendToServer(new EssentiaFillPayload(
                    menu.containerId, overKey.getId(), whereHeld(), container, hasShiftDown()));
            return true;
        }
        if (!(hoveredSlot instanceof RepoSlot)) {
            // 根本不是网格里的某一格：无论手上拿着什么，这次点击都归 AE2。
            return false;
        }
        // 满容器从一行倒空进网络，与从什么都不装的存储元件上倒空完全相同：
        // 从哪儿取由该行决定，往哪个方向由容器决定。
        log("deposit requested: {} from {}, over a repo cell", container.getHoverName().getString(),
                heldName());
        PacketDistributor.sendToServer(new EssentiaDepositPayload(
                menu.containerId, whereHeld()));
        return true;
    }

    private boolean handleLeftClick() {
        ItemStack container = heldContainer();
        if (container == null || !(hoveredSlot instanceof RepoSlot repoSlot)) {
            return false;
        }
        var entry = repoSlot.getEntry();
        boolean overEssentia = entry != null && entry.getWhat() instanceof AEssentiaKey;
        if (EssentiaFillHelper.isContainerEmpty(container)) {
            if (!overEssentia) {
                // 空容器且无物可取：另一种类型的条目就是一次普通物品点击，
                // 而什么都不装的存储元件没有可取之处。
                return false;
            }
            AEssentiaKey key = (AEssentiaKey) entry.getWhat();
            // 按 shift 会把同一次点击变成“整叠手持物品”：按网络付得起的量装满，
            // 付不起的那些留在原处。
            log("fill requested: {} into the {} of {} from {}", key.getId(),
                    container.getHoverName().getString(),
                    heldName(), hasShiftDown() ? "the whole held stack" : "one item");
            PacketDistributor.sendToServer(new EssentiaFillPayload(
                    menu.containerId, key.getId(), whereHeld(), container, hasShiftDown()));
            return true;
        }
        // 满容器从一行倒空进网络，与从什么都不装的存储元件上倒空完全相同：
        // 方向由容器决定，所以不询问光标下的那一行。
        log("deposit requested: {} from {}, over a repo cell", container.getHoverName().getString(),
                heldName());
        PacketDistributor.sendToServer(new EssentiaDepositPayload(menu.containerId, whereHeld()));
        return true;
    }

    private static void log(String message, Object... args) {
        ThELog.LOG.info(TAG + message, args);
    }

    private boolean cursorIsContainer() {
        ItemStack carried = menu.getCarried();
        return !carried.isEmpty() && EssentiaFillHelper.isSupportedContainer(carried);
    }

    private ItemStack heldContainer() {
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            return EssentiaFillHelper.isSupportedContainer(carried) ? carried : null;
        }
        ItemStack mainHand = menu.getPlayerInventory().player.getMainHandItem();
        return EssentiaFillHelper.isSupportedContainer(mainHand) ? mainHand : null;
    }

    private int whereHeld() {
        return menu.getCarried().isEmpty() ? ContainerSlot.MAIN_HAND : ContainerSlot.CURSOR;
    }

    /** {@link #whereHeld()} 所指的位置，用文字表述，供日志行使用。 */
    private String heldName() {
        return menu.getCarried().isEmpty() ? "the main hand" : "the cursor";
    }
}
