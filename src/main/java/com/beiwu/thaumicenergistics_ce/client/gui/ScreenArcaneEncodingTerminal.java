package com.beiwu.thaumicenergistics_ce.client.gui;

import appeng.api.config.ActionItems;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.ToggleButton;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.menu.MenuArcaneEncodingTerminal;
import com.beiwu.thaumicenergistics_ce.recipe.arcane.ThEArcanePattern;

/**
 * ME 奥术编码终端：AE2 样板编码终端的外壳，右侧常驻知识核心样板面板。
 * 没有模式 tab，也没有任何 AE2 意义上的样板槽——奥术合成里知识核心就是成品，
 * 它挂在背景图右侧那个小凹槽里，网格解析出的 Vis 与要素需求画在底板右下角的空带上。
 */
public class ScreenArcaneEncodingTerminal extends ScreenEssentiaTerminalBase<MenuArcaneEncodingTerminal> {

    /** AE2 pattern_modes.png 的副本，两块内容：左上的合成编码底板、右侧的 3x7 样板槽区。 */
    private static final ResourceLocation PANEL_SHEET =
            ResourceLocation.fromNamespaceAndPath("thaumicenergistics_ce", "textures/gui/arcane_encoding_modes.png");

    /**
     * 知识核心槽的空槽覆盖层。本 mod 自己的 states.png 在 (240,0) 画了这张核心图标，
     * 直接用它——和 AE2 的 states.png 不是一张图，别拿 Icon.XXX 去取。
     */
    private static final ResourceLocation CORE_SLOT_SHEET =
            ResourceLocation.fromNamespaceAndPath("thaumicenergistics_ce", "textures/gui/states.png");
    private static final int CORE_SLOT_U = 240;
    private static final int CORE_SLOT_V = 0;

    /** 网格上方那行副标题的控件名，与样式文档的 {@code text} 键同名。 */
    private static final String CRAFTING_GRID_TITLE = "crafting_grid_title";

    /** 终端自己的名字，构造器传进来但不再用于副标题；留着给后续提示文案用。 */
    private final Component terminalName;

    /**
     * 奥术合成编码底板：3x3 网格 + 箭头 + 产物框，即 AE2 的 CraftingEncodingPanel.BG 的 src(0,0,124,66)。
     * 比 AE2 的 modePanel0（left 9 / bottom 166）向左、向下各偏 1px：这张图是按本 mod 的背景重切的，
     * 3x3 网格仍按 AE2 的 15/158 注册，只有底图自己要压到凹陷区里。
     * bottom 是从底边往上量的，所以"向下"是把它减 1。
     */
    private static final int MODE_U = 0;
    private static final int MODE_V = 0;
    private static final int MODE_WIDTH = 124;
    private static final int MODE_HEIGHT = 66;

    private static final int MODE_X = 8;
    private static final int MODE_BOTTOM = 165;

    /**
     * 底板上那个产物框的位置。奥术合成里知识核心顶掉了成品样板的位置，框里不再注册槽位，
     * 只剩 Vis 那一行文字拿它当对齐基准。
     */
    private static final int FRAME_LEFT = 106;
    private static final int FRAME_BOTTOM = 140;

    /**
     * 右侧常驻的 3 列 x 7 行样板槽面板，整块是 src(197,0,59,138)：
     * 里面 (197,5,54,126) 才是 21 个槽位（18px 一格、含 1px 边框），外面各边是面板自己的边框。
     */
    private static final int PANEL_U = 197;
    private static final int PANEL_V = 0;
    private static final int PANEL_WIDTH = 59;
    private static final int PANEL_HEIGHT = 138;

    /**
     * 槽位区跟底部锚定，不跟顶部：窗口变高时多出来的是上方存储网格的行，
     * 这块面板必须留在编码底板右边不动。槽位注册用同一套：left 175 / bottom 131。
     * 面板顶边在纹理 y=113，也就是槽位顶（120）再往上 7px 的那圈外框。
     * 面板高 138，底边正好压在 imageHeight 上，和背景底边齐平。
     */
    private static final int PANEL_X = 174;
    private static final int PANEL_BOTTOM = 138;

