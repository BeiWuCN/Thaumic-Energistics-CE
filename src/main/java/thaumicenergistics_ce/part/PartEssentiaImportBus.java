package thaumicenergistics_ce.part;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.SchedulingMode;
import appeng.api.config.Settings;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKeyType;
import appeng.api.util.IConfigManagerBuilder;
import appeng.api.util.KeyTypeSelection;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.core.settings.TickRates;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.automation.IOBusPart;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

/**
 * The Essentia Import Bus: pulls essentia out of the container it faces and into the ME network.
 * <ul>
 * <li>Extends {@code IOBusPart}, inheriting AE2's upgrades, power accounting, redstone and config GUI.
 * <li>Reads the neighbour through {@link EssentiaCapabilities#STORAGE}, not reflection: jars, alembics,
 * the reservoir, or any block another mod adds. Essentia the network will not take is put back, not lost.
 * </ul>
 */
public class PartEssentiaImportBus extends IOBusPart implements KeyTypeSelectionHost {

    @PartModels
    public static final ResourceLocation MODEL_BASE = ThEIds.id("parts/essentia_import_bus_base");

    @PartModels
    public static final ResourceLocation MODEL_OFF = ThEIds.id("parts/essentia_import_bus_off");

    @PartModels
    public static final ResourceLocation MODEL_ON = ThEIds.id("parts/essentia_import_bus_on");

    @PartModels
    public static final ResourceLocation MODEL_HAS_CHANNEL = ThEIds.id("parts/essentia_import_bus_has_channel");

    /** Every model that has to be registered for this part, named once for {@code ThaumicEnergistics}. */
    public static final List<ResourceLocation> MODEL_LOCATIONS =
            List.of(MODEL_BASE, MODEL_OFF, MODEL_ON, MODEL_HAS_CHANNEL);

    private static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF);
    private static final PartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON);
    private static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_HAS_CHANNEL);

    /**
     * How much one operation moves: eight, one jar's worth and the reference build's figure. Per tick
     * this is multiplied by the operations the acceleration cards allow.
     */
    private static final int TRANSFER_RATE = 8;

    /** Idle draw, as in the reference build. */
    private static final double IDLE_POWER = 0.5;

    /** AE drawn per essentia moved. */
    private static final double AE_PER_ESSENTIA = 10.0;

    /** True while the bus moved something on its last tick, which is what lights the part up. */
    private boolean working;

    /** Essentia only: the config list must not offer items or fluids. */
    private final KeyTypeSelection essentiaOnly = new KeyTypeSelection(selection -> {}, this::isEssentia);

    public PartEssentiaImportBus(IPartItem<?> partItem) {
        super(TickRates.ImportBus, Set.of(AEssentiaKeyType.INSTANCE), partItem);
        getMainNode().setIdlePowerUsage(IDLE_POWER);
    }

    private boolean isEssentia(AEKeyType type) {
        return type == AEssentiaKeyType.INSTANCE;
    }

    @Override
    protected void registerSettings(IConfigManagerBuilder builder) {
        super.registerSettings(builder);
        builder.registerSetting(Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT);
    }

    /** The same box AE2's import bus uses, which is what the borrowed model is shaped for. */
    @Override
    public void getBoxes(IPartCollisionHelper boxes) {
        boxes.addBox(6, 6, 11, 10, 10, 13);
        boxes.addBox(5, 5, 13, 11, 11, 14);
        boxes.addBox(4, 4, 14, 12, 12, 16);
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
        // The container's own face towards us; one that does not accept that face is not connected - a
        // jar only takes essentia from above, so a bus on its side has nothing to do. See EssentiaNeighbour.
        IEssentiaStorage storage = EssentiaNeighbour.find(level, target, side.getOpposite());
        if (storage == null) {
            return false;
        }

        int wanted = Math.max(1, TRANSFER_RATE) * Math.max(1, getOperationsPerTick());
        // Whatever the container is willing to give, up to what one tick can carry. Asking per aspect
        // because a container can hold several and the network may only have room for some of them.
        for (AspectInstance entry : storage.contents().sortedByAmount()) {
            Holder<IAspect> aspect = entry.aspect();
            if (aspect == null || entry.amount() <= 0) {
                continue;
            }
            AEssentiaKey key = AEssentiaKey.of(aspect);
            if (key == null) {
                // Not registry-backed: no id to insert under, so nothing is taken from the container.
                continue;
            }
            int taken = storage.extract(aspect, Math.min(wanted, entry.amount()), false);
            if (taken <= 0) {
                continue;
            }
            long inserted =
                    grid.getStorageService().getInventory().insert(key, taken, Actionable.MODULATE, actionSource());
            if (inserted < taken) {
                // The network would not take all of it. Hand the remainder back rather than destroying it.
                storage.insert(aspect, (int) (taken - inserted), false);
            }
            if (inserted > 0) {
                grid.getEnergyService()
                        .extractAEPower(inserted * AE_PER_ESSENTIA, Actionable.MODULATE, PowerMultiplier.CONFIG);
                working = true;
                return true;
            }
        }
        return false;
    }

    /** The bus's own action source, which is what the network charges and attributes the insert to. */
    private IActionSource actionSource() {
        return IActionSource.ofMachine(this);
    }

    @Override
    protected MenuType<?> getMenuType() {
        return ModMenuTypes.ESSENTIA_IMPORT_BUS.get();
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
