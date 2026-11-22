package com.beiwu.thaumicenergistics_ce.client.gui;

import appeng.client.Point;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.recipe.arcane.EssentiaCrystals;
import com.beiwu.thaumicenergistics_ce.client.GolemBackpackClientData;
import com.beiwu.thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import com.beiwu.thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import com.beiwu.thaumicenergistics_ce.network.ClientboundReceiver;
import com.beiwu.thaumicenergistics_ce.network.GolemBackpackPayload;
import com.beiwu.thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

public class ScreenArcaneCraftingTerminal extends ScreenEssentiaTerminalBase<MenuArcaneCraftingTerminal>
        implements ClientboundReceiver {

    private static final int CHIP_UNITS = AspectRendering.GUI_ICON_SIZE;

    private static final String VIS_COST_STRIP = "visCraftCost";

    private static final String CRAFTING_GRID_TITLE = "crafting_grid_title";

    private static @Nullable ScreenArcaneCraftingTerminal open;

    private List<ArcaneCraftCostPayload.AspectCost> costs = List.of();

    private List<ArcaneCraftCostPayload.CrystalCost> crystals = List.of();

    private final Component terminalName;

    private final PaymentModeButton paymentModeButton;
    private final List<CostIcon> costIcons = new ArrayList<>();

    private record CostIcon(int x, int y, int size, List<Component> tooltip) {}

    public ScreenArcaneCraftingTerminal(
            MenuArcaneCraftingTerminal menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, Component.translatable("gui.ae2.Terminal"), style);
        paymentModeButton = addToLeftToolbar(new PaymentModeButton(button -> menu.togglePaymentSource()));
        terminalName = title;
    }

    @Override
    protected boolean essentiaGesturesAtAll() {
        return menu.hasEssentiaAccessCard();
    }

    @Override
    public void init() {
        super.init();
        var result = menu.resultSlot();
        if (result != null) {
            int x = leftPos + result.x + 22;
            int y = topPos + result.y;
            addRenderableWidget(new ArcaneGridReturnButton(x, y - 2, true,
                    Component.translatable("gui.thaumicenergistics_ce.arcane_terminal.return_to_network"),
                    () -> menu.returnCraftingGrid(true)));
            addRenderableWidget(new ArcaneGridReturnButton(x, y + 10, false,
                    Component.translatable("gui.thaumicenergistics_ce.arcane_terminal.return_to_inventory"),
                    () -> menu.returnCraftingGrid(false)));
        }
        // AE2 的 [getGuiDisplayName] 只要 title 非空就用 title 顶掉样式文档里的值，改文档换不掉顶行，
        // 只能走 [setTextContent] 覆写：顶行给通用「终端」，终端自己的名字下移到格子上方。
        setTextContent(CRAFTING_GRID_TITLE, terminalName);
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

    public static void acceptCost(ArcaneCraftCostPayload payload) {
        ScreenArcaneCraftingTerminal screen = open;
        if (screen != null && screen.getMenu().containerId == payload.containerId()) {
            screen.costs = payload.aspects();
            screen.crystals = payload.crystals();
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        paymentModeButton.setCrystals(menu.crystalFirst);
    }

    @Override
    public void drawFG(GuiGraphicsExtractor graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        costIcons.clear();
        if (menu.requiredAuraVis > 0) {
            Component required = menu.auraDiscountPercent > 0
                    ? Component.translatable("gui.thaumaturge.arcane_workbench.required_vis_discount",
                            menu.requiredAuraVis, menu.auraDiscountPercent)
                    : Component.translatable("gui.thaumaturge.arcane_workbench.required_vis_crude",
                            menu.requiredAuraVis);
            drawAuraText(graphics, "visRequired", required, 0xFFC1C1FF, 0.55F);
        }
        drawAuraText(graphics, "visAvailable",
                Component.translatable("gui.thaumaturge.arcane_workbench.vis_available", menu.availableAuraVis),
                0xFF6E6EEE, 0.6F);
        if (menu.requiredAuraVis > menu.availableAuraVis) {
            drawAuraText(graphics, "visShortage",
                    Component.translatable("gui.thaumicenergistics_ce.arcane_terminal.aura_shortage"),
                    0xFFFF5555, 0.5F);
        }
        if (costs.isEmpty() && crystals.isEmpty()) {
            return;
        }
        WidgetStyle strip = getStyle().getWidget(VIS_COST_STRIP);
        if (strip == null) {
            return;
        }
        Point at = strip.resolve(new Rect2i(0, 0, imageWidth, imageHeight));
        int wanted = Math.max(PartArcaneCraftingTerminal.CRYSTAL_SLOTS, costs.size() + crystals.size());
        int chip = Math.min(strip.getHeight(), strip.getWidth() / wanted);
        chip = Math.max(chip, Math.min(6, strip.getHeight()));
        if (chip <= 0) {
            return;
        }
        int y = at.getY() + (strip.getHeight() - chip) / 2;
        int x = at.getX() + strip.getWidth() - chip;
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
                int vis = (cost.centivis() + 99) / 100;
                String text = String.valueOf(vis);
                graphics.text(
                        font, text, CHIP_UNITS - font.width(text) + 1, CHIP_UNITS - 6, 0xFFFFFF, true);
                graphics.pose().popMatrix();
                costIcons.add(new CostIcon(x, y, chip, List.of(
                        Component.translatable("aspect.thaumaturge." + aspect.value().tag()),
                        Component.translatable("gui.thaumicenergistics_ce.arcane_terminal.chip.vis", vis))));
            }
            x -= chip;
        }
        for (ArcaneCraftCostPayload.CrystalCost cost : crystals) {
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
                graphics.item(EssentiaCrystals.create(aspect, 1), 0, 0);
                String text = String.valueOf(cost.count());
                graphics.text(
                        font, text, CHIP_UNITS - font.width(text) + 1, CHIP_UNITS - 6, 0xFFFFFF, true);
                graphics.pose().popMatrix();
                costIcons.add(new CostIcon(x, y, chip, List.of(
                        Component.translatable("aspect.thaumaturge." + aspect.value().tag()),
                        Component.translatable("gui.thaumicenergistics_ce.arcane_terminal.chip.crystals",
                                cost.count()))));
            }
            x -= chip;
        }
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractContents(graphics, mouseX, mouseY, partialTick);
        for (CostIcon icon : costIcons) {
            if (new Rect2i(leftPos + icon.x(), topPos + icon.y(), icon.size(), icon.size()).contains(mouseX, mouseY)) {
                drawTooltipWithHeader(graphics, mouseX, mouseY, icon.tooltip());
                break;
            }
        }
    }

    private void drawAuraText(GuiGraphicsExtractor graphics, String widget, Component text, int color, float maxScale) {
        WidgetStyle strip = getStyle().getWidget(widget);
        if (strip == null) return;
        Point at = strip.resolve(new Rect2i(0, 0, imageWidth, imageHeight));
        float scale = Math.min(maxScale, (float) strip.getWidth() / Math.max(1, font.width(text)));
        graphics.pose().pushMatrix();
        graphics.pose().translate(at.getX() + strip.getWidth() / 2.0F,
                at.getY() + (strip.getHeight() - font.lineHeight * scale) / 2.0F);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, -font.width(text) / 2, 0, color, false);
        graphics.pose().popMatrix();
    }


    @Override
    protected guideme.PageAnchor getHelpTopic() {
        return TECEGuide.page("arcane-crafting");
    }

    @Override
    protected void openHelp() {
        TECEGuide.open(menu.getPlayer(), "arcane-crafting");
    }

}
