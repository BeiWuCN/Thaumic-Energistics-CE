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
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.capability.CachedEssentiaNeighbours;
import thaumicenergistics_ce.util.ThETransaction;

/**
 * 供应器的缓冲：源质送往外界途中的暂存，一次一个 tick。
 * 它只是中转点，不保存东西，重载后从空开始。
 * 没接东西的供应器拒绝一切；机器的抽吸量从网格取。
 * 每次改动递增 revision，容器的缓存只需要这个数。
 */
final class AlchemyProviderBuffer {

    private final BlockEntityAlchemyProvider provider;

    private final Map<Holder<IAspect>, Integer> buffer = new HashMap<>();

    private final CachedEssentiaNeighbours neighbours;

    private long revision;

    /**
     * 托管改了缓冲的那个事务。这家存储的全部内容就是这一个映射，
     * 复制它就等于一份完整快照。
     */
    private final SnapshotJournal<BufferSnapshot> journal = new SnapshotJournal<>() {
        @Override
        protected BufferSnapshot createSnapshot() {
            return new BufferSnapshot(Map.copyOf(buffer), revision);
        }

        @Override
        protected void revertToSnapshot(BufferSnapshot snapshot) {
            buffer.clear();
            buffer.putAll(snapshot.contents());
            revision = snapshot.revision();
        }

        @Override
        protected void onRootCommit(BufferSnapshot original) {
            if (revision != original.revision()) {
                provider.setChanged();
            }
        }
    };

    private record BufferSnapshot(Map<Holder<IAspect>, Integer> contents, long revision) {}

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

    /** 收下装得下的部分，并记入日志，中止的事务好把数量放回去。 */
    int insert(Holder<IAspect> aspect, int amount, TransactionContext transaction) {
        int accepted = previewInsert(aspect, amount);
        if (accepted <= 0) {
            return 0;
        }
        journal.updateSnapshots(transaction);
        buffer.put(aspect, buffer.getOrDefault(aspect, 0) + accepted);
        revision++;
        return accepted;
    }

    /** 收下 {@code amount} 会收走多少，问它什么都不改。 */
    int previewInsert(Holder<IAspect> aspect, int amount) {
        if (aspect == null || amount <= 0 || !hasAnyTarget()) {
            return 0;
        }
        int space = BlockEntityAlchemyProvider.BUFFER_PER_ASPECT - buffer.getOrDefault(aspect, 0);
        return space <= 0 ? 0 : Math.min(amount, space);
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

    /** 任一邻接方收源质时为 true；否则插入无处可去。 */
    boolean hasAnyTarget() {
        for (Direction side : Direction.values()) {
            if (neighbours.storage(side) != null || SuctionTarget.on(neighbours, side) != null) {
                return true;
            }
        }
        return false;
    }

    /** 有活干时为 true：有源质等着送出，或有机器在索要。 */
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

    /** 该方向上值得投喂的机器；可插入的容器占着那块位置时为空。 */
    private @Nullable SuctionTarget machine(Direction side) {
        if (neighbours.storage(side) != null) {
            return null;
        }
        return SuctionTarget.on(neighbours, side);
    }

    /** 把缓冲挨个交给各方向的邻接方，返回是否有东西移动。 */
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

    /** 容器只接受一次插入；抽吸机器改成直接交付同样的量。 */
    private int hand(Direction side, Holder<IAspect> aspect, int amount) {
        IEssentiaStorage target = neighbours.storage(side);
        if (target != null) {
            return ThETransaction.apply(transaction -> target.insert(aspect, amount, transaction));
        }
        SuctionTarget machine = SuctionTarget.on(neighbours, side);
        return machine == null ? 0 : machine.accept(aspect, amount);
    }

    /**
     * 取抽吸机器要的量：来源是网格，缓冲只是中转点。
     * 旁边没有容器的机器否则会一直等一个永远不来的插入。
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
            // 网格花在一切日志之外，所以取货和改缓冲共用一个事务：
            // 退不回来的事只做一次，缓冲照样正经地加入它。
            int taken = ThETransaction.apply(transaction -> {
                int got = provider.takeForLink(wanted, BlockEntityAlchemyProvider.BUFFER_PER_ASPECT, transaction);
                if (got > 0) {
                    journal.updateSnapshots(transaction);
                    buffer.put(wanted, got);
                    revision++;
                }
                return got;
            });
            if (taken > 0) {
                fetched = true;
            }
        }
        return fetched;
    }
}
