package thaumicenergistics_ce.blockentity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The receivers one provider serves, and the idle power they cost it.
 * <ul>
 *   <li>A link is refused with the reason as its message: already linked, too far, or one link too many.
 *   <li>Positions are stored immutable: a receiver that moves is a different receiver.
 *   <li>Idle power is the base plus a share per link, set on every change and never saved.
 * </ul>
 */
final class ReceiverLinks {

    private static final double IDLE_POWER = 1.0;

    private static final double POWER_PER_RECEIVER = 5.0;

    private final BlockEntityEssentiaProvider provider;

    private final List<BlockPos> linked = new ArrayList<>();

    ReceiverLinks(BlockEntityEssentiaProvider provider) {
        this.provider = provider;
    }

    /** A refusal, or null when the link was made; the message is what the connector shows. */
    @Nullable String add(BlockPos receiver) {
        if (linked.contains(receiver)) {
            return null;
        }
        if (linked.size() >= BlockEntityEssentiaProvider.MAX_LINKED_RECEIVERS) {
            return "provider is already serving " + BlockEntityEssentiaProvider.MAX_LINKED_RECEIVERS
                    + " receivers";
        }
        double distance = Math.sqrt(provider.getBlockPos().distSqr(receiver));
        if (distance > BlockEntityEssentiaProvider.MAX_LINK_DISTANCE) {
            return "receiver is " + (int) Math.ceil(distance) + " blocks away, further than "
                    + BlockEntityEssentiaProvider.MAX_LINK_DISTANCE;
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

    /** Drops links whose receiver is gone; true when one went, so the provider can retick at once. */
    boolean pruneDead() {
        Level level = provider.getLevel();
        if (level == null || linked.isEmpty()) {
            return false;
        }
        boolean removed = linked.removeIf(pos ->
                !(level.getBlockEntity(pos) instanceof BlockEntityEssentiaProviderConnection));
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

    /** Base idle power plus a share per link: the figure the grid charges this provider. */
    void updateIdlePower() {
        provider.getMainNode().setIdlePowerUsage(IDLE_POWER + POWER_PER_RECEIVER * linked.size());
    }
}
