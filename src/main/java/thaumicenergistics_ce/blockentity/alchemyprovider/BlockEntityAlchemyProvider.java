package thaumicenergistics_ce.blockentity.alchemyprovider;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;

/**
 * The Alchemy Provider: where the ME network puts essentia meant for the world - the opposite half
 * of the Import Bus, handing essentia to whatever container or machine it touches.
 *
 * <ul><li>The buffer is a waypoint, not storage: inserted essentia is pushed to a neighbour on the
 * next tick and is never persisted.</li><li>A provider with nothing attached refuses everything, while a
 * machine that wants essentia is served straight from the grid.</li></ul>
 */
public class BlockEntityAlchemyProvider extends AENetworkedBlockEntity
        implements IStorageProvider, IGridTickable, IEssentiaStorage {

    public static final int BUFFER_PER_ASPECT = 16;

    /** How many receivers one provider will serve. Each one costs the provider extra idle power. */
    public static final int MAX_LINKED_RECEIVERS = 8;

    public static final int MAX_LINK_DISTANCE = 32;

    private static final int TICK_RATE_ACTIVE = 10;
    private static final int TICK_RATE_IDLE = 40;

    private final AlchemyProviderBuffer buffer = new AlchemyProviderBuffer(this);

    private final ReceiverLinks links = new ReceiverLinks(this);

    private final IActionSource actionSource =
            IActionSource.ofMachine(this);

    public BlockEntityAlchemyProvider(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALCHEMY_PROVIDER.get(), pos, state);
        // Asked of the links so the idle power has one author: with none, they set the base figure.
        links.updateIdlePower();
        getMainNode()
                .addService(IStorageProvider.class, this)
                .addService(IGridTickable.class, this);
    }

    @Override
    public void mountInventories(IStorageMounts mounts) {
        mounts.mount(new AlchemyProviderStorage(this));
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TICK_RATE_ACTIVE, TICK_RATE_IDLE, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        if (level == null || level.isClientSide()) {
            return TickRateModulation.IDLE;
        }
        if (!getMainNode().isActive() || !buffer.hasWork()) {
            // A receiver may have been broken; paying for a missing one is invisible to the player.
            if (pruneDeadReceivers()) {
                return TickRateModulation.URGENT;
            }
            return TickRateModulation.SLOWER;
        }

        boolean moved = buffer.push();
        // Anything still buffered means every neighbour is full; looking again sooner will not empty it.
        return moved ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    // IEssentiaStorage - the buffer as seen by the network

    @Override
    public int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
        return buffer.insert(aspect, amount, simulate);
    }

    @Override
    public int extract(Holder<IAspect> aspect, int amount, boolean simulate) {
        return 0;
    }

    @Override
    public AspectList contents() {
        return buffer.contents();
    }

    @Override
    public long contentRevision() {
        return buffer.revision();
    }

    public int buffered(Holder<IAspect> aspect) {
        return buffer.buffered(aspect);
    }

    // The bound receivers

    /** A refusal, or null when the link was made; the message is what the connector shows. */
    public @Nullable String addLinkedReceiver(BlockPos receiver) {
        return links.add(receiver);
    }

    public void removeLinkedReceiver(BlockPos receiver) {
        links.remove(receiver);
    }

    public boolean isLinkedReceiver(BlockPos receiver) {
        return links.contains(receiver);
    }

    public int linkedReceiverCount() {
        return links.count();
    }

    public List<BlockPos> linkedReceivers() {
        return links.all();
    }

    public boolean pruneDeadReceivers() {
        return links.pruneDead();
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
            return 0;
        }
        long moved = storage.extract(key, amount, mode, actionSource);
        if (!simulate && moved > 0) {
            setChanged();
        }
        return (int) Math.min(moved, Integer.MAX_VALUE);
    }

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

    // Persistence: the links only. The buffer is a waypoint and is not saved.

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag receivers = new ListTag();
        for (BlockPos pos : links.all()) {
            receivers.add(LongTag.valueOf(pos.asLong()));
        }
        tag.put("LinkedReceivers", receivers);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        ListTag receivers = tag.getList("LinkedReceivers", CompoundTag.TAG_LONG);
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < receivers.size(); i++) {
            if (receivers.get(i) instanceof LongTag value) {
                positions.add(BlockPos.of(value.getAsLong()));
            }
        }
        links.replace(positions);
        links.updateIdlePower();
        buffer.clear();
    }
}
