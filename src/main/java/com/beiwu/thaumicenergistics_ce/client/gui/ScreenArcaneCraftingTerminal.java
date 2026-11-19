package com.beiwu.thaumicenergistics_ce.client.gui;

import appeng.api.config.ActionItems;
import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;
import appeng.client.gui.widgets.ActionButton;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import com.leclowndu93150.thaumaturge.client.screen.TTTooltips;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.recipe.arcane.EssentiaCrystals;
import com.beiwu.thaumicenergistics_ce.recipe.arcane.ThEArcanePattern;
import com.beiwu.thaumicenergistics_ce.client.GolemBackpackClientData;
import com.beiwu.thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import com.beiwu.thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import com.beiwu.thaumicenergistics_ce.network.ClientboundReceiver;
import com.beiwu.thaumicenergistics_ce.network.GolemBackpackPayload;
import com.beiwu.thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 奥术合成终端的界面。
 * 费用行在样式的 [visCraftCost] 条带里：右端是法杖出的 vis，左端接着要扣的晶体。
 * 环境 VIS 独立显示在法杖槽旁。
 * 样式文档放在 AE2 命名空间下：[StyleManager] 只解析自己命名空间里的文档。
 * 罐和药瓶手势来自 {@link ScreenEssentiaTerminalBase}，只在装了源质访问卡时提供。
 */
