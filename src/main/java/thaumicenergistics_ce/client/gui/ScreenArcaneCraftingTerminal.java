package thaumicenergistics_ce.client.gui;

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
import thaumicenergistics_ce.client.GolemBackpackClientData;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import thaumicenergistics_ce.network.ClientboundReceiver;
import thaumicenergistics_ce.network.GolemBackpackPayload;

/**
 * The Arcane Crafting Terminal's screen.
 * <ul>
 *   <li>No vis display yet, by choice: it ships with the craft, so drawn and charged share one source.
 *   <li>The style document is in AE2's namespace: {@code StyleManager} resolves against its own only.
 *   <li>The jar and phial gestures come from {@link ScreenEssentiaTerminalBase}, only with the card.
 * </ul>
 */
public class ScreenArcaneCraftingTerminal extends ScreenEssentiaTerminalBase<MenuArcaneCraftingTerminal>
        implements ClientboundReceiver {

    private static final int ICON_SIZE = 14;

    private static final int COST_X = 108;
    private static final int COST_Y = 96;

    private static @Nullable ScreenArcaneCraftingTerminal open;

    private List<ArcaneCraftCostPayload.AspectCost> costs = List.of();

    public ScreenArcaneCraftingTerminal(
            MenuArcaneCraftingTerminal menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    /** Without the card the terminal is an ordinary one: every gesture falls through to AE2's own. */
    @Override
    protected boolean essentiaGesturesAtAll() {
        // Asked on every click rather than remembered: the menu reads the upgrade slot the player sees, so
        // a card taken out stops the gestures on the next click and a stale flag could never say otherwise.
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
     * The installed sink is this class, so the receiver is the open screen and the id check below still
     * decides whether the payload is for it.
     */
    public static void acceptCost(ArcaneCraftCostPayload payload) {
        ScreenArcaneCraftingTerminal screen = open;
        // The id check matters: a packet can arrive just after the player closed this screen and opened
        // another, and applying it then would draw the previous grid's cost on the new one.
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
        int x = leftPos + COST_X;
        int y = topPos + COST_Y;
        for (ArcaneCraftCostPayload.AspectCost cost : costs) {
            if (x + ICON_SIZE > leftPos + imageWidth) {
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
