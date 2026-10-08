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
        if (essentiaGesturesAtAll() && slot instanceof RepoSlot && cursorIsContainer()
                && type != ClickType.QUICK_MOVE) {
            // shift 左键（QUICK_MOVE）不经过光标，放行才能照常把网络里的东西搬进背包。
            // 非源质行也得挡：漏给 AE2 的空容器会被它当普通物品存进网络。
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (essentiaGesturesAtAll() && cursorIsContainer()) {
            // 空格子没有条目，滚轮落上去时 AE2 的 ROLL_DOWN / ROLL_UP 分支
            // 会把手持的容器当普通物品存进网络。只挡空格子：有条目的格子照旧一次取一个。
            if (hoveredSlot instanceof RepoSlot repoSlot && repoSlot.getEntry() == null) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean handleRightClick() {
        // 玩家槽位上 shift 右键：在容器所在处把它倒空。
        if (hasShiftDown() && hoveredSlot != null && menu.isPlayerSideSlot(hoveredSlot)) {
            ItemStack inSlot = hoveredSlot.getItem();
            if (EssentiaFillHelper.isSupportedContainer(inSlot)) {
                // 传菜单的槽位 id，不传物品栏下标：AE2 把视图元件和升级槽排在玩家槽位前面，
                // 服务端按自己的槽位表解析这个 id。
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
            // 行上有东西可给时，空容器就是装入，左右键都算：
            // 方向由容器决定，按键只在单个物品和整叠之间选。
            log("fill requested: {} into the {} of {} from {}", overKey.getId(),
                    container.getHoverName().getString(),
                    heldName(), hasShiftDown() ? "the whole held stack" : "one item");
            PacketDistributor.sendToServer(new EssentiaFillPayload(
                    menu.containerId, overKey.getId(), whereHeld(), container, hasShiftDown()));
            return true;
        }
        if (!(hoveredSlot instanceof RepoSlot)) {
            // 根本不是网格里的格子：手上拿什么都一样，这次点击归 AE2。
            return false;
        }
        // 满容器从一行倒空进网络，跟从空存储元件上倒空一样：
        // 从哪取由该行决定，往哪走由容器决定。
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
                // 空容器又没东西可取：别的类型的条目按普通物品点击处理，空存储元件也没得取。
                return false;
            }
            AEssentiaKey key = (AEssentiaKey) entry.getWhat();
            // 按住 shift 就把这次点击变成整叠手持物品：装满网络付得起的量，
            // 付不起的留在原处。
            log("fill requested: {} into the {} of {} from {}", key.getId(),
                    container.getHoverName().getString(),
                    heldName(), hasShiftDown() ? "the whole held stack" : "one item");
            PacketDistributor.sendToServer(new EssentiaFillPayload(
                    menu.containerId, key.getId(), whereHeld(), container, hasShiftDown()));
            return true;
        }
        // 满容器从一行倒空进网络，跟从空存储元件上倒空一样：
        // 方向由容器决定，不看光标下的那一行。
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

    /** {@link #whereHeld()} 的位置写成文字，给日志行用。 */
    private String heldName() {
        return menu.getCarried().isEmpty() ? "the main hand" : "the cursor";
    }
}
