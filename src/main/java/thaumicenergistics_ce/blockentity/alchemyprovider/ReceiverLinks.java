package thaumicenergistics_ce.blockentity.alchemyprovider;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * 一个供应器服务的接收端，以及它们给它带来的空闲功耗。链路被拒绝时以原因
 * 作为消息——已绑定、距离太远，或链路过多——位置以不可变方式存储，
 * 所以移动过的接收端就是另一个接收端。空闲功耗是基础值加上每条链路的分摊，
 * 每次变化时设置，从不保存。
 */
final class ReceiverLinks {

    private static final double IDLE_POWER = 1.0;

    private static final double POWER_PER_RECEIVER = 5.0;

    private final BlockEntityAlchemyProvider provider;

    private final List<BlockPos> linked = new ArrayList<>();

    ReceiverLinks(BlockEntityAlchemyProvider provider) {
        this.provider = provider;
    }

    /** 被拒绝，或链路建立时为 null；消息就是连接端显示的内容。 */
    @Nullable String add(BlockPos receiver) {
        if (linked.contains(receiver)) {
            return null;
        }
        if (linked.size() >= BlockEntityAlchemyProvider.MAX_LINKED_RECEIVERS) {
            return "provider is already serving " + BlockEntityAlchemyProvider.MAX_LINKED_RECEIVERS
                    + " receivers";
        }
        double distance = Math.sqrt(provider.getBlockPos().distSqr(receiver));
        if (distance > BlockEntityAlchemyProvider.MAX_LINK_DISTANCE) {
            return "receiver is " + (int) Math.ceil(distance) + " blocks away, further than "
                    + BlockEntityAlchemyProvider.MAX_LINK_DISTANCE;
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

    /** 丢弃接收端已消失的链路；有链路被丢弃时为 true，这样供应器可以立即重 tick。 */
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

    /** 基础空闲功耗加上每条链路的分摊：网格向此供应器收取的数值。 */
    void updateIdlePower() {
        provider.getMainNode().setIdlePowerUsage(IDLE_POWER + POWER_PER_RECEIVER * linked.size());
    }
}
