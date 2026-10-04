package thaumicenergistics_ce.blockentity;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
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
import thaumicenergistics_ce.block.BlockEssentiaProviderConnection;
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.init.ModBlockEntities;

/**
 * The Essentia Provider Connection: the far end of a wireless essentia link, bound to a provider with the
 * wireless connector and up to {@link BlockEntityEssentiaProvider#MAX_LINK_DISTANCE} blocks from it.
 * <ul>
 *   <li>It carries essentia, never stores it: what arrives goes to the provider on the next tick, what leaves
 *       comes out of the network, so breaking the link loses only the transfer in flight.
 *   <li>Neighbouring containers can be emptied into the network or served from it. The link lives on both
 *       sides, and whichever side sees the other gone clears its own half.
 * </ul>
 */
public class BlockEntityEssentiaProviderConnection extends ThEBaseBlockEntity implements IEssentiaStorage {

    /** How much can be in flight per aspect. Small: this is a pipe, not a tank. */
    public static final int TRANSFER_LIMIT = 16;

    private static final int TICK_INTERVAL = 10;

    private @Nullable BlockPos providerPos;

    /** Essentia taken from a neighbour and waiting for the provider to accept it. Never persisted. */
    private final Map<Holder<IAspect>, Integer> buffer = new HashMap<>();

    private long revision;
    private int tickCounter;

    public BlockEntityEssentiaProviderConnection(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ESSENTIA_PROVIDER_CONNECTION.get(), pos, state);
    }

    // --- The link ---

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
        if (!(level.getBlockEntity(toProvider) instanceof BlockEntityEssentiaProvider provider)) {
            return "not a provider";
        }
        if (!(level.getBlockEntity(worldPosition) instanceof BlockEntityEssentiaProviderConnection)) {
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

    /** Breaks the link, telling the provider so its receiver list does not keep a dead entry. */
    public void unlink() {
        if (level != null && !level.isClientSide() && providerPos != null
                && level.getBlockEntity(providerPos) instanceof BlockEntityEssentiaProvider provider) {
            provider.removeLinkedReceiver(worldPosition);
        }
        providerPos = null;
        buffer.clear();
        setChanged();
        updateConnectedState();
    }

    /**
     * The bound provider, or {@code null}. Clears the link when the provider has gone, or the player could
     * not tell "linked but idle" from "linked to nothing".
     */
    public @Nullable BlockEntityEssentiaProvider resolveProvider() {
        if (level == null || level.isClientSide() || providerPos == null) {
            return null;
        }
        if (level.getBlockEntity(providerPos) instanceof BlockEntityEssentiaProvider provider) {
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

    /** Mirrors the link into the blockstate, which is what selects the lit model. */
    public void updateConnectedState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(BlockEssentiaProviderConnection.CONNECTED)) {
            return;
        }
        boolean connected = providerPos != null;
        if (state.getValue(BlockEssentiaProviderConnection.CONNECTED) != connected) {
            level.setBlock(worldPosition, state.setValue(BlockEssentiaProviderConnection.CONNECTED, connected), 3);
        }
    }

    // --- Moving essentia ---

    /** Called from the block's ticker. */
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
        }
    }

    private void pushBufferToProvider() {
        if (buffer.isEmpty()) {
            return;
        }
        BlockEntityEssentiaProvider provider = resolveProvider();
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
            IEssentiaStorage source = level.getCapability(
                    EssentiaCapabilities.STORAGE, worldPosition.relative(side), side.getOpposite());
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

    // --- IEssentiaStorage: the network's view through this link ---

    /** Accepts essentia from a neighbouring container, to be carried to the network. Refuses when unlinked. */
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
        BlockEntityEssentiaProvider provider = resolveProvider();
        if (provider == null) {
            return 0;
        }
        return provider.takeForLink(aspect, Math.min(amount, TRANSFER_LIMIT), simulate);
    }

    /** What is in flight towards the network. */
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

    // --- Persistence ---

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
        // No screen: a link is made with the connector and there is nothing to configure.
        return null;
    }
}