    /**
     * 要素消耗只能画在背景图 68,131 起 64x21 那块空带上（底板的右下角）。
     */
    private static final int ASPECT_BAND_X = 68;
    private static final int ASPECT_BAND_BOTTOM = 120;
    private static final int ASPECT_BAND_WIDTH = 64;
    private static final int ASPECT_BAND_HEIGHT = 21;

    /** 六种元要素，条带最多排这么多。 */
    private static final int ASPECT_MAX = 6;

    /** 编码按钮。做成 AE2 那样的 16x16 图标按钮：核心槽底下只有 48px 宽，放不下文字。 */
    private ActionButton encodeButton;

    /** AE2 合成编码页同款小叉：清空编码网格。 */
    private ActionButton clearButton;

    /** 允许替代品：配方材料写的是标签时，样板收下整个标签而不只是显示的那个成员。 */
    private ToggleButton substitutionsButton;

    /** 允许流体替代：桶装输入可以用网络里的流体顶替。 */
    private ToggleButton fluidSubstitutionsButton;

    /**
     * 控件必须在这里加，不能放到 {@code init()} 里：{@code AEBaseScreen.init()} 是在末尾才调
     * {@code widgets.populateScreen(...)} 的，之后再加的控件既不会被定位也不会被画出来。
     */
    public ScreenArcaneEncodingTerminal(
            MenuArcaneEncodingTerminal menu, Inventory inventory, Component title, ScreenStyle style) {
        // 标题在这里换掉，不能放到 init() 里：MEStorageScreen.updateBeforeRender() 每帧
        // 都会把构造器传进来的 title 写回 dialog_title，在 init() 里 setTextContent
        // 只能管到第一帧之前的那一小会儿。面板版和无线版都走这里，标题才都是「终端」；
        // 机器的用途交给样式里的副标题去说。
        super(menu, inventory, Component.translatable("gui.ae2.Terminal"), style);
        // 顶行占了「终端」，物品名就下移到网格上方那行，和合成终端一个做法。
        // 原先那行写死「奥术合成编码」，与终端自己的名字对不上。
        this.terminalName = title;

        // 状态走 tooltip 第二行，不写按钮上：那一格只有 16px，长文案会压到左边的编码网格。
        encodeButton = new ActionButton(ActionItems.ENCODE, menu::encode) {
            @Override
            public List<Component> getTooltipMessage() {
                return encodeTooltip();
            }
        };
        widgets.add("encodePattern", encodeButton);

        // 与 AE2 CraftingEncodingPanel 完全一致：ActionItems.S_CLOSE（Icon.S_CLEAR，8x8）
        // 加半尺寸、去底框。这三项缺一不可，换成 CLOSE 会画成 16x16 的大叉。
        clearButton = new ActionButton(ActionItems.S_CLOSE, menu::clearGrid);
        clearButton.setHalfSize(true);
        clearButton.setDisableBackground(true);
        widgets.add("craftingClearPattern", clearButton);

        substitutionsButton = toggle(
                Icon.S_SUBSTITUTION_ENABLED,
                Icon.S_SUBSTITUTION_DISABLED,
                menu::setSubstitution,
                "gui.tooltips.ae2.SubstitutionsOn",
                "gui.tooltips.ae2.SubstitutionsOff",
                "gui.tooltips.ae2.SubstitutionsDescEnabled",
                "gui.tooltips.ae2.SubstitutionsDescDisabled");
        widgets.add("craftingSubstitutions", substitutionsButton);

        fluidSubstitutionsButton = toggle(
                Icon.S_FLUID_SUBSTITUTION_ENABLED,
                Icon.S_FLUID_SUBSTITUTION_DISABLED,
                menu::setFluidSubstitution,
                "gui.tooltips.ae2.FluidSubstitutions",
                "gui.tooltips.ae2.FluidSubstitutions",
                "gui.tooltips.ae2.FluidSubstitutionsDescEnabled",
                "gui.tooltips.ae2.FluidSubstitutionsDescDisabled");
        widgets.add("craftingFluidSubstitutions", fluidSubstitutionsButton);
    }

