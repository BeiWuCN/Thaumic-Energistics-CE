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
import thaumicenergistics_ce.recipe.arcane.EssentiaCrystals;
import thaumicenergistics_ce.client.GolemBackpackClientData;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import thaumicenergistics_ce.network.ClientboundReceiver;
import thaumicenergistics_ce.network.GolemBackpackPayload;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

public class ScreenArcaneCraftingTerminal extends ScreenEssentiaTerminalBase<MenuArcaneCraftingTerminal>
        implements ClientboundReceiver {

    private static final int CHIP_UNITS = AspectRendering.GUI_ICON_SIZE;

    private static final String VIS_COST_STRIP = "visCraftCost";

    private static final String CRAFTING_GRID_TITLE = "crafting_grid_title";

    private static @Nullable ScreenArcaneCraftingTerminal open;

    private List<ArcaneCraftCostPayload.AspectCost> costs = List.of();

    private List<ArcaneCraftCostPayload.CrystalCost> crystals = List.of();

    private final Component terminalName;

    public ScreenArcaneCraftingTerminal(
            MenuArcaneCraftingTerminal menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, Component.translatable("gui.ae2.Terminal"), style);
        terminalName = title;
    }

    @Override
    protected boolean essentiaGesturesAtAll() {
        return menu.hasEssentiaAccessCard();
    }

    @Override
    public void init() {
        super.init();
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
    public void drawFG(GuiGraphicsExtractor graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        drawAvailableAura(graphics);
        drawInheritedVisCard(graphics);
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
                String text = String.valueOf((cost.centivis() + 99) / 100);
                graphics.text(
                        font, text, CHIP_UNITS - font.width(text) + 1, CHIP_UNITS - 6, 0xFFFFFF, true);
                graphics.pose().popMatrix();
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
            }
            x -= chip;
        }
    }

    private void drawAvailableAura(GuiGraphicsExtractor graphics) {
        WidgetStyle strip = getStyle().getWidget("visAvailable");
        if (strip == null) {
            return;
        }
        Point at = strip.resolve(new Rect2i(0, 0, imageWidth, imageHeight));
        Component text = Component.translatable("gui.thaumaturge.arcane_workbench.vis_available",
                menu.availableAuraVis);
        float scale = Math.min(0.5F, (float) strip.getWidth() / Math.max(1, font.width(text)));
        graphics.pose().pushMatrix();
        graphics.pose().translate(at.getX() + strip.getWidth() / 2.0F,
                at.getY() + (strip.getHeight() - font.lineHeight * scale) / 2.0F);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, -font.width(text) / 2, 0, 0xFF6E6EEE, false);
        graphics.pose().popMatrix();
    }

    private void drawInheritedVisCard(GuiGraphicsExtractor graphics) {
        if (!menu.inheritedVisConnection) {
            return;
        }
        WidgetStyle badge = getStyle().getWidget("inheritedVisCard");
        if (badge != null) {
            Point at = badge.resolve(new Rect2i(0, 0, imageWidth, imageHeight));
            graphics.item(new net.minecraft.world.item.ItemStack(
                    thaumicenergistics_ce.registry.ModItems.VIS_CONNECTION_CARD.get()), at.getX(), at.getY());
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        WidgetStyle badge = getStyle().getWidget("inheritedVisCard");
        if (menu.inheritedVisConnection && badge != null) {
            Point at = badge.resolve(new Rect2i(0, 0, imageWidth, imageHeight));
            if (mouseX >= leftPos + at.getX() && mouseX < leftPos + at.getX() + 16
                    && mouseY >= topPos + at.getY() && mouseY < topPos + at.getY() + 16) {
                graphics.setTooltipForNextFrame(font, List.of(Component.translatable(
                        "gui.thaumicenergistics_ce.arcane_terminal.inherited_vis_card")),
                        java.util.Optional.empty(), mouseX, mouseY);
                return;
            }
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }
}