public class ScreenArcaneCraftingTerminal extends ScreenEssentiaTerminalBase<MenuArcaneCraftingTerminal>
        implements ClientboundReceiver {

    private static final int CHIP_UNITS = AspectRendering.GUI_ICON_SIZE;

    /**
     * 三行 vis 文字的配色直接取奥术工作台那套：可用、不足、消耗各一个色，
     * 和 {@code ArcaneWorkbenchScreen} 里画同一批文字用的是同几个值。
     */
    private static final int VIS_AVAILABLE_COLOR = 0xFF6E6EEE;
    private static final int VIS_SHORT_COLOR = 0xFFEE6E6E;

    /** 「即将消耗」那一行：工作台在深色底上用的浅紫，比可用行的紫更亮一档。 */
    private static final int VIS_COST_COLOR = 0xFFC1C1FF;

    /** 格子上方那行标题最多占多宽：右边要留给两行 vis 文字。 */
    private static final float GRID_TITLE_MAX_WIDTH = 88.0F;

    /**
     * 样式控件名，指向图标所在的木质条带。位置由美术图决定，换皮或改尺寸的窗口会带着这行走：
     * 屏幕样式文档里的 {@code widgets.visCraftCost}。
     */
    private static final String VIS_COST_STRIP = "visCraftCost";

    /**
     * 样式控件名，指向「即将消耗」那一行：屏幕样式文档里的 {@code widgets.visUpcoming}。
     * 它占的是「可用 vis」原来的位置，可用 vis 让到下一行。
     */
    private static final String VIS_UPCOMING_STRIP = "visUpcoming";

    /** 样式控件名，指向「可用 vis」那一行：屏幕样式文档里的 {@code widgets.visAvailable}。 */
    private static final String VIS_AVAILABLE_STRIP = "visAvailable";

    /**
     * 样式控件名，指向格子上方那行文字：屏幕样式文档里的 {@code text.crafting_grid_title}。
     * 键写死在文档里，值由 {@link #init} 换成终端自己的名字。
     */
    private static final String CRAFTING_GRID_TITLE = "crafting_grid_title";

    private static @Nullable ScreenArcaneCraftingTerminal open;

    private List<ArcaneCraftCostPayload.AspectCost> costs = List.of();

    /** 同一次合成要从六个晶体槽里扣掉的晶体，画在要素左边。 */
    private List<ArcaneCraftCostPayload.CrystalCost> crystals = List.of();

    /** 这次合成要从灵气里抽走的 vis，已算上减免。 */
    private int auraVis;

    /** 灵气减免百分比；0 表示未聚焦。 */
    private int discountPercent;

    /**
     * 费用条上每一枚图标的位置和它自己的提示，每次 {@code drawFG} 重填。
     * 六枚各说各的：要素说「消耗多少 vis」，晶体说「消耗多少颗」。
     */
    private final List<ChipHit> chipHits = new java.util.ArrayList<>();

    private record ChipHit(int x, int y, int size, List<Component> tooltip) {}

    /** 付费源按钮，图标跟着菜单里的模式走。 */
    private PaymentModeButton paymentModeButton;

    /** 终端自己的名字。原先是顶行，现在下移到格子上方那行。 */
    private final Component terminalName;

    public ScreenArcaneCraftingTerminal(
            MenuArcaneCraftingTerminal menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, Component.translatable("gui.ae2.Terminal"), style);
        terminalName = title;

        // 和 AE2 1.21.1 的合成终端同款：半尺寸图标加去掉按钮底框，缺任一项都会画成
        // 全尺寸的白色大箭头压在木纹条上。图标、tooltip 都来自 [ActionItems]，样式文档里只有坐标。
        ActionButton toNetwork = new ActionButton(ActionItems.S_STASH, btn -> menu.clearCraftingGridToNetwork());
        toNetwork.setHalfSize(true);
        toNetwork.setDisableBackground(true);
        widgets.add("clearCraftingGrid", toNetwork);

        ActionButton toPlayer = new ActionButton(ActionItems.S_STASH_TO_PLAYER_INV, btn -> menu.clearCraftingGridToPlayer());
        toPlayer.setHalfSize(true);
        toPlayer.setDisableBackground(true);
        widgets.add("clearToPlayerInv", toPlayer);
        // 付费源按钮进 AE 的统一左侧工具栏，位置由工具栏自动排，和 help 等按钮同列。
        // 两个清空按钮仍是 widgets 里的半尺寸图标，不进工具栏。
        paymentModeButton = addToLeftToolbar(new PaymentModeButton(btn -> menu.togglePaymentSource()));
        paymentModeButton.setCrystals(menu.crystalFirst());
    }

    /** AE2 的 help 按钮指向本 mod 的 ME 指南页，而不是 AE2 自己的指南。 */
    @Override
    protected guideme.PageAnchor getHelpTopic() {
        return guideme.PageAnchor.page(ResourceLocation.parse(ModGuide.PAGE_ARCANE_TERMINAL));
    }

    @Override
    protected void openHelp() {
        ModGuide.open(menu.getPlayer(), ModGuide.PAGE_ARCANE_TERMINAL);
    }

    /** 没有这张卡时终端就是普通终端，手势全落到 AE2 自己的实现上。 */
    @Override
    protected boolean essentiaGesturesAtAll() {
        // 每次点击都重新问，不缓存答案：菜单读的是玩家看到的升级槽，
        // 卡被取出后下一次点击就没手势了，缓存的标志做不到。
        return menu.hasEssentiaAccessCard();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (paymentModeButton != null) {
            paymentModeButton.setCrystals(menu.crystalFirst());
        }
    }

    @Override
    public void init() {
        super.init();
        // AE2 的 [getGuiDisplayName] 只要 title 非空就用 title 顶掉样式文档里的值，改文档换不掉顶行，
        // 只能走 [setTextContent] 覆写：顶行给通用「终端」，终端自己的名字下移到格子上方。
        setTextContent(CRAFTING_GRID_TITLE, terminalName);
        var subtitle = getStyle().getText().get(CRAFTING_GRID_TITLE);
        if (subtitle != null) {
            // 上限按左半边算：右半边让给两行 vis 文字，标题不能压过去。
            subtitle.setScale(Math.min(1.0F, GRID_TITLE_MAX_WIDTH / Math.max(1, font.width(terminalName))));
        }
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
            screen.crystals = payload.crystals();
            screen.auraVis = payload.auraVis();
            screen.discountPercent = payload.discountPercent();
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        drawAvailableAura(graphics);
        drawUpcomingVis(graphics);
        if (costs.isEmpty() && crystals.isEmpty()) {
            return;
        }
        // 条带由样式定位，解析出的点本就是窗口相对的：
        // [drawFG] 已在面板偏移的位姿里，再加一次 [leftPos] 会让这行偏出美术图一个窗口。
        WidgetStyle strip = getStyle().getWidget(VIS_COST_STRIP);
        if (strip == null) {
            return;
        }
        Point at = strip.resolve(new Rect2i(0, 0, imageWidth, imageHeight));
        // 基数仍是「水晶能占的格数」：六个图标按条带宽度等分。超过六件按件数等比缩小，下限 6 px；
        // 到下限还排不下的才会像以前那样丢掉尾巴。
        int wanted = Math.max(PartArcaneCraftingTerminal.CRYSTAL_SLOTS, costs.size() + crystals.size());
        int chip = Math.min(strip.getHeight(), strip.getWidth() / wanted);
        chip = Math.max(chip, Math.min(6, strip.getHeight()));
        if (chip <= 0) {
            return;
        }
        // 图标大小和间隔分开算：多出来的宽度摊进间隔里，图标本身不跟着长。
        // 整除的余数留不下就退回紧排，避免最后一件被挤出去。
        int pitch = chip;
        if (wanted > 1) {
            int slack = strip.getWidth() - chip * wanted;
            if (slack > 0) {
                pitch = chip + slack / (wanted - 1);
            }
        }
        // 从右往左排：第一个要素占住条带右端，剩下的往回排。
        // 单要素配方永远落在同一位置，不随要素列表长度漂移。
        int y = at.getY() + (strip.getHeight() - chip) / 2;
        int x = at.getX() + strip.getWidth() - chip;
        // Thaumaturge 的渲染器总按 [CHIP_UNITS] 见方画图标，不问界面尺寸，
        // 这里用位姿把它缩到条带容得下的大小。
        float shrink = (float) chip / CHIP_UNITS;
        chipHits.clear();
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
                // centivis 折算成整数 vis 并向上取整：1 centivis 也要一个 vis 才付得起，
                // 显示 0 会被读成免费。数字以图标为单位，靠位姿缩放。
                int vis = (cost.centivis() + 99) / 100;
                String text = String.valueOf(vis);
                graphics.drawString(
                        font, text, CHIP_UNITS - font.width(text) + 1, CHIP_UNITS - 6, 0xFFFFFF, true);
                graphics.pose().popPose();
                chipHits.add(new ChipHit(x, y, chip, List.of(
                        aspectName(aspect),
                        Component.translatable(
                                "gui.thaumicenergistics_ce.arcane_terminal.chip.vis", vis))));
            }
            x -= pitch;
        }
        // 晶体接着要素往左排：单要素配方仍占住条带右端，不因为多了晶体而挪位。
        // 晶体是物品，画的就是那一颗本身，同样是十六见方，跟着同一个位姿缩到条带容得下。
        for (ArcaneCraftCostPayload.CrystalCost cost : crystals) {
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
                graphics.renderItem(EssentiaCrystals.create(aspect, 1), 0, 0);
                // 颗数：晶体本身就是整颗的，这里不用像 centivis 那样向上折算。
                String text = String.valueOf(cost.count());
                graphics.drawString(
                        font, text, CHIP_UNITS - font.width(text) + 1, CHIP_UNITS - 6, 0xFFFFFF, true);
                graphics.pose().popPose();
                chipHits.add(new ChipHit(x, y, chip, List.of(
                        aspectName(aspect),
                        Component.translatable(
                                "gui.thaumicenergistics_ce.arcane_terminal.chip.crystals", cost.count()))));
            }
            x -= pitch;
        }
    }
    /**
     * 「可用 vis」那一行。灵气不够这次合成时改用工作台的告警色，
     * 和 {@code ArcaneWorkbenchScreen} 在同一个条件下换同一个颜色。
     */
    private void drawAvailableAura(GuiGraphics graphics) {
        WidgetStyle strip = getStyle().getWidget(VIS_AVAILABLE_STRIP);
        if (strip == null) return;
        Point at = strip.resolve(new Rect2i(0, 0, imageWidth, imageHeight));
        Component text = Component.translatable("gui.thaumaturge.arcane_workbench.vis_available", menu.availableAuraVis);
        drawCentered(graphics, strip, at, text, auraEnough() ? VIS_AVAILABLE_COLOR : VIS_SHORT_COLOR);
    }

    /** 这一行要不要报「不够」：没有合成需求时永远是够的。 */
    private boolean auraEnough() {
        return auraVis <= 0 || menu.availableAuraVis >= auraVis;
    }

    /**
     * 「即将消耗」那一行：数值和「（未聚焦）」/「（n% 减免）」都沿用奥术工作台自己的文案键，
     * 两个界面说的才是同一句话。减免由服务端按未聚焦开销与实际开销的差算好发下来，
     * 客户端不重复一遍减免规则。
     * <p>配方匹配上但灵气、法杖要素或晶体不够时也要留着：那一行正是「差多少」的来源。
     */
    private void drawUpcomingVis(GuiGraphics graphics) {
        if (auraVis <= 0 && costs.isEmpty() && crystals.isEmpty()) {
            return;
        }
        WidgetStyle strip = getStyle().getWidget(VIS_UPCOMING_STRIP);
        if (strip == null) {
            return;
        }
        Point at = strip.resolve(new Rect2i(0, 0, imageWidth, imageHeight));
        Component text = discountPercent > 0
                ? Component.translatable(
                        "gui.thaumaturge.arcane_workbench.required_vis_discount", auraVis, discountPercent)
                : Component.translatable(
                        "gui.thaumaturge.arcane_workbench.required_vis_crude", auraVis);
        drawCentered(graphics, strip, at, text, VIS_COST_COLOR);
    }

    /** 一行文字在条带里居中并按宽度缩放；条带比文字窄时才会缩，正常尺寸下是原大。 */
    private void drawCentered(GuiGraphics graphics, WidgetStyle strip, Point at, Component text, int color) {
        float scale = Math.min(0.5F, (float) strip.getWidth() / Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(at.getX() + strip.getWidth() / 2.0F,
                at.getY() + (strip.getHeight() - font.lineHeight * scale) / 2.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, false);
        graphics.pose().popPose();
    }

    /**
     * 悬停提示画在 {@code super.render} 之后，坐标就是屏幕坐标，{@code isHovering} 会自己减掉面板偏移。
     * 文案全部取自 {@code TTTooltips}，与奥术工作台那几行是同一份。
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        List<Component> tooltip = hoveredVisTooltip(mouseX, mouseY);
        if (tooltip != null && !tooltip.isEmpty()) {
            drawTooltipWithHeader(graphics, mouseX, mouseY, tooltip);
        }
    }

    private @Nullable List<Component> hoveredVisTooltip(int mouseX, int mouseY) {
        // 费用条只认逐枚：每枚图标自己说清是什么、花多少，条带本身不再另有一句总说明。
        for (ChipHit hit : chipHits) {
            if (isHovering(hit.x(), hit.y(), hit.size(), hit.size(), mouseX, mouseY)) {
                return hit.tooltip();
            }
        }
        if (hovering(VIS_UPCOMING_STRIP, mouseX, mouseY)) {
            return discountPercent > 0
                    ? TTTooltips.workbenchVisCostDiscounted(discountPercent)
                    : TTTooltips.workbenchVisCostUnfocused(
                            Math.round((ThEArcanePattern.CRAFT_AURA_SURCHARGE - 1.0F) * 100.0F));
        }
        if (hovering(VIS_AVAILABLE_STRIP, mouseX, mouseY)) {
            // 参数是「显示灵气不足那句警告」，不是「够不够」：传反了会在这行说反话。
            return TTTooltips.workbenchVisAvailable(!auraEnough());
        }
        return null;
    }

    /** 要素名走 Thaumaturge 自己的翻译键，和手册、JEI 里写的是同一个词。 */
    private static Component aspectName(Holder<IAspect> aspect) {
        return Component.translatable("aspect.thaumaturge." + aspect.value().tag());
    }

    /** 样式文档里那一块矩形是否含鼠标；没写 width/height 的控件不参与。 */
    private boolean hovering(String widget, int mouseX, int mouseY) {
        WidgetStyle strip = getStyle().getWidget(widget);
        if (strip == null || strip.getWidth() <= 0 || strip.getHeight() <= 0) {
            return false;
        }
        Point at = strip.resolve(new Rect2i(0, 0, imageWidth, imageHeight));
        return isHovering(at.getX(), at.getY(), strip.getWidth(), strip.getHeight(), mouseX, mouseY);
    }

}
