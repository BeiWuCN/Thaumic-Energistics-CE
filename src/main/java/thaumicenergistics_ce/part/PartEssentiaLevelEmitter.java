package thaumicenergistics_ce.part;

import appeng.api.networking.IGrid;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.automation.AbstractLevelEmitterPart;
import appeng.util.ConfigInventory;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

/**
 * Essentia level emitter: redstone that follows how much of one aspect the network holds.
 * <ul>
 * <li>Redstone, reporting value, upgrade slots and lit-state streaming come from AE2's emitter part.
 * <li>Watching uses the grid's storage watcher, not per-tick polling: AE2 caches the network's contents.</li>
 * <li>The level is the network total, so a bus-mounted jar counts towards it.</li>
 * </ul>
 */
public class PartEssentiaLevelEmitter extends AbstractLevelEmitterPart {

    @PartModels
    public static final ResourceLocation MODEL_BASE_OFF = ThEIds.id("parts/essentia_level_emitter_base_off");

    @PartModels
    public static final ResourceLocation MODEL_BASE_ON = ThEIds.id("parts/essentia_level_emitter_base_on");

    @PartModels
    public static final ResourceLocation MODEL_STATUS_OFF = ThEIds.id("parts/essentia_level_emitter_status_off");

    @PartModels
    public static final ResourceLocation MODEL_STATUS_ON = ThEIds.id("parts/essentia_level_emitter_status_on");

    @PartModels
    public static final ResourceLocation MODEL_STATUS_HAS_CHANNEL =
            ThEIds.id("parts/essentia_level_emitter_status_has_channel");

    public static final List<ResourceLocation> MODEL_LOCATIONS = List.of(
            MODEL_BASE_OFF, MODEL_BASE_ON, MODEL_STATUS_OFF, MODEL_STATUS_ON, MODEL_STATUS_HAS_CHANNEL);

    private static final PartModel MODELS_OFF_OFF = new PartModel(MODEL_BASE_OFF, MODEL_STATUS_OFF);
    private static final PartModel MODELS_OFF_ON = new PartModel(MODEL_BASE_OFF, MODEL_STATUS_ON);
    private static final PartModel MODELS_OFF_HAS_CHANNEL =
            new PartModel(MODEL_BASE_OFF, MODEL_STATUS_HAS_CHANNEL);
    private static final PartModel MODELS_ON_OFF = new PartModel(MODEL_BASE_ON, MODEL_STATUS_OFF);
    private static final PartModel MODELS_ON_ON = new PartModel(MODEL_BASE_ON, MODEL_STATUS_ON);
    private static final PartModel MODELS_ON_HAS_CHANNEL = new PartModel(MODEL_BASE_ON, MODEL_STATUS_HAS_CHANNEL);

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
        // Ask now rather than wait: an unchanged network would otherwise report nothing until it moved.
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
    public IPartModel getStaticModels() {
        boolean lit = isLevelEmitterOn();
        if (getMainNode().isActive()) {
            return lit ? MODELS_ON_HAS_CHANNEL : MODELS_OFF_HAS_CHANNEL;
        }
        if (isPowered()) {
            return lit ? MODELS_ON_ON : MODELS_OFF_ON;
        }
        return lit ? MODELS_ON_OFF : MODELS_OFF_OFF;
    }

    @Override
    public int getUpgradeSlots() {
        return 3;
    }
}
