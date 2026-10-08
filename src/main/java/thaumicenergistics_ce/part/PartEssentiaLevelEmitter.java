package thaumicenergistics_ce.part;

import appeng.api.networking.IGrid;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.parts.automation.AbstractLevelEmitterPart;
import appeng.util.ConfigInventory;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

/**
 * 源质标准发信器：跟随网络持有某个要素多少的红石信号。
 * 红石、上报值、升级槽位与点亮状态的流式同步都来自 AE2 的发信器部件。
 * 监视走网格的存储监视器，不逐 tick 轮询，AE2 已经缓存了网络内容。
 * 该数值是网络总量，挂在存储总线上的罐子也计入。
 */
public class PartEssentiaLevelEmitter extends AbstractLevelEmitterPart {

    public static final Identifier MODEL_BASE_OFF = ThEIds.id("parts/essentia_level_emitter_base_off");
    public static final Identifier MODEL_BASE_ON = ThEIds.id("parts/essentia_level_emitter_base_on");
    public static final Identifier MODEL_STATUS_OFF = ThEIds.id("parts/essentia_level_emitter_status_off");
    public static final Identifier MODEL_STATUS_ON = ThEIds.id("parts/essentia_level_emitter_status_on");
    public static final Identifier MODEL_STATUS_HAS_CHANNEL =
            ThEIds.id("parts/essentia_level_emitter_status_has_channel");

    public static final List<Identifier> MODEL_LOCATIONS = List.of(
            MODEL_BASE_OFF, MODEL_BASE_ON, MODEL_STATUS_OFF, MODEL_STATUS_ON, MODEL_STATUS_HAS_CHANNEL);

    private static final double IDLE_POWER = 0.5;

    private final ConfigInventory config = ConfigInventory.configTypes(1)
            .supportedTypes(Set.of(AEssentiaKeyType.INSTANCE))
            .changeListener(this::configureWatchers)
            .build();

    private IStackWatcher storageWatcher;

    private final IStorageWatcherNode watcherNode = new IStorageWatcherNode() {
        @Override
        public void updateWatcher(IStackWatcher newWatcher) {
            storageWatcher = newWatcher;
            configureWatchers();
        }

        @Override
        public void onStackChange(AEKey what, long amount) {
            if (what.equals(getConfiguredKey())) {
                lastReportedValue = amount;
                updateState();
            }
        }
    };

    public PartEssentiaLevelEmitter(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(IDLE_POWER);
        getMainNode().addService(IStorageWatcherNode.class, watcherNode);
    }

    public AEssentiaKey getConfiguredKey() {
        AEKey key = config.getKey(0);
        return key instanceof AEssentiaKey essentia ? essentia : null;
    }

    public ConfigInventory getConfig() {
        return config;
    }

    public long getCurrentLevel() {
        return lastReportedValue;
    }

    @Override
    protected void configureWatchers() {
        AEssentiaKey key = getConfiguredKey();
        if (storageWatcher != null) {
            storageWatcher.reset();
            if (key != null) {
                storageWatcher.add(key);
            }
        }
        // 立刻询问，不等：没变化的网络在变动之前不会上报。
        getMainNode().ifPresent(this::updateReportingValue);
        updateState();
    }

    private void updateReportingValue(IGrid grid) {
        AEssentiaKey key = getConfiguredKey();
        if (key == null) {
            lastReportedValue = 0;
            return;
        }
        KeyCounter cached = grid.getStorageService().getCachedInventory();
        lastReportedValue = cached.get(key);
    }

    @Override
    protected boolean hasDirectOutput() {
        return false;
    }

    @Override
    protected boolean getDirectOutput() {
        return false;
    }


    @Override
    public int getUpgradeSlots() {
        return 3;
    }
}
