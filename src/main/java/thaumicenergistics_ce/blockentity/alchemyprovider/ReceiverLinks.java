package thaumicenergistics_ce.blockentity.alchemyprovider;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

final class ReceiverLinks {

    private static final double IDLE_POWER = 1.0;

    private static final double POWER_PER_RECEIVER = 5.0;

    private final BlockEntityAlchemyProvider provider;

    private final List<BlockPos> linked = new ArrayList<>();

    ReceiverLinks(BlockEntityAlchemyProvider provider) {
        this.provider = provider;
    }

    @Nullable Component add(BlockPos receiver) {
        if (linked.contains(receiver)) {
            return null;
        }
        if (linked.size() >= BlockEntityAlchemyProvider.MAX_LINKED_RECEIVERS) {
            return Component.translatable(
                    "item.thaumicenergistics_ce.wireless_connector.receiver_limit",
                    BlockEntityAlchemyProvider.MAX_LINKED_RECEIVERS);
        }
        double distance = Math.sqrt(provider.getBlockPos().distSqr(receiver));
        if (distance > BlockEntityAlchemyProvider.MAX_LINK_DISTANCE) {
            return Component.translatable(
                    "item.thaumicenergistics_ce.wireless_connector.too_far",
                    (int) Math.ceil(distance), BlockEntityAlchemyProvider.MAX_LINK_DISTANCE);
        }
        linked.add(receiver.immutable());
        provider.setChanged();
        updateIdlePower();
        return null;
    }

    boolean remove(BlockPos receiver) {
        if (!linked.remove(receiver)) {
            return false;
        }
        provider.setChanged();
        updateIdlePower();
        return true;
    }

    boolean contains(BlockPos receiver) {
        return linked.contains(receiver);
    }

    int count() {
        return linked.size();
    }

    List<BlockPos> all() {
        return List.copyOf(linked);
    }

    boolean pruneDead() {
        Level level = provider.getLevel();
        if (level == null || linked.isEmpty()) {
            return false;
        }
        boolean removed = linked.removeIf(pos ->
                !(level.getBlockEntity(pos) instanceof BlockEntityAlchemyProviderConnection));
        if (removed) {
            provider.setChanged();
            updateIdlePower();
        }
        return removed;
    }

    void replace(List<BlockPos> positions) {
        linked.clear();
        linked.addAll(positions);
    }

    void updateIdlePower() {
        provider.getMainNode().setIdlePowerUsage(IDLE_POWER + POWER_PER_RECEIVER * linked.size());
    }
}
