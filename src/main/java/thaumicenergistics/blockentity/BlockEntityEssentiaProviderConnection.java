package thaumicenergistics.blockentity;

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
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics.block.BlockEssentiaProviderConnection;
import thaumicenergistics.block.ThEBaseBlockEntity;
import thaumicenergistics.init.ModBlockEntities;

/**
 * The Essentia Provider Connection: the far end of a wireless essentia link. Bound to a provider with the
 * wireless connector, it carries essentia from that provider's network to whatever container <em>it</em> is
 * placed against, up to {@link BlockEntityEssentiaProvider#MAX_LINK_DISTANCE} blocks away.
 *
 * <p><b>It carries essentia, it does not store it.</b> What arrives is handed to the provider on the next
 * tick and what leaves comes out of the network rather than a local tank, so breaking the link loses nothing
 * beyond the transfer in flight. Neighbouring containers can be emptied into the network, or served from it;
 * the link is stored on both sides and whichever side notices the other gone clears its own half.
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
     * Binds this receiver to a provider, which is asked first because it is the side that enforces the
     * limits - how many receivers it will serve and how far away they may be - so a refused link leaves both
     * sides untouched.
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
     * The bound provider, or {@code null}. Clears the link when the provider has gone: a receiver pointing at
     * a block that no longer exists would otherwise look bound forever, and the player could not tell
     * "linked but idle" from "linked to nothing".
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
     * Takes essentia from the containers this receiver touches. Only while a link exists: with no provider
     * there is nowhere for it to go, and taking it anyway would move it into a buffer that can only grow.
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
                // One at a time, for the same reason the provider does: a container reports one contents
                // snapshot and no notion of "as much as fits", so asking for more would overdraw it.
                int taken = source.extract(aspect, 1, false);
                if (taken > 0) {
                    buffer.put(aspect, held + taken);
                    revision++;
                    moved = true;
                }
            }
        }
        // Once per visit rather than once per unit moved: every setChanged() flags the chunk for the next
        // save, and a receiver that took six units was flagging it six times.
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
     * Serves a neighbouring container out of the provider's network, not out of the buffer: the buffer holds
     * what is on its way <em>in</em>, and giving that back out would put the same essentia on both paths.
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
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int containerId, net.minecraft.world.entity.player.Inventory playerInventory,
            net.minecraft.world.entity.player.Player player) {
        // No screen: a link is made with the connector and there is nothing to configure.
        return null;
    }
}
