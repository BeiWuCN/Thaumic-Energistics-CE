package thaumicenergistics_ce.part;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.util.KeyTypeSelection;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.automation.UpgradeablePart;
import appeng.util.ConfigInventory;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.integration.ae2.EssentiaMEStorage;

/**
 * 源质存储总线：把它正对着的源质容器接进 ME 网络。
 * <ul>
 *   <li>是提供器，不是搬运器：参考实现从未实现 {@code IStorageProvider}。
 *   <li>挂载后罐子被列入存储、计入网络用量，能装也能抽。
 *   <li>单独挂着时只上报变化；见 {@link #tickingRequest}。
 * </ul>
 */
public class PartEssentiaStorageBus extends UpgradeablePart
        implements IStorageProvider, IGridTickable, KeyTypeSelectionHost {

    public static final Identifier MODEL_BASE = ThEIds.id("parts/essentia_storage_bus_base");
    public static final Identifier MODEL_OFF = ThEIds.id("parts/essentia_storage_bus_off");
    public static final Identifier MODEL_ON = ThEIds.id("parts/essentia_storage_bus_on");
    public static final Identifier MODEL_HAS_CHANNEL = ThEIds.id("parts/essentia_storage_bus_has_channel");

    public static final List<Identifier> MODEL_LOCATIONS =
            List.of(MODEL_BASE, MODEL_OFF, MODEL_ON, MODEL_HAS_CHANNEL);

    private static final double IDLE_POWER = 1.0;

    /** AE2 只在被通知时才问提供器要存储，所以这次轮询负责发现手动填满的罐子。 */
    private static final int POLL_INTERVAL = 20;

    private long mountedRevision = Long.MIN_VALUE;

    private EssentiaMEStorage mounted;

    private final ConfigInventory config = ConfigInventory.configTypes(63)
            .supportedTypes(Set.of(AEssentiaKeyType.INSTANCE))
            .changeListener(this::onConfigChanged)
            .build();

    private final KeyTypeSelection essentiaOnly = new KeyTypeSelection(selection -> {}, this::isEssentia);

    public PartEssentiaStorageBus(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(IDLE_POWER);
        getMainNode().addService(IStorageProvider.class, this).addService(IGridTickable.class, this);
    }

    private boolean isEssentia(AEKeyType type) {
        return type == AEssentiaKeyType.INSTANCE;
    }

    /** 和 AE2 存储总线用同一个碰撞箱，借来的模型正是照它做的。 */
    @Override
    public void getBoxes(IPartCollisionHelper boxes) {
        boxes.addBox(5, 5, 12, 11, 11, 14);
        boxes.addBox(2, 2, 14, 14, 14, 15);
        boxes.addBox(3, 3, 15, 13, 13, 16);
    }


    /**
     * 把总线正对着的容器的视图交给网络。没有源质存储就什么都不挂，
     * 装在墙上的总线因此不占网络成本。
     */
    @Override
    public void mountInventories(IStorageMounts mounts) {
        IEssentiaStorage storage = adjacentStorage();
        if (storage == null) {
            mounted = null;
            return;
        }
        mounted = new EssentiaMEStorage(storage);
        mountedRevision = storage.contentRevision();
        mounts.mount(mounted);
    }

    /**
     * 每秒醒一次看容器动没动。网络会缓存提供器挂载的东西，
     * 版本号让没被动过的罐子不触发 {@code requestUpdate}。
     */
    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(POLL_INTERVAL, POLL_INTERVAL, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        IEssentiaStorage storage = adjacentStorage();
        if (storage == null) {
            if (mounted != null) {
                mounted = null;
                IStorageProvider.requestUpdate(getMainNode());
                return TickRateModulation.SLOWER;
            }
            return TickRateModulation.SLOWER;
        }
        if (mounted == null || storage.contentRevision() != mountedRevision) {
            IStorageProvider.requestUpdate(getMainNode());
        }
        return TickRateModulation.SLOWER;
    }

    private IEssentiaStorage adjacentStorage() {
        Direction side = getSide();
        if (side == null || !(getLevel() instanceof ServerLevel level)) {
            return null;
        }
        BlockPos target = getBlockEntity().getBlockPos().relative(side);
        if (!level.isLoaded(target)) {
            return null;
        }
        // 经 EssentiaNeighbour 找，这样容器自己会被问到收不收这一面（罐子只收上方），
        // 暴露传输能力的管道也接得上。
        return EssentiaNeighbour.find(level, target, side.getOpposite());
    }

    /**
     * 管道向这条总线要的端口。空的和缺失的都返回 null，
     * 面前什么都没有的存储总线因此读作没有管道连接。
     */
    public IEssentiaTransport transportView() {
        Direction side = getSide();
        IEssentiaStorage storage = adjacentStorage();
        return side == null || storage == null ? null : new EssentiaTransportView(storage, side);
    }

    public ConfigInventory getConfig() {
        return config;
    }

    private void onConfigChanged() {
        savePart();
        if (getMainNode().getNode() != null) {
            IStorageProvider.requestUpdate(getMainNode());
        }
    }

    private void savePart() {
        if (getHost() != null) {
            getHost().markForSave();
        }
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (player.level().isClientSide()) {
            return true;
        }
        MenuOpener.open(
                ModMenuTypes.ESSENTIA_STORAGE_BUS.get(),
                player,
                MenuLocators.forPart(this));
        return true;
    }

    @Override
    public int getUpgradeSlots() {
        return 4;
    }

    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return essentiaOnly;
    }

    @Override
    public void readFromNBT(ValueInput input) {
        super.readFromNBT(input);
        essentiaOnly.readFromNBT(input);
    }

    @Override
    public void writeToNBT(ValueOutput output) {
        super.writeToNBT(output);
        essentiaOnly.writeToNBT(output);
    }
}
