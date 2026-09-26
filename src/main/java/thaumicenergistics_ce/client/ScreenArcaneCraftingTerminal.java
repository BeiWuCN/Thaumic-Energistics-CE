package thaumicenergistics_ce.client;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.style.ScreenStyle;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;

/**
 * The Arcane Crafting Terminal's screen.
 *
 * <p>Everything visible so far is AE2's, the same as the Essentia Terminal: the item list, the search box,
 * the sort controls, the scrollbar. The one addition the screen will need is the vis cost - an arcane recipe
 * is paid for partly in vis, and a player about to craft has to be able to see whether the wand in the slot
 * can afford it, and in which aspects.
 *
 * <p><b>Current state: no vis display yet.</b> It is deliberately left out rather than faked. The cost
 * comes from Thaumaturge's arcane crafting transaction, and drawing a number that is not the number the
 * craft will actually charge would be worse than drawing nothing - a player would trust it. It goes in
 * together with the craft itself, so the figure on screen and the figure charged come from one source.
 *
 * <p>The style document lives in AE2's namespace rather than this mod's, which is not a mistake: AE2's
 * {@code StyleManager} resolves a style path against its own namespace only, so a terminal style cannot be
 * named by this addon. That is also why the path below has no namespace in it.
 */
public class ScreenArcaneCraftingTerminal extends MEStorageScreen<MenuArcaneCraftingTerminal> {

    private static final int ICON_SIZE = 14;

    /** Where the cost strip starts, relative to the screen's corner. */
    private static final int COST_X = 108;
    private static final int COST_Y = 96;

    /**
     * The only open instance, so an incoming cost can find the screen it belongs to.
     *
     * <p>There is exactly one terminal screen open at a time - a player has one menu - so a single
     * reference is not an approximation of "the right screen", it is the right screen. The alternative
     * would be handing the screen to a packet handler through a registry, which is more machinery for the
     * same answer.
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
     * Draws the vis cost beside the crafting grid.
     *
     * <p>One icon per aspect with the cost beneath it, which is how a player already reads a wand's vis.
     * Nothing is drawn for a grid that matches no recipe, because an empty cost means "nothing to pay"
     * rather than "nothing needed".
     *
     * <p>{@code drawFG} rather than {@code renderLabels}: AE2 makes its own {@code renderLabels} final,
     * because it already uses it to lay out the style document's text and slot backgrounds. This is the
     * hook it leaves open, and the offsets it passes are the same GUI-relative ones.
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
                break; // out of room: better to show fewer than to draw over the grid
            }
            var aspect = com.leclowndu93150.thaumaturge.api.aspect.Aspects.resolve(
                    menu.getPlayer().level(), net.minecraft.resources.ResourceKey.create(
                            com.leclowndu93150.thaumaturge.api.aspect.IAspect.REGISTRY_KEY, cost.aspect()));
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