    /**
     * 顶行的「终端」已经由构造器交给 {@code title}。副标题不在这里写：
     * 它属于样式文档，且无线版与面板版共用同一句（AE2 的无线终端也只覆盖 dialog_title）。
     * 之前这里用菜单标题覆写，导致面板版与无线版各自顶着一串物品名，比其他终端长一截。
     */
    @Override
    public void init() {
        super.init();
        var subtitle = getStyle().getText().get(CRAFTING_GRID_TITLE);
        if (subtitle != null) {
            // 和 AE2 合成终端同一套：整行宽度、最多原大，长名字才缩。
            Component shown = subtitle.getText();
            subtitle.setScale(Math.min(1.0F, (imageWidth - 16.0F) / Math.max(1, font.width(shown))));
        }
    }

    /**
     * 装了源质访问卡才有罐与药瓶手势：存储栏里的源质行、右键安瓿存取都归这个开关管。
     * 每次点击重新问，不缓存：卡被取出后下一次点击就没手势了。
     */
    @Override
    protected boolean essentiaGesturesAtAll() {
        return menu.hasEssentiaAccessCard();
    }

    /** 帮助按钮指向本 mod 的编码终端指南页，而不是 AE2 自己的。 */
    @Override
    protected guideme.PageAnchor getHelpTopic() {
        return TECEGuide.page("arcane-encoding");
    }

    @Override
    protected void openHelp() {
        TECEGuide.open(menu.getPlayer(), "arcane-encoding");
    }

