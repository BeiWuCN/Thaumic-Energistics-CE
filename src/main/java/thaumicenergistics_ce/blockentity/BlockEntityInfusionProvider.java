package thaumicenergistics_ce.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.IAspectSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;

/**
 * The Infusion Provider: lets an Infusion Altar draw essentia straight out of the ME network.
 * <ul>
 *   <li>Nothing is buffered - the block is a window onto the network, not a tank.
 *   <li>{@link #getAspects()} answers empty so no pipe treats it as a container to pump.
 *   <li>{@link #takeFromContainer} is all-or-nothing; a partial take is put back and reported as failure.
 * </ul>
 */
public class BlockEntityInfusionProvider extends AENetworkedBlockEntity implements IAspectSource {

    private static final double IDLE_POWER = 5.0;

    private final IActionSource actionSource = IActionSource.ofMachine(this);

    public BlockEntityInfusionProvider(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFUSION_PROVIDER.get(), pos, state);
        getMainNode().setIdlePowerUsage(IDLE_POWER);
    }

    // ------------------------------------------------------------------
    // IAspectSource
    // ------------------------------------------------------------------

    @Override
    public AspectList getAspects() {
        return AspectList.EMPTY;
    }

    @Override
    public void setAspects(AspectList aspects) {
    }

    @Override
    public boolean doesContainerAccept(Holder<IAspect> aspect) {
        return false;
    }

    @Override
    public int addToContainer(Holder<IAspect> aspect, int amount) {
        return amount;
    }

    @Override
    public boolean takeFromContainer(Holder<IAspect> aspect, int amount) {
        if (aspect == null || amount <= 0 || !getMainNode().isActive()) {
            return false;
        }
        MEStorage storage = networkStorage();
        if (storage == null) {
            return false;
        }
        AEssentiaKey key = AEssentiaKey.of(aspect);
        if (key == null) {
            return false;
        }

        long available = storage.extract(key, amount, Actionable.SIMULATE, actionSource);
        if (available < amount) {
            return false;
        }
        long taken = storage.extract(key, amount, Actionable.MODULATE, actionSource);
        if (taken < amount) {
            // The network changed between the two calls. Put back what did come out, so nothing is lost.
            if (taken > 0) {
                storage.insert(key, taken, Actionable.MODULATE, actionSource);
            }
            return false;
        }
        setChanged();
        return true;
    }

    @Override
    public boolean doesContainerContainAmount(Holder<IAspect> aspect, int amount) {
        if (aspect == null || amount <= 0 || !getMainNode().isActive()) {
            return false;
        }
        return containerContains(aspect) >= amount;
    }

    @Override
    public int containerContains(Holder<IAspect> aspect) {
        if (aspect == null || !getMainNode().isActive()) {
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
        long available = storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource);
        return (int) Math.min(available, Integer.MAX_VALUE);
    }

    @Override
    public boolean isBlocked() {
        return false;
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
        IStorageService service = grid.getService(IStorageService.class);
        return service == null ? null : service.getInventory();
    }

    public KeyCounter visibleEssentia() {
        KeyCounter counter = new KeyCounter();
        MEStorage storage = networkStorage();
        if (storage == null) {
            return counter;
        }
        KeyCounter all = new KeyCounter();
        storage.getAvailableStacks(all);
        for (var entry : all) {
            AEKey key = entry.getKey();
            if (key instanceof AEssentiaKey) {
                counter.add(key, entry.getLongValue());
            }
        }
        return counter;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
    }
}
