package thaumicenergistics_ce.blockentity.vibrationchamber;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.MachineMenus;
import thaumicenergistics_ce.init.ModBlockEntities;

/**
 * 源质振动室烧源质发 AE，并 tick 紧挨着它的三个部件。[Potentia] 的时长和功率为 1.6 倍，
 * [ignis] 按基础速率，它它减半；缓冲是计数不是要素列表，留下的要素只给显示，
 * {@link BurnState} 是燃烧的唯一答案。网格拒收的 AE 由 AE2 销毁。
 */
public class BlockEntityEssentiaVibrationChamber extends AENetworkedBlockEntity
        implements IGridTickable, IEssentiaStorage, IEssentiaTransport, MenuProvider {

    public static final int MAX_ESSENTIA = 64;

    /** 能量槽容量，单位 AE；1 AE 兑 2 FE，AE2 把 16 kAE 报成 32,000 FE。 */
    public static final double MAX_ENERGY_STORAGE = 16_000.0;

    public static final double MAX_OUTPUT_PER_TICK = 2_000.0;

    private static final int TICK_RATE_BURNING = 10;
    private static final int TICK_RATE_IDLE = 40;

    public enum BurnState {
        BURNING,
        /** 退回：能量槽满到一个 tick 的量以内，什么都放不下。 */
        PAUSED_FULL,
        /** 网格上只有这台机器：电力无处可去。 */
        NO_NETWORK,
        IDLE;

        public boolean mayBurn() { return this == BURNING || this == IDLE; }

        public static BurnState byOrdinal(int ordinal) {
            BurnState[] states = values();
            return ordinal >= 0 && ordinal < states.length ? states[ordinal] : IDLE;
        }
    }

    // 燃料、燃烧和电力分别在 [ChamberBurn]、[ChamberEssentiaTank]、[ChamberEnergyOutput]：
    // 本类 tick 它们，并对网格、管道和界面当门面。
    private final ChamberEssentiaTank tank = new ChamberEssentiaTank(this);
    private final ChamberEnergyOutput energy = new ChamberEnergyOutput(this);
    private final ChamberBurn burn = new ChamberBurn(this, tank, energy);
    private final ChamberTrace trace = new ChamberTrace(this, tank, energy, burn);

    ChamberEssentiaTank tank() { return tank; }
    ChamberEnergyOutput energy() { return energy; }
    ChamberBurn burn() { return burn; }
    ChamberTrace trace() { return trace; }

    void markChanged() { setChanged(); markForClientUpdate(); }

    public BlockEntityEssentiaVibrationChamber(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(), pos, state);
        // 空闲 0.0 且不占频道，跟 AE2 自己的发电机一致（[VibrationChamberBlockEntity:57]、
        // [ChargerBlockEntity:43]）：扁平网络最缺频道时，带频道的机器会灭。
        getMainNode().setIdlePowerUsage(0.0).setFlags().addService(IGridTickable.class, this);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TICK_RATE_BURNING, TICK_RATE_IDLE, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        return ChamberTick.advance(this, node, ticksSinceLast);
    }

    @Override
    public AspectList contents() { return tank.contents(); }

    @Override
    public int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
        return tank.insert(aspect, amount, simulate, burn.paused());
    }

    @Override
    public int extract(Holder<IAspect> aspect, int amount, boolean simulate) { return 0; }

    @Override
    public long contentRevision() { return tank.revision(); }

    @Override
    public boolean isConnectable(Direction side) { return true; }

    @Override
    public boolean canInputFrom(Direction side) { return true; }

    /** 返回 true，让管道看到目的地不是死路，尽管 {@link #takeEssentia} 什么都不取。 */
    @Override
    public boolean canOutputTo(Direction side) { return true; }

    @Override
    public void setSuction(@Nullable Holder<IAspect> aspect, int amount) {}

    @Override
    public @Nullable Holder<IAspect> getSuctionType(Direction side) {
        return null;
    }

    /** 缓冲或能量槽满时返回 0：管道照这个数导向，满的机器再报吸力会吸来烧不掉的源质。 */
    @Override
    public int getSuctionAmount(Direction side) { return tank.suctionAmount(burn.paused()); }

    @Override
    public int takeEssentia(Holder<IAspect> aspect, int amount, Direction side) { return 0; }

    @Override
    public int addEssentia(Holder<IAspect> aspect, int amount, Direction side) {
        return insert(aspect, amount, false);
    }

    @Override
    public @Nullable Holder<IAspect> getEssentiaType(Direction side) {
        return tank.amount() <= 0 ? null : tank.aspect();
    }

    @Override
    public int getEssentiaAmount(Direction side) { return tank.amount(); }

    @Override
    public int getMinimumSuction() { return 1; }

    @Override
    public int spaceFor(Holder<IAspect> aspect, Direction side) { return tank.space(); }

    public int getStoredEssentia() { return tank.amount(); }
    public int getMaxEssentia() { return MAX_ESSENTIA; }
    public double getAePerTick() { return burn.aePerTick(); }
    public int getBurnTicksRemaining() { return burn.remaining(); }
    public int getTotalBurnTicks() { return burn.total(); }
    public float getBurnProgress() { return burn.progress(); }
    public BurnState getBurnState() { return burn.state(); }
    public boolean isBurning() { return burn.burning(); }
    public boolean isPaused() { return burn.paused(); }
    public double getStoredEnergy() { return energy.amount(); }
    public double getMaxEnergyStorage() { return MAX_ENERGY_STORAGE; }
    public double getMaxOutputPerTick() { return MAX_OUTPUT_PER_TICK; }
    public float getEnergyFillProgress() { return energy.fillProgress(); }
    public @Nullable ResourceLocation getCurrentAspect() { return tank.aspectId(); }
    public @Nullable Holder<IAspect> currentAspectHolder() { return tank.aspect(); }

    /**
     * 客户端的副本：状态、燃烧速率、缓冲燃料。只由 {@link #markForClientUpdate()} 发出，
     * 不每 tick 发，否则被盯着看时倒计时会冻住。
     */
    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        VibrationChamberSync.writeStream(data, this);
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        // 两者都会跑：基类回答有没有变化，同步单元把字节流写进机器。
        return super.readFromStream(data) | VibrationChamberSync.applyStreamed(this, data);
    }

    /** 包级可见，给读字节流的 {@link VibrationChamberSync} 和下面的重载用。 */
    void applyStreamed(BurnState streamedState, double streamedRate, int essentia,
            @Nullable Holder<IAspect> aspect) {
        burn.applyStreamed(streamedState, streamedRate); tank.set(essentia, aspect);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        VibrationChamberSync.writePersistent(tag, this);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        VibrationChamberSync.applyPersistent(this, tag, registries);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return MachineMenus.essentiaVibrationChamber(containerId, inventory, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    public static List<String> fuelAspectHint() {
        return List.of(ChamberBurn.ASPECT_POTENTIA, ChamberBurn.ASPECT_IGNIS);
    }
}
