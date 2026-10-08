package thaumicenergistics_ce.blockentity;

import net.minecraft.world.level.storage.ValueOutput;
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
 * 注魔供应器：让注魔祭坛直接从 ME 网络抽源质。
 * 没有缓冲：方块是网络的一扇窗，不是罐子；{@link #getAspects()} 答空，管道就不会把它当容器抽。
 * 经 {@link #takeFromContainer} 的取出是全有或全无：部分取出放回去并报失败。
 */
public class BlockEntityInfusionProvider extends AENetworkedBlockEntity implements IAspectSource {

    private static final double IDLE_POWER = 5.0;

    private final IActionSource actionSource = IActionSource.ofMachine(this);

    public BlockEntityInfusionProvider(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFUSION_PROVIDER.get(), pos, state);
        getMainNode().setIdlePowerUsage(IDLE_POWER);
    }

    // ------------------------------------------------------------------
    // 持久化
    // ------------------------------------------------------------------

    @Override
    public AspectList getAspects() {
        return AspectList.EMPTY;
    }

    @Override
    public void setAspects(AspectList aspects) {
    }

    @Override
    public boolean accepts(Holder<IAspect> aspect) {
        return false;
    }

    @Override
    public int fill(Holder<IAspect> aspect, int amount) {
        return amount;
    }

    @Override
    public boolean drain(Holder<IAspect> aspect, int amount) {
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
            // 两次调用之间网络变了：把真取出的那部分放回去，不会有东西丢。
            if (taken > 0) {
                storage.insert(key, taken, Actionable.MODULATE, actionSource);
            }
            return false;
        }
        setChanged();
        return true;
    }

    /**
     * 覆写而不是留给默认实现，默认实现会读 {@link #getAspects()}：那个方法故意答空，
     * 好让管道不把这个方块当罐子抽；默认实现读它会向祭坛自己的搜索报零，
     * 供应器就永远够不到。
     */
    @Override
    public int amountOf(Holder<IAspect> aspect) {
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
    // 持久化
    // ------------------------------------------------------------------

    @Override
    public void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
    }
}