    /** 和清空按钮同一套：半尺寸 + 去掉底框，缺任一项都会画成压满整格的大图标。 */
    private ToggleButton toggle(
            Icon on,
            Icon off,
            ToggleButton.Listener listener,
            String onKey,
            String offKey,
            String onDescKey,
            String offDescKey) {
        ToggleButton button = new ToggleButton(on, off, listener);
        button.setHalfSize(true);
        button.setDisableBackground(true);
        button.setTooltipOn(List.of(Component.translatable(onKey), Component.translatable(onDescKey)));
        button.setTooltipOff(List.of(Component.translatable(offKey), Component.translatable(offDescKey)));
        return button;
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTick) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTick);
        // 奥术合成编码底板，画在槽位背景之下。
        graphics.blit(
                PANEL_SHEET,
                leftPos + MODE_X,
                topPos + imageHeight - MODE_BOTTOM,
                MODE_U,
                MODE_V,
                MODE_WIDTH,
                MODE_HEIGHT);
        // 知识核心的 21 个样板槽：整块面板块连同外框一起画，槽位区只是它内部那 3x7 格。
        graphics.blit(
                PANEL_SHEET,
                leftPos + PANEL_X,
                topPos + imageHeight - PANEL_BOTTOM,
                PANEL_U,
                PANEL_V,
                PANEL_WIDTH,
                PANEL_HEIGHT);
        // 空槽补一层 AE2 的空样板水印（16x16，恰好和槽位同尺寸），有样板时让位给物品图标。
        // 用的是样板供应器/分子装配室里那张 BLANK（states.png 240,128），
        // 不是编码终端里表示"已编码"的 ENCODED（240,112）。
        for (Slot slot : menu.patternSlots()) {
            if (!slot.hasItem()) {
                Icon.BACKGROUND_BLANK_PATTERN
                        .getBlitter()
                        .dest(leftPos + slot.x, topPos + slot.y)
                        .blit(graphics);
            }
        }
        // 知识核心槽画自己的覆盖层：空槽提示"这里放核心"，不再拿样板水印顶替。
        Slot coreSlot = coreSlot();
        if (coreSlot != null && !coreSlot.hasItem()) {
            graphics.blit(
                    CORE_SLOT_SHEET,
                    leftPos + coreSlot.x,
                    topPos + coreSlot.y,
                    CORE_SLOT_U,
                    CORE_SLOT_V,
                    16,
                    16,
                    256,
                    256);
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        drawSelectionFrame(graphics);

        ThEArcanePattern pattern = menu.currentPattern();
        if (pattern == null) {
            return;
        }
        // drawFG 已经平移到 GUI 原点，所以这里用界面自身的局部坐标。

        // Vis 压在底板产物框正上方 2px，水平居中：那一列只有产物框这么宽，偏一点就压到箭头。
        String vis = pattern.chargedVis() + " Vis";
        int visX = FRAME_LEFT + 8 - font.width(vis) / 2;
        int visY = imageHeight - FRAME_BOTTOM - 4 - font.lineHeight;
        graphics.drawString(font, vis, visX, visY, 0xFF6E6EEE, false);

        drawAspectCosts(graphics, pattern);
    }

    /**
     * 要素消耗：六个 aspect 图标排一行，限制在 64x21 的空带里。
     * 图标边长按条带宽度除出来（64/6=10），数字用 ME 奥术合成终端那套
     * 白色带阴影写在图标右下角，而不是让 {@code AspectRendering} 用自己那层暗色小字。
     */
    private void drawAspectCosts(GuiGraphics graphics, ThEArcanePattern pattern) {
        int chip = Math.min(ASPECT_BAND_HEIGHT, ASPECT_BAND_WIDTH / ASPECT_MAX);
        if (chip <= 0) {
            return;
        }
        int pitch = ASPECT_MAX > 1 && ASPECT_BAND_WIDTH - chip * ASPECT_MAX > 0
                ? chip + (ASPECT_BAND_WIDTH - chip * ASPECT_MAX) / (ASPECT_MAX - 1)
                : chip;
        int y = imageHeight - ASPECT_BAND_BOTTOM + (ASPECT_BAND_HEIGHT - chip) / 2;
        int x = ASPECT_BAND_X + (ASPECT_BAND_WIDTH - pitch * ASPECT_MAX + pitch - chip) / 2;
        float shrink = (float) chip / AspectRendering.GUI_ICON_SIZE;
        int drawn = 0;
        for (AspectInstance entry : pattern.crystals().entries()) {
            if (drawn >= ASPECT_MAX) {
                break;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(shrink, shrink, 1.0F);
            // amount 传 0：数量自己画，Thaumaturge 那层暗色小字在半尺寸下看不清。
            AspectRendering.renderGui(graphics, font, 0, 0, entry.aspect(), 0.0F);
            String text = String.valueOf(Math.round(entry.amount()));
            graphics.drawString(
                    font,
                    text,
                    AspectRendering.GUI_ICON_SIZE - font.width(text) + 1,
                    AspectRendering.GUI_ICON_SIZE - 6,
                    0xFFFFFF,
                    true);
            graphics.pose().popPose();
            x += pitch;
            drawn++;
        }
    }

    /**
     * 选中样板的虹色外框：1px 描边绕槽位一圈，颜色沿周长走色相、随时间流动。
     * 画在槽位外面（slot.x-1 起 18px），不会盖住样板自己的图标。
     */
    private void drawSelectionFrame(GuiGraphics graphics) {
        List<Slot> slots = menu.patternSlots();
        int index = menu.selectedPattern;
        if (index < 0 || index >= slots.size()) {
            return;
        }
        Slot slot = slots.get(index);
        int x = slot.x - 1;
        int y = slot.y - 1;
        int size = 18;
        long tick = System.currentTimeMillis() / 24L;
        for (int i = 0; i < size; i++) {
            graphics.fill(x + i, y, x + i + 1, y + 1, rainbow(tick, i));
            graphics.fill(x + i, y + size - 1, x + i + 1, y + size, rainbow(tick, size * 3 - i));
        }
        for (int i = 1; i < size - 1; i++) {
            graphics.fill(x, y + i, x + 1, y + i + 1, rainbow(tick, size * 4 - i));
            graphics.fill(x + size - 1, y + i, x + size, y + i + 1, rainbow(tick, size + i));
        }
    }

    private static int rainbow(long tick, int offset) {
        float hue = Math.floorMod(tick + offset * 6L, 360L) / 360.0F;
        return 0xFF000000 | Mth.hsvToRgb(hue, 0.85F, 1.0F);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (encodeButton != null) {
            encodeButton.active = menu.hasCore
                    && (menu.gridEmpty()
                            ? menu.selectedPattern >= 0
                            : menu.status != MenuArcaneEncodingTerminal.STATUS_NO_RECIPE);
        }
        // 清空按钮不跟着网格状态置灰：{@code IconButton} 对 inactive 的按钮会把图标
        // 按 0.5 不透明度再叠一层，看着像盖了块灰布。清空本身是幂等的，空网格点了也没事。
        if (substitutionsButton != null) {
            substitutionsButton.setState(menu.substitution);
        }
        if (fluidSubstitutionsButton != null) {
            fluidSubstitutionsButton.setState(menu.fluidSubstitution);
        }
    }

    /**
     * 样板槽点击只做选中，不搬东西：那些槽位是只读镜像，真正的数据在核心物品里。
     * 再次点同一个槽取消选中。
     */
    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        var patternSlots = menu.patternSlots();
        int index = patternSlots.indexOf(slot);
        if (index >= 0) {
            menu.selectPattern(menu.selectedPattern == index ? -1 : index);
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    /**
     * 第一行直接用 AE2 样板编码终端那句 Encode Pattern，第二行才是这台机器自己的状态。
     * 没有额外状态（可以正常编码）时只留一行，免得多一行废话。
     */
    private List<Component> encodeTooltip() {
        List<Component> lines = new ArrayList<>(2);
        lines.add(Component.translatable("gui.tooltips.ae2.Encode"));
        Component state = encodeState();
        if (state != null) {
            lines.add(state);
        }
        return lines;
    }

    private @Nullable Component encodeState() {
        if (!menu.hasCore) {
            return label("no_core");
        }
        if (menu.gridEmpty()) {
            return label(menu.selectedPattern >= 0 ? "delete" : "empty");
        }
        return switch (menu.status) {
            // 网格填了但解析不出配方，AE2 那边就叫这个。
            case MenuArcaneEncodingTerminal.STATUS_NO_RECIPE -> Component.translatable("gui.ae2.InvalidPattern");
            case MenuArcaneEncodingTerminal.STATUS_CORE_FULL -> label("full");
            case MenuArcaneEncodingTerminal.STATUS_ALREADY_STORED -> label("stored");
            case MenuArcaneEncodingTerminal.STATUS_RESEARCH_LOCKED -> label("locked");
            default -> null;
        };
    }

    private Slot coreSlot() {
        var slots = menu.getSlots(appeng.menu.SlotSemantics.STORAGE);
        return slots.isEmpty() ? null : slots.getFirst();
    }

    /**
     * 给 JEI 的避让区：右侧样板面板比 195 宽的背景多伸出 38px，
     * 不申报的话 JEI 的物品列表和配方页会正好盖在面板上。
     * 坐标是屏幕绝对坐标，和 JEI 自己的槽位矩形同一套。
     */
    public List<Rect2i> jeiExtraAreas() {
        return List.of(new Rect2i(
                leftPos + PANEL_X,
                topPos + imageHeight - PANEL_BOTTOM,
                PANEL_WIDTH,
                PANEL_HEIGHT));
    }

    private static Component label(String key) {
        return Component.translatable("gui.thaumicenergistics_ce.arcane_encoding_terminal." + key);
    }
}
