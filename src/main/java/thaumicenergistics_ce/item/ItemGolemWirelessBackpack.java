package thaumicenergistics_ce.item;

import appeng.api.features.IGridLinkableHandler;
import appeng.api.ids.AEComponents;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * 一条通往 ME 网络的无线连接，形态是傀儡能携带的东西。
 * 连接是 AE2 自己的：一个 {@link GlobalPos} 存在 {@link AEComponents#WIRELESS_LINK_TARGET}。
 * {@link #LINKABLE_HANDLER} 是内存卡请求的对象；对傀儡点一下就算装备。
 * Thaumaturge 的饰品注册表用不了，{@code ItemGolemAccessory} 是 final；连接存在傀儡的数据里。
 */
public class ItemGolemWirelessBackpack extends Item {

    /** 交给 AE2，内存卡靠它链接与解除链接这个物品。 */
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
        // 这里用 AE2 自己的措辞，已链接的背包读起来像已链接的无线终端。
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
