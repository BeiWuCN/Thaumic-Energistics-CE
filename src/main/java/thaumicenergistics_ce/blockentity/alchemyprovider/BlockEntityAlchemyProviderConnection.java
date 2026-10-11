package thaumicenergistics_ce.blockentity.alchemyprovider;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
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
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.block.BlockAlchemyProviderConnection;
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.essentia.ThEImmediateStorage;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.init.capability.CachedEssentiaNeighbours;
import thaumicenergistics_ce.util.ThETransaction;

public class BlockEntityAlchemyProviderConnection extends ThEBaseBlockEntity
        implements IEssentiaStorage, ThEImmediateStorage {

    public static final int TRANSFER_LIMIT = 16;

    private static final int TICK_INTERVAL = 10;

    private @Nullable BlockPos providerPos;

    private final Map<Holder<IAspect>, Integer> buffer = new HashMap<>();

    private final CachedEssentiaNeighbours neighbours = new CachedEssentiaNeighbours(this);

    private long revision;

    private final SnapshotJournal<LinkSnapshot> journal = new SnapshotJournal<>() {
        @Override
        protected LinkSnapshot createSnapshot() {
            return new LinkSnapshot(Map.copyOf(buffer), revision);
        }

        @Override
        protected void revertToSnapshot(LinkSnapshot snapshot) {
            buffer.clear();
            buffer.putAll(snapshot.contents());
            revision = snapshot.revision();
        }

        @Override
        protected void onRootCommit(LinkSnapshot original) {
            if (revision != original.revision()) {
                setChanged();
            }
        }
    };

    private record LinkSnapshot(Map<Holder<IAspect>, Integer> contents, long revision) {}
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
            int taken = ThETransaction.apply(transaction -> provider.takeForLink(wanted, room, transaction));
            if (taken <= 0) {
                continue;
            }
            fetched = true;
            int accepted = machine.accept(wanted, taken);
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
            int accepted = ThETransaction.apply(transaction -> provider.insertFromLink(aspect, held, transaction));
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
                int taken = ThETransaction.apply(transaction -> source.extract(aspect, 1, transaction));
                if (taken > 0) {
                    buffer.put(aspect, held + taken);
                    revision++;
                    moved = true;
                }
            }
        }
        if (moved) {
            setChanged();
        }
    }


    @Override
    public int insert(Holder<IAspect> aspect, int amount, TransactionContext transaction) {
        int accepted = previewInsert(aspect, amount);
        if (accepted <= 0) {
            return 0;
        }
        journal.updateSnapshots(transaction);
        buffer.put(aspect, buffer.getOrDefault(aspect, 0) + accepted);
        revision++;
        return accepted;
    }

    @Override
    public int previewInsert(Holder<IAspect> aspect, int amount) {
        if (aspect == null || amount <= 0 || !isLinked()) {
            return 0;
        }
        int space = TRANSFER_LIMIT - buffer.getOrDefault(aspect, 0);
        return space <= 0 ? 0 : Math.min(amount, space);
    }

    @Override
    public int extract(Holder<IAspect> aspect, int amount, TransactionContext transaction) {
        if (aspect == null || amount <= 0) {
            return 0;
        }
        BlockEntityAlchemyProvider provider = resolveProvider();
        if (provider == null) {
            return 0;
        }
        return provider.takeForLink(aspect, Math.min(amount, TRANSFER_LIMIT), transaction);
    }

    @Override
    public int previewExtract(Holder<IAspect> aspect, int amount) {
        if (aspect == null || amount <= 0) {
            return 0;
        }
        BlockEntityAlchemyProvider provider = resolveProvider();
        return provider == null
                ? 0
                : provider.previewTakeForLink(aspect, Math.min(amount, TRANSFER_LIMIT));
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (providerPos != null) {
            output.putLong("ProviderPos", providerPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        providerPos = input.getLong("ProviderPos").map(BlockPos::of).orElse(null);
        buffer.clear();
    }

    @Override
    public AbstractContainerMenu createMenu(
            int containerId, Inventory playerInventory,
            Player player) {
        return null;
    }
}
