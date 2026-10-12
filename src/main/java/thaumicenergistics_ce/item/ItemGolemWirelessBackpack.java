package thaumicenergistics_ce.item;

import appeng.api.features.IGridLinkableHandler;
import appeng.api.ids.AEComponents;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import java.util.function.Consumer;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public class ItemGolemWirelessBackpack extends Item {

    public static final IGridLinkableHandler LINKABLE_HANDLER = new LinkableHandler();

    public ItemGolemWirelessBackpack(Properties properties) {
        super(properties.stacksTo(1));
    }

    public GlobalPos getLinkedPosition(ItemStack stack) {
        return stack.get(AEComponents.WIRELESS_LINK_TARGET);
    }

    public static boolean isLinked(ItemStack stack) {
        return stack.get(AEComponents.WIRELESS_LINK_TARGET) != null;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(isLinked(stack)
                ? Tooltips.of(GuiText.Linked, Tooltips.GREEN)
                : Tooltips.of(GuiText.Unlinked, Tooltips.RED));
    }

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
