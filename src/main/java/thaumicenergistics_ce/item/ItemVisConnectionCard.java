package thaumicenergistics_ce.item;

import appeng.api.parts.IPartHost;
import appeng.items.materials.UpgradeCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.part.PartVisInterface;

public final class ItemVisConnectionCard extends UpgradeCardItem {
    public ItemVisConnectionCard(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> lines,
            net.minecraft.world.item.TooltipFlag flags) {
        super.appendHoverText(stack, context, lines, flags);
        lines.add(Component.translatable("item.thaumicenergistics_ce.vis_connection_card.bind_hint"));
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.copyTag().contains("VisRelayPosition")) {
            var tag = data.copyTag();
            var pos = BlockPos.of(tag.getLong("VisRelayPosition"));
            lines.add(Component.translatable("item.thaumicenergistics_ce.vis_connection_card.binding",
                    tag.getString("VisRelayDimension"), pos.getX(), pos.getY(), pos.getZ(), tag.getString("VisRelaySide")));
        }
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        var player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()
                && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof IPartHost host) {
            var selected = host.selectPartWorld(context.getClickLocation());
            if (selected.part instanceof PartVisInterface relay) {
                if (!relay.isOutput()) {
                    if (!context.getLevel().isClientSide()) {
                        player.displayClientMessage(Component.translatable("item.thaumicenergistics_ce.vis_connection_card.output_required"), true);
                    }
                    return InteractionResult.FAIL;
                }
                if (!context.getLevel().isClientSide()) {
                    bind(stack, context.getLevel().dimension().location().toString(),
                            context.getClickedPos(), relay.getSide());
                    player.displayClientMessage(Component.translatable("item.thaumicenergistics_ce.vis_connection_card.bound",
                            context.getClickedPos().getX(), context.getClickedPos().getY(), context.getClickedPos().getZ()), true);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.onItemUseFirst(stack, context);
    }

    public static void bind(ItemStack stack, String dimension, BlockPos pos, Direction side) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        tag.putString("VisRelayDimension", dimension);
        tag.putLong("VisRelayPosition", pos.asLong());
        tag.putString("VisRelaySide", side.getSerializedName());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static @Nullable PartVisInterface resolve(ServerLevel level, ItemStack card) {
        var data = card.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        var tag = data.copyTag();
        if (!tag.contains("VisRelayPosition")
                || !level.dimension().location().toString().equals(tag.getString("VisRelayDimension"))) {
            return null;
        }
        BlockPos pos = BlockPos.of(tag.getLong("VisRelayPosition"));
        Direction side = Direction.byName(tag.getString("VisRelaySide"));
        if (side == null || !level.hasChunkAt(pos)) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof IPartHost host
                && host.getPart(side) instanceof PartVisInterface relay && relay.isOutput() && relay.isActive()) {
            return relay;
        }
        return null;
    }
}
