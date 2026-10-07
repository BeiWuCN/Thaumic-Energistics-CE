package thaumicenergistics_ce.client.gui;

import appeng.client.Point;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.client.GolemBackpackClientData;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import thaumicenergistics_ce.network.ClientboundReceiver;
import thaumicenergistics_ce.network.GolemBackpackPayload;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 奥术合成终端的界面。
 * 费用行位于样式的 [visCraftCost] 条带中，它就是 vis 显示的全部：不带灵气，出于
 * 刻意取舍。样式文档放在 AE2 的命名空间下，因为 [StyleManager] 只解析自己命名空间内的文档。
 * 罐与药瓶手势来自 {@link ScreenEssentiaTerminalBase}，仅在装有源质访问卡时
 * 才提供。
 */
public class ScreenArcaneCraftingTerminal extends ScreenEssentiaTerminalBase<MenuArcaneCraftingTerminal>
        implements ClientboundReceiver {

    private static final int CHIP_UNITS = AspectRendering.GUI_ICON_SIZE;

    /**
     * 样式控件的名字，指向图标所放的木质条带。位置由美术图决定，所以换皮或改尺寸
     * 的窗口会带着这一行一起走：屏幕样式文档里的 {@code widgets.visCraftCost}。
     */
    private static final String VIS_COST_STRIP = "visCraftCost";

    private static @Nullable ScreenArcaneCraftingTerminal open;

    private List<ArcaneCraftCostPayload.AspectCost> costs = List.of();

    public ScreenArcaneCraftingTerminal(
            MenuArcaneCraftingTerminal menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    /** 没有这张卡时终端就是普通终端：所有手势都落到 AE2 自己的实现上。 */
    @Override
    protected boolean essentiaGesturesAtAll() {
        // 每次点击都重新询问，而不是记住结果：菜单读取的是玩家看到的升级槽，因此
        // 卡被取出后下一次点击就会停掉手势，缓存的标志永远做不到这一点。
        return menu.hasEssentiaAccessCard();
    }

    @Override
    public void init() {
        super.init();
        open = this;
    }

    @Override
    public void removed() {
        if (open == this) {
            open = null;
        }
        super.removed();
    }

    @Override
    public void acceptArcaneCraftCost(ArcaneCraftCostPayload payload) {
        acceptCost(payload);
    }

    @Override
    public void acceptGolemBackpack(GolemBackpackPayload payload) {
        GolemBackpackClientData.accept(payload);
    }

    /**
     * 安装的接收端就是本类，所以接收者就是打开着的那个界面，下面的 id 校验仍然
     * 决定这份载荷是不是给它的。
     */
    public static void acceptCost(ArcaneCraftCostPayload payload) {
        ScreenArcaneCraftingTerminal screen = open;
        // id 校验很重要：数据包可能在玩家刚关掉本界面、打开另一个界面之后才到，
        // 那时套用就会把上一个网格的费用画到新界面上。
        if (screen != null && screen.getMenu().containerId == payload.containerId()) {
            screen.costs = payload.aspects();
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        if (costs.isEmpty()) {
            return;
        }
        // 条带由样式定位，它解析出的点本就是窗口相对的：[drawFG] 运行在面板偏移的
        // 位姿内，再加一次 [leftPos] 会让该行偏出美术图整整一个窗口。
        WidgetStyle strip = getStyle().getWidget(VIS_COST_STRIP);
        if (strip == null) {
            return;
        }
        Point at = strip.resolve(new Rect2i(0, 0, imageWidth, imageHeight));
        // 图标数量绝不会超过水晶能占的格数，所以条带宽度除以该格数就是一个图标的最大
        // 边长：美术图 69 列除以六个格得 11，六个图标正好占满其中 66 列。
        int chip = Math.min(strip.getHeight(), strip.getWidth() / PartArcaneCraftingTerminal.CRYSTAL_SLOTS);
        if (chip <= 0) {
            return;
        }
        // 从右向左：第一个要素占住条带的右端，其余要素沿条带往回排，这样单要素配方
        // 总是落在同一位置，而不会随要素列表长度漂移。
        int y = at.getY() + (strip.getHeight() - chip) / 2;
        int x = at.getX() + strip.getWidth() - chip;
        // Thaumaturge 的渲染器不管界面要多大，都按 [CHIP_UNITS] 见方绘制图标，所以
        // 这里用位姿把它缩到条带容得下的图标尺寸。
        float shrink = (float) chip / CHIP_UNITS;
        for (ArcaneCraftCostPayload.AspectCost cost : costs) {
            if (x < at.getX()) {
                break;
            }
            var aspect = Aspects.resolve(
                    menu.getPlayer().level(), ResourceKey.create(
                            IAspect.REGISTRY_KEY, cost.aspect()));
            if (aspect != null) {
                graphics.pose().pushPose();
                graphics.pose().translate(x, y, 0.0F);
                graphics.pose().scale(shrink, shrink, 1.0F);
                AspectRendering.renderGui(graphics, font, 0, 0, aspect, 0.0F);
                // centivis 折算成整数 vis，向上取整：1 centivis 的费用仍需要一个 vis 来付，
                // 显示 0 会被读成免费。数字以图标为单位，由位姿缩放下去。
                String text = String.valueOf((cost.centivis() + 99) / 100);
                graphics.drawString(
                        font, text, CHIP_UNITS - font.width(text) + 1, CHIP_UNITS - 6, 0xFFFFFF, true);
                graphics.pose().popPose();
            }
            x -= chip;
        }
    }
}
