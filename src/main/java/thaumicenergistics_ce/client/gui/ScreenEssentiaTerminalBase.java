package thaumicenergistics_ce.client.gui;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
import appeng.client.gui.style.ScreenStyle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.essentia.EssentiaFillHelper;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.menu.MenuEssentiaTerminalBase;
import thaumicenergistics_ce.menu.slot.ContainerSlot;
import thaumicenergistics_ce.network.EssentiaDepositPayload;
import thaumicenergistics_ce.network.EssentiaFillPayload;
import thaumicenergistics_ce.util.ThELog;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 两个带罐与药瓶手势的终端界面共用这一套。手势要读 [hoveredSlot]，
 * 辅助类看不到它。
 * 光标下的那一行说从哪取，容器说往哪去：空容器从该行装入；
 * 满容器能从该行装入，也能从空存储元件倒空进网络。
 * shift 保持 AE2 的含义：两种点击都对整叠手持物品生效；
 * 玩家自己的槽位上 shift 右键则在容器所在处动手。这里不画东西。
 */
public abstract class ScreenEssentiaTerminalBase<M extends MenuEssentiaTerminalBase>
        extends MEStorageScreen<M> {

    /** 两个界面共用这个日志标签，手势代码只有一份。 */
    protected static final String TAG = "[essentia-terminal] ";

    protected ScreenEssentiaTerminalBase(
            M menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    /** 本界面有没有罐与药瓶手势；装了卡的终端才有。 */
    protected abstract boolean essentiaGesturesAtAll();

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!essentiaGesturesAtAll()) {
            return super.mouseClicked(event, doubleClick);
        }
        boolean shift = event.hasShiftDown();
        if (event.button() == 1 && handleRightClick(shift)) {
            return true;
        }
        if (event.button() == 0 && handleLeftClick(shift)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ContainerInput type) {
        if (essentiaGesturesAtAll() && slot instanceof RepoSlot repoSlot && cursorIsContainer()) {
            var entry = repoSlot.getEntry();
            if (entry != null && entry.getWhat() instanceof AEssentiaKey) {
                // 光标上是我们的容器时就到这里：手势是唯一入口，AE2 自己的槽位点击不跑。
                // 静默处理，光标每过一格就触发一次。
                return;
            }
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    private boolean handleRightClick(boolean shift) {
        // 玩家槽位上 shift 右键：在容器所在处把它倒空。
        if (shift && hoveredSlot != null && menu.isPlayerSideSlot(hoveredSlot)) {
            ItemStack inSlot = hoveredSlot.getItem();
            if (EssentiaFillHelper.isSupportedContainer(inSlot)) {
                // 传菜单的槽位 id，不传物品栏下标：AE2 把视图元件和升级槽排在玩家槽位前面，
                // 服务端按自己的槽位表解析这个 id。
                ClientPacketDistributor.sendToServer(new EssentiaDepositPayload(
                        menu.containerId, menu.slots.indexOf(hoveredSlot)));
                return true;
            }
            return false;
        }

        ItemStack container = heldContainer();
        if (container == null) {
            return false;
        }
        boolean overEssentia = false;
        if (hoveredSlot instanceof RepoSlot repoSlot) {
            var entry = repoSlot.getEntry();
            overEssentia = entry != null && entry.getWhat() instanceof AEssentiaKey;
        }
        if (!overEssentia) {
            // 根本不是网格里的格子：手上拿什么都一样，这次点击归 AE2。
            return false;
        }
        if (EssentiaFillHelper.isContainerEmpty(container)) {
            // 我们的容器且是空的：AE2 的右键会把这个容器插进去，而空容器是给左键用的工具，
            // 所以这次点击到此为止。
            return true;
        }
        // 我们的容器且是满的：内容进网络，从光标上的或主手上的都行。
        ClientPacketDistributor.sendToServer(new EssentiaDepositPayload(
                menu.containerId, whereHeld()));
        return true;
    }

    private boolean handleLeftClick(boolean shift) {
        ItemStack container = heldContainer();
        if (container == null || !(hoveredSlot instanceof RepoSlot repoSlot)) {
            return false;
        }
        var entry = repoSlot.getEntry();
        if (entry == null) {
            return false;
        }
        if (entry.getWhat() instanceof AEssentiaKey key && EssentiaFillHelper.isContainerEmpty(container)) {
            // 按住 shift 就把这次点击变成「整叠手持物品」：装满网络付得起的量，
            // 付不起的留在原处。
            ClientPacketDistributor.sendToServer(new EssentiaFillPayload(
                    menu.containerId, key.getId(), whereHeld(), container, shift));
            return true;
        }
        if (!(entry.getWhat() instanceof AEssentiaKey)) {
            // 根本不是网格里的格子：手上拿什么都一样，这次点击归 AE2。
            return false;
        }
        if (cursorIsContainer()) {
            // 我们的容器，光标上是罐或药瓶：AE2 会把还装着源质的容器插进去，
            // 而那种操作对应的手势是 shift 右键，所以这次点击到此为止。
            ThELog.LOG.info(TAG + "entry click refused: the cursor holds a container,"
                    + " which is never inserted into the network");
            return true;
        }
            // 根本不是网格里的格子：手上拿什么都一样，这次点击归 AE2。
        return false;
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
}
