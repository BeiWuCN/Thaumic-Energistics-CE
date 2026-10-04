package thaumicenergistics_ce.part;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.util.KeyTypeSelection;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.items.parts.PartModels;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.automation.UpgradeablePart;
import appeng.util.ConfigInventory;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.phys.Vec3;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.integration.ae2.EssentiaMEStorage;

/**
 * The Essentia Storage Bus: makes the essentia container it faces part of the ME network.
 * <ul>
 *   <li>A provider, not a mover, which is what the reference build got wrong: its version pulled
 *       essentia into the network each tick and never implemented {@code IStorageProvider}, so a
 *       jar behind it was invisible and nothing put back.
 *   <li>Mounted as storage, a jar behind it is listed, counts towards the network's contents, and
 *       both fills and drains; on its own it only announces changes, see {@link #tickingRequest}.
 * </ul>
 */
public class PartEssentiaStorageBus extends UpgradeablePart
        implements IStorageProvider, IGridTickable, KeyTypeSelectionHost {

    @PartModels
    public static final ResourceLocation MODEL_BASE = ThEIds.id("parts/essentia_storage_bus_base");

    @PartModels
    public static final ResourceLocation MODEL_OFF = ThEIds.id("parts/essentia_storage_bus_off");

    @PartModels
    public static final ResourceLocation MODEL_ON = ThEIds.id("parts/essentia_storage_bus_on");

    @PartModels
    public static final ResourceLocation MODEL_HAS_CHANNEL = ThEIds.id("parts/essentia_storage_bus_has_channel");

    /** Every model that has to be registered for this part, named once for {@code ThaumicEnergistics}. */
    public static final List<ResourceLocation> MODEL_LOCATIONS =
            List.of(MODEL_BASE, MODEL_OFF, MODEL_ON, MODEL_HAS_CHANNEL);

    private static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF);
    private static final PartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON);
    private static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_HAS_CHANNEL);

    private static final double IDLE_POWER = 1.0;

    /** AE2 asks a provider for storage only when told to, so this poll notices a jar filled by hand. */
    private static final int POLL_INTERVAL = 20;

    /** The container revision last mounted, so the network is only told when it really moved. */
    private long mountedRevision = Long.MIN_VALUE;

    /** The storage view last handed to the network, or {@code null} when nothing is mounted. */
    private EssentiaMEStorage mounted;

    /**
     * The config list, for a storage bus a partition: which aspects the network may put in this
     * container and take out. Here because {@code UpgradeablePart} has no config inventory.
     */
    private final ConfigInventory config = ConfigInventory.configTypes(63)
            .supportedTypes(Set.of(AEssentiaKeyType.INSTANCE))
            .changeListener(this::onConfigChanged)
            .build();

    /** Only essentia: this bus is for essentia containers, so no other key type is offered. */
    private final KeyTypeSelection essentiaOnly = new KeyTypeSelection(selection -> {}, this::isEssentia);

    public PartEssentiaStorageBus(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(IDLE_POWER);
        getMainNode().addService(IStorageProvider.class, this).addService(IGridTickable.class, this);
    }

    private boolean isEssentia(AEKeyType type) {
        return type == AEssentiaKeyType.INSTANCE;
    }

    /** The same box AE2's storage bus uses, which is what the borrowed model is shaped for. */
    @Override
    public void getBoxes(IPartCollisionHelper boxes) {
        boxes.addBox(5, 5, 12, 11, 11, 14);
        boxes.addBox(2, 2, 14, 14, 14, 15);
        boxes.addBox(3, 3, 15, 13, 13, 16);
    }

    @Override
    public IPartModel getStaticModels() {
        return getMainNode().isOnline() ? MODELS_HAS_CHANNEL : MODELS_OFF;
    }

    /**
     * Hands the network a view of the container this bus faces. No essentia storage, nothing is
     * mounted, which is how a bus on a wall costs the network nothing.
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
     * Wakes once a second to see whether the container moved. The network caches what a provider
     * mounted, so the revision keeps {@code requestUpdate} from being sent for an untouched jar.
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
        // Through EssentiaNeighbour, so the container is asked whether it accepts this face (a jar
        // only takes UP) and so a pipe exposing the transport capability can be attached too.
        return EssentiaNeighbour.find(level, target, side.getOpposite());
    }

    public ConfigInventory getConfig() {
        return config;
    }

    /** Changing the partition changes what the network may do here, so it has to be told. */
    private void onConfigChanged() {
        savePart();
        if (getMainNode().getNode() != null) {
            IStorageProvider.requestUpdate(getMainNode());
        }
    }

    /**
     * Persists this part through the part host, where a part's data lives: a part is not a block
     * entity and has no {@code setChanged} of its own.
     */
    private void savePart() {
        if (getHost() != null) {
            getHost().markForSave();
        }
    }

    /**
     * Opens the partition screen when the part is used with an empty hand. {@code UpgradeablePart}
     * has no menu hook, so this is written out rather than inherited.
     */
    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (player.level().isClientSide) {
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
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        essentiaOnly.readFromNBT(data, registries);
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        essentiaOnly.writeToNBT(data);
    }
}
