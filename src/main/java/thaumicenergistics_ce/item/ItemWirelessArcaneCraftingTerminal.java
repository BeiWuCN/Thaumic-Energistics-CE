package thaumicenergistics_ce.item;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.PartHelper;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.ItemMenuHostLocator;
import java.util.List;
import java.util.function.DoubleSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ArcaneTerminalLink;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 无线奥术合成终端：一个可携带的奥术工作台，显示它所绑定的已放置
 * 终端的网格。
 * 在该终端上潜行即可绑定两者，潜行左键则解除绑定：一个状态，两个
 * 位置。它的 vis 来自玩家周围的灵气，因为可携带的工作台没有方块
 * 可供抽取。
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
     * 只有已经装有奥术合成终端的方块才值得把潜行交给它：在别的
     * 部件上，这次点击会打到玩家并未瞄准的机器上。
     */
    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return holdsArcaneTerminal(level, pos);
    }

    /**
     * 当该方块在任意一面装有终端时返回 true。方法签名里没有面本身，所以
     * 装有终端的线缆会被整体打开；点击仍然只在装它的那一面上配对。
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
     * 配对是潜行手势，所以潜行也不再打开界面：没有这一条，一次点击会
     * 既绑定物品，又让玩家盯着一个并非他要的网格。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
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
     * 在物品上记住一个已放置的终端；维度也一并保存，因为两个终端可能
     * 持有相同的坐标。
     */
    @Override
    public void pairWith(ItemStack terminal, Level level, BlockPos pos, Direction side) {
        CompoundTag tag = bindingTag(terminal);
        CompoundTag updated = tag == null ? new CompoundTag() : tag.copy();
        updated.putString(NBT_DIMENSION, level.dimension().location().toString());
        updated.putLong(NBT_POS, pos.asLong());
        updated.putString(NBT_SIDE, side.getName());
        terminal.set(DataComponents.CUSTOM_DATA, CustomData.of(updated));
    }

    /**
     * 这个物品所配对的已放置终端，或 {@code null}——没有配对、它位于
     * 另一个维度、或它的区块未加载时。
     */
    public static @Nullable PartArcaneCraftingTerminal pairedTerminal(Level level, ItemStack terminal) {
        CompoundTag tag = bindingTag(terminal);
        if (tag == null || !tag.contains(NBT_POS)) {
            return null;
        }
        if (!level.dimension().location().toString().equals(tag.getString(NBT_DIMENSION))) {
            return null;
        }
        Direction side = Direction.byName(tag.getString(NBT_SIDE));
        BlockPos pos = BlockPos.of(tag.getLong(NBT_POS));
        if (side == null || !level.isLoaded(pos)) {
            return null;
        }
        IPart part = PartHelper.getPart(level, pos, side);
        return part instanceof PartArcaneCraftingTerminal placed ? placed : null;
    }

    /**
     * 忘记已配对的终端。只有当确实有一个可忘记的对象时才返回 true，这样第二次
     * 清除才不会像是做了什么事一样被通告给玩家。
     */
    public static boolean unbind(ItemStack terminal) {
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

    private static @Nullable CompoundTag bindingTag(ItemStack terminal) {
        CustomData data = terminal.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CompoundTag tag = bindingTag(stack);
        if (tag != null && tag.contains(NBT_POS)) {
            BlockPos pos = BlockPos.of(tag.getLong(NBT_POS));
            tooltip.add(Component.translatable(
                    "tooltip.thaumicenergistics_ce.wireless_arcane_crafting_terminal.paired",
                    pos.getX(), pos.getY(), pos.getZ()));
        }
    }
}
