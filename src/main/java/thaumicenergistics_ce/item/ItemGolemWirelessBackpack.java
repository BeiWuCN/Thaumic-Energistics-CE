package thaumicenergistics_ce.item;

import appeng.api.features.IGridLinkableHandler;
import appeng.api.ids.AEComponents;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A wireless link to an ME network, in a form a golem can carry.
 * <ul>
 * <li>It is AE2's own link: a {@link GlobalPos} in {@link AEComponents#WIRELESS_LINK_TARGET}.
 * <li>{@link #LINKABLE_HANDLER} is what the memory card asks for; equipping is a click on the golem.
 * <li>Not Thaumaturge's accessory registry: {@code ItemGolemAccessory} is final, so the link lives in data.
 * </ul>
 */
public class ItemGolemWirelessBackpack extends Item {

    /** Handed to AE2 so a memory card can link and unlink this item. */
    public static final IGridLinkableHandler LINKABLE_HANDLER = new LinkableHandler();

    public ItemGolemWirelessBackpack(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** The network this backpack points at, or null if it has never been linked. */
    public GlobalPos getLinkedPosition(ItemStack stack) {
        return stack.get(AEComponents.WIRELESS_LINK_TARGET);
    }

    public static boolean isLinked(ItemStack stack) {
        return stack.get(AEComponents.WIRELESS_LINK_TARGET) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        // AE2's own wording for this, so a linked backpack reads like a linked wireless terminal.
        tooltip.add(isLinked(stack)
                ? Tooltips.of(GuiText.Linked, Tooltips.GREEN)
                : Tooltips.of(GuiText.Unlinked, Tooltips.RED));
    }

    /** Straight from the reference implementation, and the only shape AE2's interface allows. */
    private static final class LinkableHandler implements IGridLinkableHandler {
        @Override
        public boolean canLink(ItemStack stack) {
            return stack.getItem() instanceof ItemGolemWirelessBackpack;
        }

        @Override
        public void link(ItemStack stack, GlobalPos pos) {
            stack.set(AEComponents.WIRELESS_LINK_TARGET, pos);
        }

        @Override
        public void unlink(ItemStack stack) {
            stack.remove(AEComponents.WIRELESS_LINK_TARGET);
        }
    }
}
