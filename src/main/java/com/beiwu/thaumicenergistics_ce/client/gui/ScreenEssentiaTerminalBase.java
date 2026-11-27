package com.beiwu.thaumicenergistics_ce.client.gui;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
import appeng.client.gui.style.ScreenStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import com.beiwu.thaumicenergistics_ce.integration.ae2.essentia.EssentiaFillHelper;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import com.beiwu.thaumicenergistics_ce.menu.MenuEssentiaTerminalBase;
import com.beiwu.thaumicenergistics_ce.menu.slot.ContainerSlot;
import com.beiwu.thaumicenergistics_ce.network.EssentiaDepositPayload;
import com.beiwu.thaumicenergistics_ce.network.EssentiaFillPayload;
import com.beiwu.thaumicenergistics_ce.network.EssentiaPhialTakePayload;
import com.beiwu.thaumicenergistics_ce.util.ThELog;

/**
 * 两个带罐与药瓶手势的终端界面共用这一套。手势要读鼠标落在哪一格，
 * 而那一格由 {@link #slotAt} 现算（辅助类看不到界面坐标）。
 * <p>分工与 AE2 的流体终端一致，不另立一套：
 * <ul>
 *   <li><b>交回 AE2</b>：非源质条目在空手时的点击、空手点击空格子、
 *       以及右键空容器存放。没装卡时的存取门槛由容器策略的 menuAllows 兜底
 *       （AE2 把 FILL_ITEM / EMPTY_ITEM 直接交给策略，不经过这里的界面手势）。</li>
 *   <li><b>本类补的</b>（AE2 原生做不到或做不完整的）：
 *     <ul>
 *       <li>空手左键取一整瓶：AE2 的自动取空容器写死在流体桶上
 *           （{@code tryFillContainerItem} 只认 {@code AEFluidKey} 与 {@code Items.BUCKET}）。</li>
 *       <li>网格上右键倒空满容器：AE2 的 EMPTY_ITEM 一次只请求 {@code getAmountPerUnit()}
 *           那么多单位，那个值固定为 1（调大要把全部存量显示除下去，不能动），
 *           而容器策略要整瓶才肯换，于是它报 0 空转。这一支只能自己发。</li>
 *       <li>玩家槽位上 shift 右键，在容器所在处倒空。</li>
 *     </ul></li>
 *   <li><b>不拦的</b>：右键空安瓿存进网络。那是存放路径的第二步——
 *       空瓶的 {@code getEmptyingAction} 返回 null，AE2 于是发 SPLIT_OR_PLACE_SINGLE，
 *       一次存 1 个。流体桶存进网络也是这条。
 *       右键满瓶同理会走到 EMPTY_ITEM，但对整瓶语义它空转，所以本类先接管。</li>
 *   <li><b>拦的</b>：左键点空格子或非源质行、以及 shift+滚轮。
 *       这三手对光标里的容器只做一件事：把它当普通物品存进网络
 *       （左键是整叠，滚轮一次一个）。流体桶被这么存进去玩家不心疼，
 *       安瓿里装着刚取出的源质。存放只走右键，其他地方一律不动它。</li>
 * </ul>
 * 按键约定：左键装、右键倒。满容器时左键不倒——那会把刚取出的那瓶倒回去。
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
        // 槽位用几何位置现算，不读 hoveredSlot：后者由 mouseMoved 更新，
        // 按下的那一刻可能还是 null 或上一格。原版 AbstractContainerScreen.mouseClicked
        // 是用 findSlot 现算后交给 slotClicked 的，AE2 的网格入口同样只认现算结果。
        // 读 hoveredSlot 的后果是满容器那一支不成立，整只安瓿（连里面的源质）
        // 被 AE2 当普通物品存进网络。
        Slot hit = slotAt(mouseX, mouseY);
        if (!essentiaGesturesAtAll()) {
            // 无卡：源质行仍然可见，但存取都不该发生。AE2 自己的 FILL_ITEM / EMPTY_ITEM
            // 会把光标里的容器交给容器策略，服务端那边已经拦了；这里再挡一下，
            // 免得界面看起来能操作、点下去却没动静。
            if (holdsContainerOverEssentia(hit)) {
                log("gesture blocked: no essentia access card in the terminal");
                Player player = menu.getPlayerInventory().player;
                player.displayClientMessage(
                        Component.translatable("thaumicenergistics_ce.gui.essentia.needs_access_card"), true);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 1 && handleRightClick(hit)) {
            return true;
        }
        if (button == 0 && handleLeftClick(hit)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 光标（或主手）拿着容器，且鼠标正悬在源质行上。 */
    private boolean holdsContainerOverEssentia(Slot hit) {
        if (heldContainer() == null || !(hit instanceof RepoSlot repoSlot)) {
            return false;
        }
        var entry = repoSlot.getEntry();
        return entry != null && entry.getWhat() instanceof AEssentiaKey;
    }

    @Override
    public void init() {
        super.init();
        logUpgradePanelOnce();
    }

    /**
     * 升级槽由 AE2 的 {@code UpgradesPanel} 画而不是样式里的 slot 声明，
     * 面板本身依赖 {@code menu.getSlots(UPGRADE)} 非空。这两个数任一个是 0，
     * 界面上就一个升级槽都看不到——不打印就只能靠猜。每个屏幕类只报一次。
     */
    private static final java.util.Set<Class<?>> REPORTED = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private void logUpgradePanelOnce() {
        if (!REPORTED.add(getClass())) {
            return;
        }
        var upgradeSlots = menu.getSlots(appeng.menu.SlotSemantics.UPGRADE);
        var host = menu.getHost();
        long enabled = upgradeSlots.stream()
                .filter(s -> !(s instanceof appeng.menu.slot.AppEngSlot appEng) || appEng.isSlotEnabled())
                .count();
        ThELog.LOG.info(TAG + "{}: upgrade slots in menu = {} (enabled {}), host = {}, host inventory size = {}",
                getClass().getSimpleName(), upgradeSlots.size(), enabled,
                host.getClass().getSimpleName(), host.getUpgrades().size());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // shift+滚轮也是存放路径：AE2 把对网格的滚动换成 MEInteractionPacket 的
        // ROLL_DOWN，而它会“Insert 1 of the carried stack into the network”。
        // 一次一个看着温柔，但它同样是把安瓿往网络里推。存放只走右键。
        // 不带 shift 的滚动只是滚列表，拦了反而滚不动。
        if (essentiaGesturesAtAll() && isHoldingContainer() && hasShiftDown()
                && slotAt(mouseX, mouseY) instanceof RepoSlot) {
            log("shift+scroll blocked: the cursor holds {}",
                    menu.getCarried().getHoverName().getString());
            menu.getPlayerInventory().player.displayClientMessage(
                    Component.translatable("thaumicenergistics_ce.gui.essentia.store_with_right_click"), true);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean handleRightClick(Slot hit) {
        // 玩家槽位上 shift 右键：在容器所在处把它倒空
        // （AE2 的 EMPTY_* 只从网格条目上发）。
        if (hasShiftDown() && hit != null && menu.isPlayerSideSlot(hit)) {
            ItemStack inSlot = hit.getItem();
            if (EssentiaFillHelper.isSupportedContainer(inSlot)) {
                // 传菜单的槽位 id，不传物品栏下标：AE2 把视图元件和升级槽排在玩家槽位前面，
                // 服务端按自己的槽位表解析这个 id。
                int where = menu.slots.indexOf(hit);
                if (where < 0) {
                    // 算不出来就别发：-1 与服务端的 CURSOR 撞车，会把光标上的东西倒了。
                    return false;
                }
                log("deposit requested: {} from menu slot {}",
                        inSlot.getHoverName().getString(), where);
                PacketDistributor.sendToServer(new EssentiaDepositPayload(menu.containerId, where));
                return true;
            }
            return false;
        }

        // 网格上、手拿着满容器：这一支自己发，而不是让 AE2 走 EMPTY_ITEM。
        // AE2 的取量是 getAmountPerUnit()，该值固定为 1（见 AEssentiaKeyType），
        // 而容器策略要整瓶才肯换，所以交互交给 AE2 只会报 0 空转。
        // 另外容器策略只认 PhialItem：罐子、标签、水晶它都不认识。
        // 用 heldContainer() 而不是 getCarried()：光标空时回退到主手，
        // 否则主手那瓶永远倒不了（AE2 自己的 emptying 也只看光标）。
        ItemStack container = heldContainer();
        if (hit instanceof RepoSlot
                && container != null
                && !EssentiaFillHelper.isContainerEmpty(container)) {
            log("deposit requested: {} from {}", container.getHoverName().getString(), heldName());
            PacketDistributor.sendToServer(new EssentiaDepositPayload(menu.containerId, whereHeld()));
            return true;
        }

        // 剩下的全归 AE2，而它自己就能排好次序：
        // 空安瓿报不出可倒的东西（getEmptyingAction 返回 null），
        // 动作于是落到 SPLIT_OR_PLACE_SINGLE，存 1 个进网络。
        // 先倒、再存，正好是想要的顺序，不需要在客户端拦任何东西。
        return false;
    }

    private boolean handleLeftClick(Slot hit) {
        ItemStack container = heldContainer();
        if (!(hit instanceof RepoSlot repoSlot)) {
            return false;
        }
        var entry = repoSlot.getEntry();
        boolean overEssentia = entry != null && entry.getWhat() instanceof AEssentiaKey;
        if (!overEssentia) {
            // 左键不是存放路径。本模组的约定是右键存放：右键一次把源质倒进网络，
            // 再右键一次把空瓶存进去。左键若也交给 AE2，它会把光标上的整叠安瓿
            // 当普通物品一下存掉（PICKUP_OR_SET_DOWN → putCarriedItemIntoNetwork(false)）。
            // 流体的桶被这么存进去玩家不心疼，安瓿里装着刚取出来的源质。
            if (isHoldingContainer()) {
                log("left-click blocked: {} over a non-essentia row",
                        menu.getCarried().getHoverName().getString());
                menu.getPlayerInventory().player.displayClientMessage(
                        Component.translatable("thaumicenergistics_ce.gui.essentia.store_with_right_click"), true);
                return true;
            }
            return false;
        }
        if (container == null) {
            // 桶同款：空手点源质行，直接从网络取一个空瓶灌满。
            // 装不出来时服务端只回一句提示，不扣源质。
            // shift 不参与：AE2 的桶路径也是一次一瓶，不因为按了 shift 就连抽一背包。
            AEssentiaKey key = (AEssentiaKey) entry.getWhat();
            log("phial take requested: {}", key.getId());
            PacketDistributor.sendToServer(new EssentiaPhialTakePayload(menu.containerId, key.getId()));
            return true;
        }
        if (EssentiaFillHelper.isContainerEmpty(container)) {
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
        // 左键不负责「倒空」。以前这里也发 deposit，于是空手取出一瓶后光标上就是满容器，
        // 玩家再点一下源质行（想取第二瓶）就把刚取的那瓶倒回网络了。
        // 本模组的约定是左键装、右键倒（满瓶右键由本类发 deposit 倒空）。
        // 源质行上左键什么也不做，但得说一声，否则看起来像卡住了。
        log("left-click ignored: {} is full, emptying is a right-click", container.getHoverName().getString());
        menu.getPlayerInventory().player.displayClientMessage(
                Component.translatable("thaumicenergistics_ce.gui.essentia.empty_with_right_click"), true);
        return true;
    }

    /**
     * 按鼠标位置现算落在哪一格。不用 hoveredSlot：它由 mouseMoved 更新，
     * 按下的那一刻可能还是 null 或上一格，后果是手势判据全落空、点击整个交给 AE2
     * （整只安瓿连里面的源质被当普通物品存进网络）。
     * AbstractContainerScreen.findSlot 对本模组不可见（AE2 靠 access widener 才能调），
     * 所以这里按原版同一套几何规则自己算：起点是 leftPos + slot.x / topPos + slot.y，
     * 尺寸 16×16，<b>外加原版 isHovering 的 ±1 松弛</b>（有效 18×18）。
     * 那 1px 不能省：少了它，格子之间那条 2px 缝隙就成盲区——本类判空、AE2 判中，
     * 点在缝上就把安瓿交给 AE2 存了。
     */
    private @Nullable Slot slotAt(double mouseX, double mouseY) {
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) {
                return slot;
            }
        }
        return null;
    }

    /** 光标上拿着源质容器（安瓿、罐子）。左键拦截与滚轮拦截共用。 */
    private boolean isHoldingContainer() {
        ItemStack carried = menu.getCarried();
        return !carried.isEmpty() && EssentiaFillHelper.isSupportedContainer(carried);
    }

    private static void log(String message, Object... args) {        ThELog.LOG.info(TAG + message, args);
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
