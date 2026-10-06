package thaumicenergistics_ce.blockentity.alchemyprovider;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.block.BlockAlchemyProviderConnection;
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.init.capability.CachedEssentiaNeighbours;

/**
 * The Alchemy Provider Connection: the far end of a wireless essentia link to a provider.
 * <ul>
 *   <li>It carries essentia, never stores it: arrivals go on to the provider next tick.
 *   <li>Beside a machine that asks, it answers out of the grid, as a cabled provider does.
 *   <li>Bound within {@link BlockEntityAlchemyProvider#MAX_LINK_DISTANCE} blocks; a lost half clears itself.
 * </ul>
 */
public class BlockEntityAlchemyProviderConnection extends ThEBaseBlockEntity implements IEssentiaStorage {

    public static final int TRANSFER_LIMIT = 16;

    private static final int TICK_INTERVAL = 10;

    private @Nullable BlockPos providerPos;

    private final Map<Holder<IAspect>, Integer> buffer = new HashMap<>();

    private final CachedEssentiaNeighbours neighbours = new CachedEssentiaNeighbours(this);

    private long revision;
    private int tickCounter;

    public BlockEntityAlchemyProviderConnection(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALCHEMY_PROVIDER_CONNECTION.get(), pos, state);
    }


    public @Nullable BlockPos linkedProvider() {
        return providerPos;
    }

    public boolean isLinked() {
        return resolveProvider() != null;
    }

    /**
     * Binds this receiver to a provider, which is asked first because it enforces the limits - how many
     * receivers it serves, how far away - so a refused link leaves both sides untouched.
     *
     * @return the reason the link was refused, or {@code null} on success
     */
    public @Nullable String link(BlockPos toProvider) {
        if (level == null || level.isClientSide()) {
            return null;
        }
        if (!(level.getBlockEntity(toProvider) instanceof BlockEntityAlchemyProvider provider)) {
            return "not a provider";
        }
        if (!(level.getBlockEntity(worldPosition) instanceof BlockEntityAlchemyProviderConnection)) {
            return "receiver is gone";
        }
        String refusal = provider.addLinkedReceiver(worldPosition);
        if (refusal != null) {
            return refusal;
        }
        providerPos = toProvider.immutable();
        setChanged();
        updateConnectedState();
        return null;
    }

    public void unlink() {
        if (level != null && !level.isClientSide() && providerPos != null
                && level.getBlockEntity(providerPos) instanceof BlockEntityAlchemyProvider provider) {
            provider.removeLinkedReceiver(worldPosition);
        }
        providerPos = null;
        buffer.clear();
        setChanged();
        updateConnectedState();
    }

    public @Nullable BlockEntityAlchemyProvider resolveProvider() {
        if (level == null || level.isClientSide() || providerPos == null) {
            return null;
        }
        if (level.getBlockEntity(providerPos) instanceof BlockEntityAlchemyProvider provider) {
            // Self-healing: put the receiver back if the provider's list lost it, e.g. after an older save.
            if (!provider.isLinkedReceiver(worldPosition)) {
                provider.addLinkedReceiver(worldPosition);
            }
            return provider;
        }
        providerPos = null;
        setChanged();
        updateConnectedState();
        return null;
    }

    public void updateConnectedState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(BlockAlchemyProviderConnection.CONNECTED)) {
            return;
        }
        boolean connected = providerPos != null;
        if (state.getValue(BlockAlchemyProviderConnection.CONNECTED) != connected) {
            level.setBlock(worldPosition, state.setValue(BlockAlchemyProviderConnection.CONNECTED, connected), 3);
        }
    }


    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (++tickCounter < TICK_INTERVAL) {
            return;
        }
        tickCounter = 0;

        pushBufferToProvider();
        if (isLinked()) {
            drawFromNeighbours();
            feedSuctionMachines();
        }
    }

    /**
     * Feeds a machine beside the receiver out of the provider's grid: a suction machine offers no
     * container face, so the container path leaves it waiting. Fetched first, handed over second.
     */
    private void feedSuctionMachines() {
        BlockEntityAlchemyProvider provider = resolveProvider();
        if (provider == null) {
            return;
        }
        boolean fetched = false;
        for (Direction side : Direction.values()) {
            if (neighbours.storage(side) != null) {
                continue;
            }
            SuctionTarget machine = SuctionTarget.on(neighbours, side);
            if (machine == null) {
                continue;
            }
            Holder<IAspect> wanted = machine.wants();
            if (wanted == null) {
                continue;
            }
            int room = TRANSFER_LIMIT - buffer.getOrDefault(wanted, 0);
            if (room <= 0) {
                continue;
            }
            int taken = provider.takeForLink(wanted, room, false);
            if (taken <= 0) {
                continue;
            }
            fetched = true;
            int accepted = machine.accept(wanted, taken);
            // What the machine refused goes on the way in, so an answered request is never thrown away.
            int left = taken - accepted;
            if (left > 0) {
                buffer.put(wanted, buffer.getOrDefault(wanted, 0) + left);
                revision++;
            }
        }
        if (fetched) {
            setChanged();
        }
    }

    private void pushBufferToProvider() {
        if (buffer.isEmpty()) {
            return;
        }
        BlockEntityAlchemyProvider provider = resolveProvider();
        if (provider == null) {
            return;
        }
        boolean moved = false;
        for (var aspect : new ArrayList<>(buffer.keySet())) {
            int held = buffer.getOrDefault(aspect, 0);
            if (held <= 0) {
                buffer.remove(aspect);
                continue;
            }
            int accepted = provider.insert(aspect, held, false);
            if (accepted > 0) {
                moved = true;
            }
            int left = held - accepted;
            if (left <= 0) {
                buffer.remove(aspect);
            } else {
                buffer.put(aspect, left);
            }
        }
        if (moved) {
            revision++;
            setChanged();
        }
    }

    /**
     * Takes essentia from neighbouring containers, only while linked: with no provider there is nowhere
     * for it to go, and taking it anyway would grow a buffer that can never drain.
     */
    private void drawFromNeighbours() {
        if (level == null) {
            return;
        }
        boolean moved = false;
        for (Direction side : Direction.values()) {
            IEssentiaStorage source = neighbours.storage(side);
            if (source == null) {
                continue;
            }
            for (AspectInstance entry : source.contents().entries()) {
                if (entry.amount() <= 0) {
                    continue;
                }
                Holder<IAspect> aspect = entry.aspect();
                int held = buffer.getOrDefault(aspect, 0);
                int space = TRANSFER_LIMIT - held;
                if (space <= 0) {
                    continue;
                }
                // The container reports one contents snapshot, so asking for more than one would overdraw it.
                // One unit per pull keeps this in step with the provider's own draw.
                int taken = source.extract(aspect, 1, false);
                if (taken > 0) {
                    buffer.put(aspect, held + taken);
                    revision++;
                    moved = true;
                }
            }
        }
        // Once per visit, not per unit moved: every setChanged() flags the chunk for the next save.
        if (moved) {
            setChanged();
        }
    }


    @Override
    public int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
        if (aspect == null || amount <= 0 || !isLinked()) {
            return 0;
        }
        int held = buffer.getOrDefault(aspect, 0);
        int space = TRANSFER_LIMIT - held;
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

    /**
     * Serves a neighbouring container out of the provider's network, not the buffer: the buffer holds what
     * is on its way <em>in</em>, so giving it back out would put the same essentia on both paths.
     */
    @Override
    public int extract(Holder<IAspect> aspect, int amount, boolean simulate) {
        if (aspect == null || amount <= 0) {
            return 0;
        }
        BlockEntityAlchemyProvider provider = resolveProvider();
        if (provider == null) {
            return 0;
        }
        return provider.takeForLink(aspect, Math.min(amount, TRANSFER_LIMIT), simulate);
    }

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


    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (providerPos != null) {
            tag.putLong("ProviderPos", providerPos.asLong());
        }
        // The buffer is deliberately not written: a transfer in progress, not a container to accumulate.
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        providerPos = tag.contains("ProviderPos") ? BlockPos.of(tag.getLong("ProviderPos")) : null;
        buffer.clear();
    }

    @Override
    public AbstractContainerMenu createMenu(
            int containerId, Inventory playerInventory,
            Player player) {
        return null;
    }
}
