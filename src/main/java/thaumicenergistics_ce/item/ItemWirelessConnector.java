package thaumicenergistics_ce.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProvider;
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProviderConnection;

/**
 * 无线绑定工具：建立与断开炼金供应器所使用的链接。
 * 两端式的链接需要有个东西把一端的身份带到另一端，所以这个工具
 * 持有一个坐标——已选中但尚未绑定的接收端——第二次点击才完成
 * 配对。潜行把读取与写入分开；没有它，走过一座祭坛就会重新绑定
 * 东西。
 */
public class ItemWirelessConnector extends Item {

    private static final String NBT_SELECTED = "SelectedReceiver";

    private static final String NBT_DIMENSION = "SelectedDimension";

    public ItemWirelessConnector(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        ItemStack tool = context.getItemInHand();
        var player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        boolean isReceiver = level.getBlockEntity(clicked) instanceof BlockEntityAlchemyProviderConnection;
        boolean isProvider = level.getBlockEntity(clicked) instanceof BlockEntityAlchemyProvider;
        if (!isReceiver && !isProvider) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!player.isShiftKeyDown()) {
            report(level, clicked, player, isReceiver, isProvider);
            return InteractionResult.SUCCESS;
        }

        if (isReceiver) {
            select(level, clicked, tool, player);
        } else {
            bind(level, clicked, tool, player);
        }
        return InteractionResult.SUCCESS;
    }

    private static void select(Level level, BlockPos receiver, ItemStack tool, Player player) {
        setSelection(tool, receiver, level.dimension().location().toString());
        player.displayClientMessage(
                Component.translatable("item.thaumicenergistics_ce.wireless_connector.selected",
                        receiver.getX(), receiver.getY(), receiver.getZ()),
                true);
    }

    /**
     * 这个工具当前持有的接收端，或 {@code null}：通过自定义数据组件读取，因为
     * 1.21 从 {@link ItemStack} 上移除了直接的 tag 访问器，而真正传输的是组件。
     */
    private static @Nullable CompoundTag selection(ItemStack tool) {
        CustomData data = tool.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    private static void setSelection(ItemStack tool, BlockPos receiver, String dimension) {
        CompoundTag tag = selection(tool);
        CompoundTag updated = tag == null ? new CompoundTag() : tag.copy();
        updated.putLong(NBT_SELECTED, receiver.asLong());
        updated.putString(NBT_DIMENSION, dimension);
        tool.set(DataComponents.CUSTOM_DATA, CustomData.of(updated));
    }

    private static void clearSelection(ItemStack tool) {
        CompoundTag tag = selection(tool);
        if (tag == null) {
            return;
        }
        CompoundTag updated = tag.copy();
        updated.remove(NBT_SELECTED);
        updated.remove(NBT_DIMENSION);
        tool.set(DataComponents.CUSTOM_DATA, CustomData.of(updated));
    }

    private static void bind(Level level, BlockPos provider, ItemStack tool, Player player) {
        CompoundTag tag = selection(tool);
        if (tag == null || !tag.contains(NBT_SELECTED)) {
            player.displayClientMessage(
                    Component.translatable("item.thaumicenergistics_ce.wireless_connector.no_selection"),
                    true);
            return;
        }
        String dimension = level.dimension().location().toString();
        if (!dimension.equals(tag.getString(NBT_DIMENSION))) {
            player.displayClientMessage(
                    Component.translatable("item.thaumicenergistics_ce.wireless_connector.wrong_dimension"),
                    true);
            return;
        }

        BlockPos receiverPos = BlockPos.of(tag.getLong(NBT_SELECTED));
        if (!(level.getBlockEntity(receiverPos) instanceof BlockEntityAlchemyProviderConnection receiver)) {
            player.displayClientMessage(
                    Component.translatable("item.thaumicenergistics_ce.wireless_connector.receiver_gone"),
                    true);
            clearSelection(tool);
            return;
        }

        String refusal = receiver.link(provider);
        if (refusal != null) {
            player.displayClientMessage(Component.literal(refusal).withStyle(ChatFormatting.RED), true);
            return;
        }
        clearSelection(tool);
        player.displayClientMessage(
                Component.translatable("item.thaumicenergistics_ce.wireless_connector.linked"), true);
    }

    private static void report(Level level, BlockPos pos, Player player,
            boolean isReceiver, boolean isProvider) {
        if (isReceiver && level.getBlockEntity(pos) instanceof BlockEntityAlchemyProviderConnection receiver) {
            BlockPos provider = receiver.linkedProvider();
            player.displayClientMessage(provider == null
                    ? Component.translatable("item.thaumicenergistics_ce.wireless_connector.receiver_unbound",
                            pos.getX(), pos.getY(), pos.getZ())
                    : Component.translatable("item.thaumicenergistics_ce.wireless_connector.receiver_bound",
                            pos.getX(), pos.getY(), pos.getZ(),
                            provider.getX(), provider.getY(), provider.getZ()),
                    false);
        } else if (level.getBlockEntity(pos) instanceof BlockEntityAlchemyProvider provider) {
            player.displayClientMessage(
                    Component.translatable("item.thaumicenergistics_ce.wireless_connector.provider_report",
                            pos.getX(), pos.getY(), pos.getZ(),
                            provider.linkedReceiverCount(),
                            BlockEntityAlchemyProvider.MAX_LINKED_RECEIVERS),
                    false);
        }
    }
}
