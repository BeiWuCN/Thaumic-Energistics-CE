package thaumicenergistics_ce.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.EssentiaMEStorage;

/**
 * The Essentia Provider: where the ME network puts essentia meant for the world - the opposite half
 * of the Import Bus, handing essentia to whatever container it touches.
 *
 * <ul><li>The buffer is a waypoint, not storage: inserted essentia is pushed to a neighbour on the
 * next tick and is never persisted.</li><li>A provider with nothing attached refuses everything.</li></ul>
 */
public class BlockEntityEssentiaProvider extends AENetworkedBlockEntity
        implements IStorageProvider, IGridTickable, IEssentiaStorage {

    /** How much of one aspect can be waiting to be pushed. Small on purpose - see the class note. */
    public static final int BUFFER_PER_ASPECT = 16;

    /** How many receivers one provider will serve. Each one costs the provider extra idle power. */
    public static final int MAX_LINKED_RECEIVERS = 8;

    /** How far a receiver may be from its provider, in blocks. */
    public static final int MAX_LINK_DISTANCE = 32;

    /** Extra network cost per bound receiver, on top of {@link #IDLE_POWER}. */
    private static final double POWER_PER_RECEIVER = 5.0;

    /** The receivers bound to this provider, kept so it can price its own draw. */
    private final List<BlockPos> linkedReceivers = new ArrayList<>();

    /** Identity for anything taken out of the network on a receiver's behalf. */
    private final IActionSource actionSource =
            IActionSource.ofMachine(this);

    /** The network cost of being connected. A provider does nothing while idle, so this is small. */
    private static final double IDLE_POWER = 1.0;

    private static final int TICK_RATE_ACTIVE = 10;
    private static final int TICK_RATE_IDLE = 40;

    /** Essentia waiting to be pushed to a neighbour, by aspect. Never persisted - see the class note. */
    private final Map<Holder<IAspect>, Integer> buffer = new HashMap<>();

    /** Bumped whenever the buffer changes: an unannounced change is one a terminal will not show. */
    private long revision;

    public BlockEntityEssentiaProvider(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ESSENTIA_PROVIDER.get(), pos, state);
        getMainNode()
                .setIdlePowerUsage(IDLE_POWER)
                .addService(IStorageProvider.class, this)
                .addService(IGridTickable.class, this);
    }

    // IStorageProvider - the network's view of this block

    @Override
    public void mountInventories(IStorageMounts mounts) {
        mounts.mount(new ProviderStorage(this));
    }

    // IGridTickable

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TICK_RATE_ACTIVE, TICK_RATE_IDLE, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        if (level == null || level.isClientSide()) {
            return TickRateModulation.IDLE;
        }
        if (!getMainNode().isActive() || buffer.isEmpty()) {
            // A receiver may have been broken; paying for a missing one is invisible to the player.
            if (pruneDeadReceivers()) {
                return TickRateModulation.URGENT;
            }
            return TickRateModulation.SLOWER;
        }

        boolean moved = pushBufferToNeighbours();
        // Anything still buffered means every neighbour is full; looking again sooner will not empty it.
        return moved ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    /**
     * Hands the buffer to whatever the block touches; each side is offered all that is left, so none
     * of them starves.
     */
    private boolean pushBufferToNeighbours() {
        if (level == null) {
            return false;
        }
        boolean movedAnything = false;

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
                IEssentiaStorage target = level.getCapability(
                        EssentiaCapabilities.STORAGE, worldPosition.relative(side), side.getOpposite());
                if (target == null) {
                    continue;
                }
                int accepted = target.insert(aspect, remaining, false);
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
            setChanged();
        }
        return movedAnything;
    }

    // IEssentiaStorage - what the network can put here

    /** Accepts essentia for delivery, or refuses everything when there is nowhere to deliver it. */
    @Override
    public int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
        if (aspect == null || amount <= 0 || !hasAnyTarget()) {
            return 0;
        }
        int held = buffer.getOrDefault(aspect, 0);
        int space = BUFFER_PER_ASPECT - held;
        if (space <= 0) {
            return 0;
        }
        int accepted = Math.min(amount, space);
        if (!simulate) {
            buffer.put(aspect, held + accepted);
            revision++;
            setChanged();
        }
        return accepted;
    }

    /** Never gives essentia back: what is in the buffer is already promised to a neighbour. */
    @Override
    public int extract(Holder<IAspect> aspect, int amount, boolean simulate) {
        return 0;
    }

    /** What is waiting to be delivered. */
    @Override
    public AspectList contents() {
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

    @Override
    public long contentRevision() {
        return revision;
    }

    /** Whether any side of this block is touching something that can take essentia. */
    private boolean hasAnyTarget() {
        if (level == null) {
            return false;
        }
        for (Direction side : Direction.values()) {
            if (level.getCapability(
                            EssentiaCapabilities.STORAGE, worldPosition.relative(side), side.getOpposite())
                    != null) {
                return true;
            }
        }
        return false;
    }

    /** How much of one aspect is waiting, for Jade. */
    public int buffered(Holder<IAspect> aspect) {
        return buffer.getOrDefault(aspect, 0);
    }

    // Wireless receivers

    /**
     * Registers a receiver as bound to this provider, which pays for it. Returns a refusal reason, or
     * {@code null} when the link was accepted.
     */
    public @Nullable String addLinkedReceiver(BlockPos receiver) {
        if (linkedReceivers.contains(receiver)) {
            // Already bound; not an error.
            return null;
        }
        if (linkedReceivers.size() >= MAX_LINKED_RECEIVERS) {
            return "provider is already serving " + MAX_LINKED_RECEIVERS + " receivers";
        }
        double distance = Math.sqrt(worldPosition.distSqr(receiver));
        if (distance > MAX_LINK_DISTANCE) {
            return "receiver is " + (int) Math.ceil(distance) + " blocks away, further than "
                    + MAX_LINK_DISTANCE;
        }
        linkedReceivers.add(receiver.immutable());
        setChanged();
        updateIdlePower();
        return null;
    }

    public void removeLinkedReceiver(BlockPos receiver) {
        if (linkedReceivers.remove(receiver)) {
            setChanged();
            updateIdlePower();
        }
    }

    public boolean isLinkedReceiver(BlockPos receiver) {
        return linkedReceivers.contains(receiver);
    }

    public int linkedReceiverCount() {
        return linkedReceivers.size();
    }

    public List<BlockPos> linkedReceivers() {
        return List.copyOf(linkedReceivers);
    }

    /** Recomputes what this block costs the network; broken receivers are dropped when the cost is wrong. */
    private void updateIdlePower() {
        getMainNode().setIdlePowerUsage(IDLE_POWER + POWER_PER_RECEIVER * linkedReceivers.size());
    }

    /** Drops receivers whose block has gone, and returns whether any were dropped. */
    public boolean pruneDeadReceivers() {
        if (level == null || linkedReceivers.isEmpty()) {
            return false;
        }
        boolean removed = linkedReceivers.removeIf(pos ->
                !(level.getBlockEntity(pos) instanceof BlockEntityEssentiaProviderConnection));
        if (removed) {
            setChanged();
            updateIdlePower();
        }
        return removed;
    }

    /**
     * Lets a bound receiver take essentia from the network on behalf of its own neighbours. It goes
     * to the grid, not to {@link #extract}: the buffer holds essentia on its way into the world.
     */
    public int takeForLink(Holder<IAspect> aspect, int amount, boolean simulate) {
        if (aspect == null || amount <= 0 || !getMainNode().isActive()) {
            return 0;
        }
        MEStorage storage = networkStorage();
        if (storage == null) {
            return 0;
        }
        Actionable mode = simulate
                ? Actionable.SIMULATE
                : Actionable.MODULATE;
        AEssentiaKey key = AEssentiaKey.of(aspect);
        if (key == null) {
            // Not registry-backed: no id, so nothing the network could hold it under.
            return 0;
        }
        long moved = storage.extract(key, amount, mode, actionSource);
        if (!simulate && moved > 0) {
            setChanged();
        }
        return (int) Math.min(moved, Integer.MAX_VALUE);
    }

    /** The network's storage, or {@code null} when this block is not on a grid. */
    private MEStorage networkStorage() {
        IGridNode node = getMainNode().getNode();
        if (node == null) {
            return null;
        }
        IGrid grid = node.getGrid();
        if (grid == null) {
            return null;
        }
        IStorageService service =
                grid.getService(IStorageService.class);
        return service == null ? null : service.getInventory();
    }

    // Persistence

    /**
     * Saves the receiver list but never the buffer: saving it would make it a place essentia accumulates
     * across restarts. See the class note.
     */
    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag receivers = new ListTag();
        for (BlockPos pos : linkedReceivers) {
            receivers.add(LongTag.valueOf(pos.asLong()));
        }
        tag.put("LinkedReceivers", receivers);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        linkedReceivers.clear();
        ListTag receivers = tag.getList("LinkedReceivers", CompoundTag.TAG_LONG);
        for (int i = 0; i < receivers.size(); i++) {
            if (receivers.get(i) instanceof LongTag value) {
                linkedReceivers.add(BlockPos.of(value.getAsLong()));
            }
        }
        updateIdlePower();
        buffer.clear();
    }

    /** The network's view of the provider: reports the buffer, accepts inserts, offers no extraction. */
    private static final class ProviderStorage implements MEStorage {

        private final BlockEntityEssentiaProvider provider;

        ProviderStorage(BlockEntityEssentiaProvider provider) {
            this.provider = provider;
        }

        @Override
        public long insert(
                AEKey what,
                long amount,
                Actionable mode,
                IActionSource source) {
            if (!(what instanceof AEssentiaKey key) || amount <= 0) {
                return 0;
            }
            Holder<IAspect> aspect = key.resolveAspect();
            if (aspect == null) {
                return 0;
            }
            return provider.insert(aspect, clamp(amount), mode.isSimulate());
        }

        @Override
        public long extract(
                AEKey what,
                long amount,
                Actionable mode,
                IActionSource source) {
            return 0;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            var contents = provider.contents();
            for (var entry : contents.entries()) {
                ResourceLocation id = entry.aspect().unwrapKey().map(k -> k.location()).orElse(null);
                if (id != null && entry.amount() > 0) {
                    out.add(AEssentiaKey.of(id), entry.amount());
                }
            }
        }

        @Override
        public Component getDescription() {
            return Component.translatable(
                    "block.thaumicenergistics_ce.essentia_provider");
        }

        /** The buffer holds ints; a single AE insert cannot exceed what one aspect slot allows anyway. */
        private static int clamp(long amount) {
            return (int) Math.min(amount, Integer.MAX_VALUE);
        }
    }
}
