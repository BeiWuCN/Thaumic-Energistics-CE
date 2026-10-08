package thaumicenergistics_ce.client.gui;

import appeng.client.Point;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * 费用行在样式的 [visCraftCost] 条带里，vis 显示只有这一处，不带灵气是刻意的。
 * 样式文档放在 AE2 命名空间下：[StyleManager] 只解析自己命名空间里的文档。
 * 罐和药瓶手势来自 {@link ScreenEssentiaTerminalBase}，只在装了源质访问卡时提供。
 */
public class ScreenArcaneCraftingTerminal extends ScreenEssentiaTerminalBase<MenuArcaneCraftingTerminal>
        implements ClientboundReceiver {

    private static final int CHIP_UNITS = AspectRendering.GUI_ICON_SIZE;

    /**
     * 样式控件名，指向图标所在的木质条带。位置由美术图决定，换皮或改尺寸的窗口会带着这行走：
     * 屏幕样式文档里的 {@code widgets.visCraftCost}。
     */
    private static final String VIS_COST_STRIP = "visCraftCost";

    private static @Nullable ScreenArcaneCraftingTerminal open;

    private List<ArcaneCraftCostPayload.AspectCost> costs = List.of();

    public ScreenArcaneCraftingTerminal(
            MenuArcaneCraftingTerminal menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    /** 没有这张卡时终端就是普通终端，手势全落到 AE2 自己的实现上。 */
    @Override
    protected boolean essentiaGesturesAtAll() {
        // 每次点击都重新问，不缓存答案：菜单读的是玩家看到的升级槽，
        // 卡被取出后下一次点击就没手势了，缓存的标志做不到。
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
     * 接收端装在本类上，接收者就是打开着的那个界面；下面的 id 校验仍然决定载荷是不是给它的。
     */
    public static void acceptCost(ArcaneCraftCostPayload payload) {
        ScreenArcaneCraftingTerminal screen = open;
        // id 校验不能省：数据包可能在玩家关掉本界面、打开另一个界面之后才到，
        // 套用就会把上一个网格的费用画到新界面上。
        if (screen != null && screen.getMenu().containerId == payload.containerId()) {
            screen.costs = payload.aspects();
        }
    }

    @Override
    public void drawFG(GuiGraphicsExtractor graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        if (costs.isEmpty()) {
            return;
        }
        // 条带由样式定位，解析出的点本就是窗口相对的：
        // [drawFG] 已在面板偏移的位姿里，再加一次 [leftPos] 会让这行偏出美术图一个窗口。
        WidgetStyle strip = getStyle().getWidget(VIS_COST_STRIP);
        if (strip == null) {
            return;
        }
        Point at = strip.resolve(new Rect2i(0, 0, imageWidth, imageHeight));
        // 图标数不超过水晶能占的格数，条带宽度除以格数就是一个图标的最大边长：
        // 美术图 69 列除以六个格得 11，六个图标占满这 66 列。
        int chip = Math.min(strip.getHeight(), strip.getWidth() / PartArcaneCraftingTerminal.CRYSTAL_SLOTS);
        if (chip <= 0) {
            return;
        }
        // 从右往左排：第一个要素占住条带右端，剩下的往回排。
        // 单要素配方永远落在同一位置，不随要素列表长度漂移。
        int y = at.getY() + (strip.getHeight() - chip) / 2;
        int x = at.getX() + strip.getWidth() - chip;
        // Thaumaturge 的渲染器总按 [CHIP_UNITS] 见方画图标，不问界面尺寸，
        // 这里用位姿把它缩到条带容得下的大小。
        float shrink = (float) chip / CHIP_UNITS;
        for (ArcaneCraftCostPayload.AspectCost cost : costs) {
            if (x < at.getX()) {
                break;
            }
            var aspect = Aspects.resolve(
                    menu.getPlayer().level(), ResourceKey.create(
                            IAspect.REGISTRY_KEY, cost.aspect()));
            if (aspect != null) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(x, y);
                graphics.pose().scale(shrink, shrink);
                AspectRendering.renderGui(graphics, font, 0, 0, aspect, 0.0F);
                // centivis 折算成整数 vis 并向上取整：1 centivis 也要一个 vis 才付得起，
                // 显示 0 会被读成免费。数字以图标为单位，靠位姿缩放。
                String text = String.valueOf((cost.centivis() + 99) / 100);
                graphics.text(
                        font, text, CHIP_UNITS - font.width(text) + 1, CHIP_UNITS - 6, 0xFFFFFF, true);
                graphics.pose().popMatrix();
            }
            x -= chip;
        }
    }
}
