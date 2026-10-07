package thaumicenergistics_ce.blockentity.alchemyprovider;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.capability.CachedEssentiaNeighbours;

/**
 * 供应器的缓冲：源质在送往外界途中的暂存，一次一个 tick。它只是
 * 中转点而不是存储，因为这里什么都不保存，重载后从空开始。没有接
 * 任何东西的供应器拒绝一切，而机器的抽吸量从网格获取。每次改动都会
 * 递增 revision，这是这个容器的缓存唯一需要的答案。
 */
final class AlchemyProviderBuffer {

    private final BlockEntityAlchemyProvider provider;

    private final Map<Holder<IAspect>, Integer> buffer = new HashMap<>();

    private final CachedEssentiaNeighbours neighbours;

    private long revision;

    AlchemyProviderBuffer(BlockEntityAlchemyProvider provider) {
        this.provider = provider;
        this.neighbours = new CachedEssentiaNeighbours(provider);
    }

    int buffered(Holder<IAspect> aspect) {
        return buffer.getOrDefault(aspect, 0);
    }

    long revision() {
        return revision;
    }

    boolean isEmpty() {
        return buffer.isEmpty();
    }

    void clear() {
        buffer.clear();
    }

    int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
        if (aspect == null || amount <= 0 || !hasAnyTarget()) {
            return 0;
        }
        int held = buffer.getOrDefault(aspect, 0);
        int space = BlockEntityAlchemyProvider.BUFFER_PER_ASPECT - held;
        if (space <= 0) {
            return 0;
        }
        int accepted = Math.min(amount, space);
        if (!simulate) {
            buffer.put(aspect, held + accepted);
            revision++;
            provider.setChanged();
        }
        return accepted;
    }

    AspectList contents() {
        if (buffer.isEmpty()) {
            return AspectList.EMPTY;
        }
        var entries = new ArrayList<AspectInstance>();
        buffer.forEach((aspect, amount) -> {
            if (amount > 0) {
                entries.add(new AspectInstance(aspect, amount));
            }
        });
        return AspectList.ofEntries(entries);
    }

    /** 任意一个邻接方接受源质时为 true，这样插入才有去处。 */
    boolean hasAnyTarget() {
        for (Direction side : Direction.values()) {
            if (neighbours.storage(side) != null || SuctionTarget.on(neighbours, side) != null) {
                return true;
            }
        }
        return false;
    }

    /** 有活可干时为 true：有源质等着送出，或有机器在索要源质。 */
    boolean hasWork() {
        if (!buffer.isEmpty()) {
            return true;
        }
        for (Direction side : Direction.values()) {
            SuctionTarget machine = machine(side);
            if (machine != null && machine.wants() != null) {
                return true;
            }
        }
        return false;
    }

    /** 该方向上值得投喂的机器：仅在没有可插入的容器占据该位置时。 */
    private @Nullable SuctionTarget machine(Direction side) {
        if (neighbours.storage(side) != null) {
            return null;
        }
        return SuctionTarget.on(neighbours, side);
    }

    /** 把缓冲依次交给各个方向的邻接方，并返回是否有东西被移动。 */
    boolean push() {
        if (provider.getLevel() == null) {
            return false;
        }
        boolean movedAnything = topUpFromNetwork();

        var aspects = new ArrayList<>(buffer.keySet());
        for (Holder<IAspect> aspect : aspects) {
            int remaining = buffer.getOrDefault(aspect, 0);
            if (remaining <= 0) {
                buffer.remove(aspect);
                continue;
            }
            for (Direction side : Direction.values()) {
                if (remaining <= 0) {
                    break;
                }
                int accepted = hand(side, aspect, remaining);
                if (accepted > 0) {
                    remaining -= accepted;
                    movedAnything = true;
                }
            }
            if (remaining <= 0) {
                buffer.remove(aspect);
            } else {
                buffer.put(aspect, remaining);
            }
        }

        if (movedAnything) {
            revision++;
            provider.setChanged();
        }
        return movedAnything;
    }

    /** 容器就接受一次插入；而需要源质的机器则改为直接交付相同的量。 */
    private int hand(Direction side, Holder<IAspect> aspect, int amount) {
        IEssentiaStorage target = neighbours.storage(side);
        if (target != null) {
            return target.insert(aspect, amount, false);
        }
        SuctionTarget machine = SuctionTarget.on(neighbours, side);
        return machine == null ? 0 : machine.accept(aspect, amount);
    }

    /**
     * 取来抽吸机器所求的量：缓冲只是中转点，网格才是来源，否则
     * 旁边没有容器的机器会一直等一个永不到来的插入。
     */
    private boolean topUpFromNetwork() {
        boolean fetched = false;
        for (Direction side : Direction.values()) {
            SuctionTarget machine = machine(side);
            if (machine == null) {
                continue;
            }
            Holder<IAspect> wanted = machine.wants();
            if (wanted == null || buffer.getOrDefault(wanted, 0) > 0) {
                continue;
            }
            int taken = provider.takeForLink(wanted, BlockEntityAlchemyProvider.BUFFER_PER_ASPECT, false);
            if (taken > 0) {
                buffer.put(wanted, taken);
                fetched = true;
            }
        }
        return fetched;
    }
}
