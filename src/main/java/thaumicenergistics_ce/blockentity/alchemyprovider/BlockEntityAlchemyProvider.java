package thaumicenergistics_ce.blockentity.alchemyprovider;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
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
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;

public class BlockEntityAlchemyProvider extends AENetworkedBlockEntity
        implements IStorageProvider, IGridTickable, IEssentiaStorage {

    public static final int BUFFER_PER_ASPECT = 16;

    public static final int MAX_LINKED_RECEIVERS = 8;

    public static final int MAX_LINK_DISTANCE = 32;

    public static final double AE_PER_ESSENTIA = 10.0;

    public static final double AE_CACHE = 40.0;

    private static final int TICK_RATE_ACTIVE = 10;
    private static final int TICK_RATE_IDLE = 40;

    private final AlchemyProviderBuffer buffer = new AlchemyProviderBuffer(this);

    private double cacheAE;

    private final ReceiverLinks links = new ReceiverLinks(this);

    private final IActionSource actionSource =
            IActionSource.ofMachine(this);

    public BlockEntityAlchemyProvider(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALCHEMY_PROVIDER.get(), pos, state);
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
        fillCache();
        if (!getMainNode().isActive() || !buffer.hasWork()) {
            if (pruneDeadReceivers()) {
                return TickRateModulation.URGENT;
            }
            return TickRateModulation.SLOWER;
        }

        boolean moved = buffer.push();
        return moved ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }


    @Override
    public int insert(Holder<IAspect> aspect, int amount, TransactionContext transaction) {
        return buffer.insert(aspect, amount, transaction);
    }

    public int previewInsert(Holder<IAspect> aspect, int amount) {
        return aspect == null ? 0 : buffer.previewInsert(aspect, amount);
    }

    public int insertFromLink(Holder<IAspect> aspect, int amount, TransactionContext transaction) {
        if (aspect == null || amount <= 0) {
            return 0;
        }
        int accepted = buffer.previewInsert(aspect, amount);
        if (accepted <= 0) {
            return 0;
        }
        int paid = chargeForLink(accepted);
        return paid <= 0 ? 0 : buffer.insert(aspect, paid, transaction);
    }

    public int previewInsertFromLink(Holder<IAspect> aspect, int amount) {
        return aspect == null ? 0 : buffer.previewInsert(aspect, amount);
    }

    @Override
    public int extract(Holder<IAspect> aspect, int amount, TransactionContext transaction) {
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

    public int takeForLink(Holder<IAspect> aspect, int amount, TransactionContext transaction) {
        if (aspect == null || amount <= 0 || !getMainNode().isActive()) {
            return 0;
        }
        MEStorage storage = networkStorage();
        if (storage == null) {
            return 0;
        }
        AEssentiaKey key = AEssentiaKey.of(aspect);
        if (key == null) {
            return 0;
        }
        long available = storage.extract(key, amount, Actionable.SIMULATE, actionSource);
        if (available <= 0) {
            return 0;
        }
        int wanted = chargeForLink((int) Math.min(available, amount));
        if (wanted <= 0) {
            return 0;
        }
        long moved = storage.extract(key, wanted, Actionable.MODULATE, actionSource);
        if (moved > 0) {
            setChanged();
        }
        return (int) Math.min(moved, Integer.MAX_VALUE);
    }

    public int previewTakeForLink(Holder<IAspect> aspect, int amount) {
        if (aspect == null || amount <= 0 || !getMainNode().isActive()) {
            return 0;
        }
        MEStorage storage = networkStorage();
        if (storage == null) {
            return 0;
        }
        AEssentiaKey key = AEssentiaKey.of(aspect);
        if (key == null) {
            return 0;
        }
        long available = storage.extract(key, amount, Actionable.SIMULATE, actionSource);
        return (int) Math.min(available, Integer.MAX_VALUE);
    }

    public int cachedAE() {
        return (int) Math.floor(cacheAE);
    }

    private void fillCache() {
        if (cacheAE >= AE_CACHE) {
            return;
        }
        IEnergyService energy = networkEnergy();
        if (energy == null) {
            return;
        }
        cacheAE += energy.extractAEPower(AE_CACHE - cacheAE, Actionable.MODULATE,
                PowerMultiplier.CONFIG);
    }

    private int chargeForLink(int units) {
        IEnergyService energy = networkEnergy();
        if (energy == null) {
            return 0;
        }
        int paid = 0;
        while (paid < units) {
            double offered = energy.extractAEPower(AE_PER_ESSENTIA, Actionable.SIMULATE,
                    PowerMultiplier.CONFIG);
            double shortfall = AE_PER_ESSENTIA - offered;
            if (shortfall > cacheAE) {
                break;
            }
            if (offered > 0) {
                energy.extractAEPower(offered, Actionable.MODULATE, PowerMultiplier.CONFIG);
            }
            cacheAE -= shortfall;
            paid++;
        }
        return paid;
    }

    private @Nullable IGrid networkGrid() {
        IGridNode node = getMainNode().getNode();
        return node == null ? null : node.getGrid();
    }

    private @Nullable MEStorage networkStorage() {
        IGrid grid = networkGrid();
        if (grid == null) {
            return null;
        }
        IStorageService service =
                grid.getService(IStorageService.class);
        return service == null ? null : service.getInventory();
    }

    private @Nullable IEnergyService networkEnergy() {
        IGrid grid = networkGrid();
        return grid == null ? null : grid.getService(IEnergyService.class);
    }


    @Override
    public void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("LinkedReceivers", Codec.LONG.listOf(),
                links.all().stream().map(BlockPos::asLong).toList());
        output.putDouble("CacheAE", cacheAE);
    }

    @Override
    public void loadTag(ValueInput input) {
        super.loadTag(input);
        List<BlockPos> positions = input.read("LinkedReceivers", Codec.LONG.listOf()).orElse(List.of())
                .stream().map(BlockPos::of).toList();
        links.replace(positions);
        links.updateIdlePower();
        cacheAE = input.getDoubleOr("CacheAE", 0.0D);
        buffer.clear();
    }
}
