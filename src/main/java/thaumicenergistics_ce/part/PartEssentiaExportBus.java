package thaumicenergistics_ce.part;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.util.KeyTypeSelection;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.core.settings.TickRates;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.automation.IOBusPart;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

/**
 * The Essentia Export Bus: takes essentia out of the ME network and puts it into the container it faces.
 *
 * <p>The mirror of {@link PartEssentiaImportBus}, and built the same way - an AE2 {@code IOBusPart} with
 * its config list filtered to essentia. What it exports is whatever the config list names, so a player
 * tells a jar to keep filling with aer by putting aer in the bus.
 *
 * <p>Like the import bus it uses Thaumaturge's {@link EssentiaCapabilities#STORAGE} rather than the
 * reference build's reflection, so any block publishing essentia storage works as a target.
 *
 * <p>The order of the three steps is what keeps essentia from being destroyed: take from the network,
 * give to the container, and put back whatever the container would not accept. Doing it the other way -
 * asking the container what it wants first - would need the container to promise, and none of them do.
 */
public class PartEssentiaExportBus extends IOBusPart implements KeyTypeSelectionHost {

    @PartModels
    public static final ResourceLocation MODEL_BASE = ThEIds.id("parts/essentia_export_bus_base");

    @PartModels
    public static final ResourceLocation MODEL_OFF = ThEIds.id("parts/essentia_export_bus_off");

    @PartModels
    public static final ResourceLocation MODEL_ON = ThEIds.id("parts/essentia_export_bus_on");

    @PartModels
    public static final ResourceLocation MODEL_HAS_CHANNEL = ThEIds.id("parts/essentia_export_bus_has_channel");

    private static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF);
    private static final PartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON);
    private static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_HAS_CHANNEL);

    /** How much one operation moves, as in the reference build. */
    private static final int TRANSFER_RATE = 8;

    private static final double IDLE_POWER = 0.5;

    /** AE drawn per essentia moved. */
    private static final double AE_PER_ESSENTIA = 10.0;

    private boolean working;

    private final KeyTypeSelection essentiaOnly = new KeyTypeSelection(selection -> {}, this::isEssentia);

    public PartEssentiaExportBus(IPartItem<?> partItem) {
        super(TickRates.ExportBus, Set.of(AEssentiaKeyType.INSTANCE), partItem);
        getMainNode().setIdlePowerUsage(IDLE_POWER);
    }

    private boolean isEssentia(appeng.api.stacks.AEKeyType type) {
        return type == AEssentiaKeyType.INSTANCE;
    }

    /** The same box AE2's export bus uses, which is what the borrowed model is shaped for. */
    @Override
    public void getBoxes(IPartCollisionHelper boxes) {
        boxes.addBox(6, 6, 15, 10, 10, 16);
        boxes.addBox(6, 6, 11, 10, 10, 12);
    }

    @Override
    public IPartModel getStaticModels() {
        if (working) {
            return MODELS_ON;
        }
        return getMainNode().isOnline() ? MODELS_HAS_CHANNEL : MODELS_OFF;
    }

    @Override
    protected boolean doBusWork(IGrid grid) {
        working = false;

        Direction side = getSide();
        if (side == null) {
            return false;
        }
        if (!(getLevel() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos target = getBlockEntity().getBlockPos().relative(side);
        if (!level.isLoaded(target)) {
            return false;
        }
        // The container's own face towards us. A jar answers isConnectable true only for UP, so a bus on its
        // side is not connected and must not pretend to be. See EssentiaNeighbour.
        IEssentiaStorage storage = EssentiaNeighbour.find(level, target, side.getOpposite());
        if (storage == null) {
            return false;
        }

        // What the player asked this bus to move. The first configured aspect, as in the reference - a bus
        // exports one kind of essentia, and a player wanting two places two buses.
        AEssentiaKey key = firstConfigured();
        if (key == null) {
            return false;
        }
        Holder<IAspect> aspect = key.resolveAspect();
        if (aspect == null) {
            return false;
        }

        int wanted = Math.max(1, TRANSFER_RATE) * Math.max(1, getOperationsPerTick());
        long taken = grid.getStorageService()
                .getInventory()
                .extract(key, wanted, Actionable.MODULATE, actionSource());
        if (taken <= 0) {
            return false;
        }

        int accepted = storage.insert(aspect, (int) Math.min(taken, Integer.MAX_VALUE), false);
        if (accepted < taken) {
            // The container filled up part way. What it would not take goes back to the network, or it
            // would simply cease to exist.
            grid.getStorageService()
                    .getInventory()
                    .insert(key, taken - accepted, Actionable.MODULATE, actionSource());
        }
        if (accepted > 0) {
            grid.getEnergyService()
                    .extractAEPower(accepted * AE_PER_ESSENTIA, Actionable.MODULATE, PowerMultiplier.CONFIG);
            working = true;
            return true;
        }
        return false;
    }

    /** The first essentia the config list names, or {@code null} when it names none. */
    private AEssentiaKey firstConfigured() {
        for (int slot = 0; slot < getConfig().size(); slot++) {
            AEKey key = getConfig().getKey(slot);
            if (key instanceof AEssentiaKey essentia) {
                return essentia;
            }
        }
        return null;
    }

    private IActionSource actionSource() {
        return IActionSource.ofMachine(this);
    }

    @Override
    protected MenuType<?> getMenuType() {
        return ModMenuTypes.ESSENTIA_EXPORT_BUS.get();
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
