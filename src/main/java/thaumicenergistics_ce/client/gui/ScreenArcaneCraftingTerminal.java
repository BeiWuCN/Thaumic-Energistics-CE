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
 *   <li>The cost row in the strip under the craft result is the whole vis display: no aura, by choice.
 *   <li>The style document is in AE2's namespace: {@code StyleManager} resolves against its own only.
 *   <li>The jar and phial gestures come from {@link ScreenEssentiaTerminalBase}, only with the card.
 * </ul>
 */
public class ScreenArcaneCraftingTerminal extends ScreenEssentiaTerminalBase<MenuArcaneCraftingTerminal>
        implements ClientboundReceiver {

    private static final int CHIP_UNITS = AspectRendering.GUI_ICON_SIZE;

    private static final int COST_ICON = 11;

    /**
     * The strip the chips go in, in window coordinates: the style's bottom section draws its art from the
     * texture row 71 down, and the strip is texture x 97..166, y 134..151 there.
     */
    private static final int COST_LEFT = 97;
    private static final int COST_RIGHT = 166;
    private static final int COST_STRIP_HEIGHT = 17;
    private static final int COST_STRIP_FROM_BOTTOM = 117;

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
        // Right to left: the first aspect holds the strip's right end and the rest run back along it,
        // so a one aspect recipe always lands in the same place instead of drifting with the list length.
        int y = topPos + imageHeight - COST_STRIP_FROM_BOTTOM + (COST_STRIP_HEIGHT - COST_ICON) / 2;
        int x = leftPos + COST_RIGHT - COST_ICON;
        // Thaumaturge's renderer draws a chip at CHIP_UNITS square whatever the screen wants, so the pose
        // shrinks it to COST_ICON: six primal aspects come to 66 of the strip's 69 columns.
        float shrink = (float) COST_ICON / CHIP_UNITS;
        for (ArcaneCraftCostPayload.AspectCost cost : costs) {
            if (x < leftPos + COST_LEFT) {
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
                // Centivis to whole vis, rounded up: a cost of 1 centivis still needs a vis to pay it, and
                // showing 0 would say it is free. The number sits in chip units; the pose scales it down.
                String text = String.valueOf((cost.centivis() + 99) / 100);
                graphics.drawString(
                        font, text, CHIP_UNITS - font.width(text) + 1, CHIP_UNITS - 6, 0xFFFFFF, true);
                graphics.pose().popPose();
            }
            x -= COST_ICON;
        }
    }
}
