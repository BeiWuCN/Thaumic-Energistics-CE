package thaumicenergistics_ce.item;

import appeng.api.ids.AEComponents;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.PartHelper;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.ItemMenuHostLocator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ArcaneTerminalLink;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 无线奥术合成终端：一个能带走的奥术工作台。
 * 工作台来自配对的已放置终端，ME 网络来自 AE2 的无线访问点链接，两件事互不干涉。
 * 在已放置的终端上潜行就配对，潜行左键把配对和链接一起下掉。
 * vis 从玩家周围的灵气取，可携带的工作台没有方块可抽。
 */
public class ItemWirelessArcaneCraftingTerminal extends WirelessTerminalItem implements ArcaneTerminalLink {

    public static final double POWER_CAPACITY = 200_000;

    private static final String NBT_DIMENSION = "ArcaneDimension";

    private static final String NBT_POS = "ArcanePos";

    private static final String NBT_SIDE = "ArcaneSide";

    public ItemWirelessArcaneCraftingTerminal(DoubleSupplier powerCapacity, Item.Properties properties) {
        super(powerCapacity, properties);
    }

    /**
     * 只有真装了奥术合成终端的方块才值得把潜行交给它：别的部件上，这一击会打到玩家没瞄的机器。
     */
    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return holdsArcaneTerminal(level, pos);
    }

    /**
     * 该方块任意一面装有终端就返回 true。签名里没有面，
     * 装了终端的线缆会被整体打开；配对仍只认装了它的那一面。
     */
    private static boolean holdsArcaneTerminal(LevelReader level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof IPartHost host)) {
            return false;
        }
        if (host.getPart(null) instanceof PartArcaneCraftingTerminal) {
            return true;
        }
        for (Direction side : Direction.values()) {
            if (host.getPart(side) instanceof PartArcaneCraftingTerminal) {
                return true;
            }
        }
        return false;
    }

    /**
     * 配对是潜行手势，潜行也就不再打开界面：
     * 没有这一条，一次点击既绑定物品，又让玩家盯着一个不要的网格。
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        return super.use(level, player, hand);
    }

    @Override
    public MenuType<?> getMenuType() {
        return ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get();
    }

    @Override
    public @Nullable WirelessTerminalMenuHost<?> getMenuHost(
            Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hitResult) {
        return new WirelessArcaneCraftingTerminalMenuHost(
                this, player, locator, (p, subMenu) -> this.openFromInventory(p, locator, true));
    }

    /**
     * 在物品上记住一个已放置的终端；维度一起存，两个终端可能坐标相同。
     */
    @Override
    public void pairWith(ItemStack terminal, Level level, BlockPos pos, Direction side) {
        CompoundTag tag = bindingTag(terminal);
        CompoundTag updated = tag == null ? new CompoundTag() : tag.copy();
        updated.putString(NBT_DIMENSION, level.dimension().identifier().toString());
        updated.putLong(NBT_POS, pos.asLong());
        updated.putString(NBT_SIDE, side.getName());
        terminal.set(DataComponents.CUSTOM_DATA, CustomData.of(updated));
    }

    /**
     * 这个物品配对的已放置终端；没有配对、在另一个维度、或区块没加载时是 {@code null}。
     */
    public static @Nullable PartArcaneCraftingTerminal pairedTerminal(Level level, ItemStack terminal) {
        CompoundTag tag = bindingTag(terminal);
        if (tag == null || !tag.contains(NBT_POS)) {
            return null;
        }
        if (!level.dimension().identifier().toString().equals(tag.getStringOr(NBT_DIMENSION, ""))) {
            return null;
        }
        Direction side = Direction.byName(tag.getStringOr(NBT_SIDE, ""));
        BlockPos pos = BlockPos.of(tag.getLongOr(NBT_POS, 0L));
        if (side == null || !level.isLoaded(pos)) {
            return null;
        }
        IPart part = PartHelper.getPart(level, pos, side);
        return part instanceof PartArcaneCraftingTerminal placed ? placed : null;
    }

    /**
     * 忘掉已配对的终端，**和它的 AE2 链接**。真有一个可忘的才返回 true，
     * 第二次清除才不会当成做了事通告给玩家。
     */
    public static boolean unbind(ItemStack terminal) {
        boolean cleared = clearPairing(terminal);
        if (isLinked(terminal)) {
            // 走 AE2 自己的解除，和访问点链接槽用的是同一段代码。
            WirelessTerminalItem.LINKABLE_HANDLER.unlink(terminal);
            cleared = true;
        }
        return cleared;
    }

    /** 只清配对那三个键，物品上别的自定义数据不碰。 */
    private static boolean clearPairing(ItemStack terminal) {
        CompoundTag tag = bindingTag(terminal);
        if (tag == null || !tag.contains(NBT_POS)) {
            return false;
        }
        tag.remove(NBT_DIMENSION);
        tag.remove(NBT_POS);
        tag.remove(NBT_SIDE);
        if (tag.isEmpty()) {
            terminal.remove(DataComponents.CUSTOM_DATA);
        } else {
            terminal.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return true;
    }

    /** 有没有 AE2 的访问点链接，也就是终端看不看得到一张网络。 */
    private static boolean isLinked(ItemStack terminal) {
        return terminal.get(AEComponents.WIRELESS_LINK_TARGET) != null;
    }

    private static @Nullable CompoundTag bindingTag(ItemStack terminal) {
        CustomData data = terminal.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        CompoundTag tag = bindingTag(stack);
        if (tag != null && tag.contains(NBT_POS)) {
            BlockPos pos = BlockPos.of(tag.getLongOr(NBT_POS, 0L));
            tooltip.accept(Component.translatable(
                    "tooltip.thaumicenergistics_ce.wireless_arcane_crafting_terminal.paired",
                    pos.getX(), pos.getY(), pos.getZ()));
        }
    }
}
