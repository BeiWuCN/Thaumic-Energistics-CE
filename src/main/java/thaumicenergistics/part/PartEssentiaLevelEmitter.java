package thaumicenergistics.part;

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
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics.ThEIds;
import thaumicenergistics.integration.ae2.AEssentiaKey;
import thaumicenergistics.integration.ae2.AEssentiaKeyType;

/**
 * The Essentia Level Emitter: redstone that follows how much of one aspect the network holds.
 *
 * <p>An AE2 level emitter with essentia in place of items. It extends AE2's own
 * {@link AbstractLevelEmitterPart}, so the redstone behaviour, the reporting value, the upgrade slots
 * and the streaming of its lit state to clients all come from AE2 rather than being reimplemented. The
 * four abstract methods it leaves are what an emitter has to answer for itself: what it watches, and
 * whether it is currently on.
 *
 * <p>Watching is done through the grid's storage watcher rather than by polling. AE2 keeps a cached count
 * of what the network holds - including anything a storage bus has mounted - and notifies watchers when a
 * watched key changes. Polling a terminal's worth of contents every tick to answer one redstone question
 * would be the expensive way to be told the same thing.
 *
 * <p>The level shown is the network's total for the configured aspect. A bus-mounted jar counts towards
 * it, which is the point: the emitter answers "does the network have enough", not "does this drive".
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

    private static final PartModel MODELS_OFF_OFF = new PartModel(MODEL_BASE_OFF, MODEL_STATUS_OFF);
    private static final PartModel MODELS_OFF_ON = new PartModel(MODEL_BASE_OFF, MODEL_STATUS_ON);
    private static final PartModel MODELS_OFF_HAS_CHANNEL =
            new PartModel(MODEL_BASE_OFF, MODEL_STATUS_HAS_CHANNEL);
    private static final PartModel MODELS_ON_OFF = new PartModel(MODEL_BASE_ON, MODEL_STATUS_OFF);
    private static final PartModel MODELS_ON_ON = new PartModel(MODEL_BASE_ON, MODEL_STATUS_ON);
    private static final PartModel MODELS_ON_HAS_CHANNEL = new PartModel(MODEL_BASE_ON, MODEL_STATUS_HAS_CHANNEL);

    private static final double IDLE_POWER = 0.5;

    /**
     * The aspect being watched, if any.
     *
     * <p>One slot, as in AE2's own level emitter. Reconfigured through a change listener rather than
     * checked each tick, so the watcher is only rebuilt when the player actually changes it.
     */
    private final ConfigInventory config = ConfigInventory.configTypes(1)
            .supportedTypes(Set.of(AEssentiaKeyType.INSTANCE))
            .changeListener(this::configureWatchers)
            .build();

    /** The grid's watcher, handed to us when the node comes up. */
    private IStackWatcher storageWatcher;

    /** Told by the grid when a watched aspect's stored amount changes. */
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

    /** The aspect this emitter watches, or {@code null} when none is configured. */
    public AEssentiaKey getConfiguredKey() {
        AEKey key = config.getKey(0);
        return key instanceof AEssentiaKey essentia ? essentia : null;
    }

    /** The config slot's inventory, for the menu. */
    public ConfigInventory getConfig() {
        return config;
    }

    /** The network's current total for the configured aspect, as last reported to us. */
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
        // Ask for the value now rather than waiting for the next change: a network whose contents have not
        // moved since the emitter was placed would otherwise report nothing until something else did.
        getMainNode().ifPresent(this::updateReportingValue);
        updateState();
    }

    /** Recounts from the grid's cached inventory, which is what the watcher would have told us. */
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

    /** The models AE2's emitter picks from, chosen by whether it is lit and whether it has a channel. */
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
