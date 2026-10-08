package thaumicenergistics_ce.client.gui;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
import appeng.client.gui.style.ScreenStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * 按键定方向：左键把光标下那一行的要素装进手里的容器（容器得是空的），
 * 右键把手里的容器倒回网络（容器里得有源质）。
 * 方向不再由容器里有没有东西决定——同一个键一会儿取一会儿倒，用起来记不住。
 * shift 保持 AE2 的含义：装入时对整叠手持物品生效；
 * 玩家自己的槽位上 shift 右键则在容器所在处动手。除了悬停框，这里不画别的东西。
 */
public abstract class ScreenEssentiaTerminalBase<M extends MenuEssentiaTerminalBase>
        extends MEStorageScreen<M> {

    protected ScreenEssentiaTerminalBase(
            M menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    /** 本界面有没有罐与药瓶手势；装了卡的终端才有。 */
    protected abstract boolean essentiaGesturesAtAll();

    /**
     * 悬停框画成 AE2 的样子：一像素浅青细框加半透明蓝底。
     * 26.1.2 原版的高亮变成了私有的白方块 sprite，模组覆写不了也拦不住；
     * AE2 自己那个 {@code AEBaseScreen.renderSlotHighlight} 在这一代也没接上钩子
     * （全 jar 只有声明、没有任何调用点），所以终端界面只能自己补一遍，
     * 见 {@link Ae2SlotHighlight}。AE2 的 [extractContents] 末尾会抬一层图元，
     * 这一帧因此压在槽位美术上面、tooltip 下面。
     */
    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractContents(graphics, mouseX, mouseY, partialTick);
        Slot hovered = this.hoveredSlot;
        if (hovered == null) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(leftPos, topPos);
        Ae2SlotHighlight.render(graphics, hovered);
        graphics.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!essentiaGesturesAtAll()) {
            return super.mouseClicked(event, doubleClick);
        }
        boolean shift = event.hasShiftDown();
        // 左键取、右键倒，固定不变。不看容器里有没有东西来定方向，
        // 那样同一个键一会儿取一会儿倒，玩家记不住：左键永远是装入，右键永远是倒空。
        if (event.button() == 0 && handleLeftClick(shift)) {
            return true;
        }
        if (event.button() == 1 && handleRightClick(shift)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ContainerInput type) {
        if (essentiaGesturesAtAll() && slot instanceof RepoSlot && cursorIsContainer()
                && type != ContainerInput.QUICK_MOVE) {
            // 除 shift 左键（QUICK_MOVE）以外，光标上是本 mod 的容器时，网格里的格子不交给 AE2。
            // shift 左键放行：它不经过光标，放行才能照常把网络里的东西搬进背包——
            // 之前一并挡掉，手里拿着罐子时整个面板就成了死的。
            // 源质行：鼠标点击已被手势认领，这里只挡拖拽（拖拽不走 [mouseClicked]）。
            // 非源质行：满容器在上面已被认领成倒空；只剩空容器会漏到这里，
            // 而 AE2 会把它当普通物品存进网络——玩家看到的就是
            // 「源质存进去了，空安瓿也被网络吃掉」。手势开着时手里的容器是工具，不是物品。
            ThELog.LOG.info("[源质手势] 吞掉仓库点击：光标拿着 {}，按键 {}，类型 {}（这一次 AE2 收不到）",
                    getMenu().getCarried(), mouseButton, type);
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (essentiaGesturesAtAll() && cursorIsContainer()) {
            // 空格子没有条目，AE2 收到的是 serial -1；shift 滚轮落在空格子上时，
            // 它的 ROLL_DOWN / ROLL_UP 分支会把手持的容器当普通物品存进网络。
            // 只挡空格子：有条目的格子照旧一次取一个，那是 AE2 的正经功能。
            if (hoveredSlot instanceof RepoSlot repoSlot
                    && repoSlot.getEntry() == null) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        // 右键只做倒空：往网络走，所以要落在网格的格子上；别处右键还是 AE2 自己的操作。
        if (!(hoveredSlot instanceof RepoSlot)) {
            return false;
        }
        if (EssentiaFillHelper.isContainerEmpty(container)) {
            // 空容器没有东西可倒，这次点击交回 AE2。
            // 它想把空容器当普通物品存进网络也存不成：[#slotClicked] 那道守卫拦得住。
            return false;
        }
        // 倒空进网络，跟从空存储元件上倒空一样：方向由容器决定，不看光标下是哪一行。
        ClientPacketDistributor.sendToServer(new EssentiaDepositPayload(
                menu.containerId, whereHeld()));
        return true;
    }

    private boolean handleLeftClick(boolean shift) {
        // 左键只做装入：光标下得是列出了要素的那一行，手里的容器还得是空的。
        ItemStack container = heldContainer();
        if (container == null || !(hoveredSlot instanceof RepoSlot repoSlot)) {
            return false;
        }
        if (!EssentiaFillHelper.isContainerEmpty(container)) {
            // 容器里已经有源质：装入会被服务端拒绝（一个容器只装一种要素），
            // 倒空又是右键的事，所以这次点击交回 AE2。
            return false;
        }
        var entry = repoSlot.getEntry();
        if (entry == null || !(entry.getWhat() instanceof AEssentiaKey key)) {
            // 空容器又没东西可取：别的类型的条目按普通物品点击处理，空存储元件也没得取。
            return false;
        }
        // 按住 shift 就把这次点击变成「整叠手持物品」：装满网络付得起的量，
        // 付不起的留在原处。
        ClientPacketDistributor.sendToServer(new EssentiaFillPayload(
                menu.containerId, key.getId(), whereHeld(), container, shift));
        return true;
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
