package thaumicenergistics_ce.client.gui;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.ScreenStyle;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.network.ArcaneCraftCostPayload;

/**
 * The Arcane Crafting Terminal's screen.
 * <ul>
 *   <li>No vis display yet, by choice: it ships with the craft, so drawn and charged share one source.
 *   <li>The style document is in AE2's namespace: {@code StyleManager} resolves against its own only.
 * </ul>
 */
public class ScreenArcaneCraftingTerminal extends MEStorageScreen<MenuArcaneCraftingTerminal> {

    private static final int ICON_SIZE = 14;

    /** Where the cost strip starts, relative to the screen's corner. */
    private static final int COST_X = 108;
    private static final int COST_Y = 96;

    /**
     * The only open instance, so an incoming cost finds its screen: a player has one menu, so this one
     * reference is the right screen rather than an approximation of it.
     */
    private static @Nullable ScreenArcaneCraftingTerminal open;

    /** Each aspect's share of the cost, in centivis, as last sent by the server. */
    private List<ArcaneCraftCostPayload.AspectCost> costs = List.of();

    public ScreenArcaneCraftingTerminal(
            MenuArcaneCraftingTerminal menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
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

    /** Called by the network handler when the server sends a new cost. */
    public static void acceptCost(ArcaneCraftCostPayload payload) {
        ScreenArcaneCraftingTerminal screen = open;
        // The id check matters: a packet can arrive just after the player closed this screen and opened
        // another, and applying it then would draw the previous grid's cost on the new one.
        if (screen != null && screen.getMenu().containerId == payload.containerId()) {
            screen.costs = payload.aspects();
        }
    }

    /**
     * Draws the vis cost beside the grid: one icon per aspect, the cost beneath, as a wand's vis reads.
     * {@code drawFG} because AE2 makes {@code renderLabels} final; the offsets passed are the same.
     */
    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        if (costs.isEmpty()) {
            return;
        }
        int x = leftPos + COST_X;
        int y = topPos + COST_Y;
        for (ArcaneCraftCostPayload.AspectCost cost : costs) {
            if (x + ICON_SIZE > leftPos + imageWidth) {
                // Out of room: fewer aspects shown beats drawing over the grid.
                break;
            }
            var aspect = Aspects.resolve(
                    menu.getPlayer().level(), ResourceKey.create(
                            IAspect.REGISTRY_KEY, cost.aspect()));
            if (aspect != null) {
                AspectRendering.renderGui(graphics, font, x, y, aspect, 0.0F);
                // Centivis to whole vis, rounded up: a cost of 1 centivis still needs a vis to pay it, and
                // showing 0 would say it is free.
                int vis = (cost.centivis() + 99) / 100;
                String text = String.valueOf(vis);
                graphics.drawString(
                        font, text, x + ICON_SIZE - font.width(text) + 1, y + ICON_SIZE - 6, 0xFFFFFF, true);
            }
            x += ICON_SIZE + 2;
        }
    }
}
