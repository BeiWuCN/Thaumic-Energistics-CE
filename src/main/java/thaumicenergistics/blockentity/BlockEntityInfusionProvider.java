package thaumicenergistics.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.IAspectSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.integration.ae2.AEssentiaKey;

/**
 * The Infusion Provider: lets an Infusion Altar draw essentia straight out of the ME network.
 *
 * <p>An altar normally takes essentia from jars and alembics placed around it, which means somebody has to
 * keep filling them. This block presents the network's essentia <em>as if it were a container</em>: the
 * altar finds it the same way it finds a jar, and pulls what the recipe needs. Nothing is buffered here and
 * nothing is stored - the block is a window onto the network, not a tank.
 *
 * <p>That is why {@link #getAspects()} answers with an empty list rather than the network's contents. The
 * altar does not use it to decide what it can have; it asks {@link #containerContains} for the one aspect
 * the current recipe wants. Reporting everything the network holds would instead advertise this block to
 * every essentia pipe in range as a container with hundreds of essentia in it, and the pipes would spend
 * their time trying to pump a window.
 *
 * <p>{@link #takeFromContainer} is the one operation that does anything, and it is careful about partial
 * extraction: if the network can only supply part of what was asked for, the part is put back and the call
 * reports failure. Returning true after a partial take would have the altar believe it received essentia it
 * never got, and the recipe would continue with essentia missing - which does not fail loudly, it produces
 * the wrong result or none at all after the ingredients are already spent.
 */
public class BlockEntityInfusionProvider extends AENetworkedBlockEntity implements IAspectSource {

    /** The network cost of being connected. The block is idle unless an altar is draining it. */
    private static final double IDLE_POWER = 5.0;

    private final IActionSource actionSource = IActionSource.ofMachine(this);

    public BlockEntityInfusionProvider(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFUSION_PROVIDER.get(), pos, state);
        getMainNode().setIdlePowerUsage(IDLE_POWER);
    }

    // ------------------------------------------------------------------
    // IAspectSource
    // ------------------------------------------------------------------

    /**
     * Always empty - see the class note.
     *
     * <p>An altar that wants to know whether this block can supply a given aspect asks
     * {@link #containerContains}, not this.
     */
    @Override
    public AspectList getAspects() {
        return AspectList.EMPTY;
    }

    @Override
    public void setAspects(AspectList aspects) {
        // A window has nothing to set.
    }

    @Override
    public boolean doesContainerAccept(Holder<IAspect> aspect) {
        return false;
    }

    @Override
    public int addToContainer(Holder<IAspect> aspect, int amount) {
        // Nothing is accepted: essentia goes into the network through a bus or a terminal, not here.
        return amount;
    }

    /**
     * Takes essentia out of the network for the altar.
     *
     * <p>All or nothing. The simulate call runs first so the amount is known before anything moves, and a
     * shortfall is put back rather than passed off as a success.
     */
    @Override
    public boolean takeFromContainer(Holder<IAspect> aspect, int amount) {
        if (aspect == null || amount <= 0 || !getMainNode().isActive()) {
            return false;
        }
        appeng.api.storage.MEStorage storage = networkStorage();
        if (storage == null) {
            return false;
        }
        AEssentiaKey key = AEssentiaKey.of(aspect);
        if (key == null) {
            // Not registry-backed: no id, so the altar is told there is nothing to take.
            return false;
        }

        long available = storage.extract(key, amount, Actionable.SIMULATE, actionSource);
        if (available < amount) {
            return false;
        }
        long taken = storage.extract(key, amount, Actionable.MODULATE, actionSource);
        if (taken < amount) {
            // The network changed between the two calls. Put back what did come out so nothing is lost.
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

    /** How much of one aspect the network holds, for the altar's planning. */
    @Override
    public int containerContains(Holder<IAspect> aspect) {
        if (aspect == null || !getMainNode().isActive()) {
            return 0;
        }
        appeng.api.storage.MEStorage storage = networkStorage();
        if (storage == null) {
            return 0;
        }
        AEssentiaKey key = AEssentiaKey.of(aspect);
        if (key == null) {
            // Not registry-backed: the honest answer is that the network holds none of it.
            return 0;
        }
        long available = storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource);
        return (int) Math.min(available, Integer.MAX_VALUE);
    }

    /**
     * Never blocked.
     *
     * <p>The altar treats a blocked source as one to skip. This block has no state in which it holds
     * essentia it will not release - an empty network is reported as an empty container by
     * {@link #containerContains}, which is the honest answer.
     */
    @Override
    public boolean isBlocked() {
        return false;
    }

    /** The network's storage, or {@code null} when this block is not connected to a grid. */
    private appeng.api.storage.MEStorage networkStorage() {
        appeng.api.networking.IGridNode node = getMainNode().getNode();
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

    /**
     * The essentia the altar can see here, for Jade.
     *
     * <p>Not {@link #getAspects()} - that is empty by design and has to stay empty. This is a separate
     * method so a tooltip can show the network's contents without advertising the block as a container.
     */
    public KeyCounter visibleEssentia() {
        KeyCounter counter = new KeyCounter();
        appeng.api.storage.MEStorage storage = networkStorage();
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

    /** Nothing but the grid node: this block holds no essentia and has no state of its own. */
    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
    }
}
