package thaumicenergistics_ce.blockentity.vibrationchamber;

import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber.BurnState;

/**
 * The one place that says how the Essentia Vibration Chamber is read, sent and saved.
 * <ul>
 * <li>The menu's readings are listed here, in the order {@code ContainerData} carries them, and the
 *     byte order of {@link #writeStream} is fixed with it: both are contracts with open screens.
 * <li>The save tag, AE2's byte stream and the menu readings used to encode the same state apart.
 * </ul>
 */
public final class VibrationChamberSync {

    // The readings, in the order the screen reads them out of ContainerData. The numbers, not just the
    // names, are the contract: an already-open menu parses by number.

    public static final int ESSENTIA = 0;
    public static final int ESSENTIA_MAX = 1;
    public static final int ENERGY = 2;
    public static final int ENERGY_MAX = 3;
    public static final int BURN = 4;
    public static final int BURN_TOTAL = 5;

    /** Power per tick, times ten: the reading has one decimal and ContainerData carries ints. */
    public static final int AE_PER_TICK = 6;

    public static final int ASPECT_COLOUR = 7;
    public static final int STATE = 8;
    public static final int COUNT = 9;

    // Save keys. Older worlds carry exactly these names, so they are fixed.

    private static final String KEY_ESSENTIA = "StoredEssentia";
    private static final String KEY_BURN = "BurnTicksRemaining";
    private static final String KEY_BURN_TOTAL = "TotalBurnTicks";
    private static final String KEY_AE_PER_TICK = "AePerTick";
    private static final String KEY_ENERGY = "StoredEnergy";
    private static final String KEY_ASPECT = "CurrentAspect";

    private VibrationChamberSync() {
    }

    /**
     * What the server reads off the machine. A client must read what it was sent instead: the machine
     * it can see holds no fuel, so these numbers would be wrong on the screen.
     */
    public static int reading(BlockEntityEssentiaVibrationChamber chamber, int index) {
        if (chamber == null) {
            return 0;
        }
        return switch (index) {
            case ESSENTIA -> chamber.getStoredEssentia();
            case ESSENTIA_MAX -> BlockEntityEssentiaVibrationChamber.MAX_ESSENTIA;
            case ENERGY -> (int) Math.round(chamber.getStoredEnergy());
            case ENERGY_MAX -> (int) BlockEntityEssentiaVibrationChamber.MAX_ENERGY_STORAGE;
            case BURN -> chamber.getBurnTicksRemaining();
            case BURN_TOTAL -> chamber.getTotalBurnTicks();
            case AE_PER_TICK -> (int) Math.round(chamber.getAePerTick() * 10.0);
            case ASPECT_COLOUR -> aspectColour(chamber);
            case STATE -> chamber.getBurnState().ordinal();
            default -> 0;
        };
    }

    /** The held aspect's colour, made opaque, or 0 when nothing is held. */
    private static int aspectColour(BlockEntityEssentiaVibrationChamber chamber) {
        Holder<IAspect> aspect = chamber.currentAspectHolder();
        return aspect == null ? 0 : aspect.value().color() | 0xFF000000;
    }

    // AE2's byte stream: state, burn rate, buffered fuel, then the aspect the client draws.

    static void writeStream(RegistryFriendlyByteBuf data, BlockEntityEssentiaVibrationChamber chamber) {
        data.writeByte(chamber.getBurnState().ordinal());
        data.writeDouble(chamber.getAePerTick());
        data.writeVarInt(chamber.getStoredEssentia());
        ResourceLocation aspect = chamber.getCurrentAspect();
        data.writeBoolean(aspect != null);
        if (aspect != null) {
            data.writeResourceLocation(aspect);
        }
    }

    /** What {@link #writeStream} put on the wire, for the block entity to compare and apply. */
    record Streamed(BurnState state, double aePerTick, int essentia, @Nullable ResourceLocation aspect) {
    }

    static Streamed readStream(RegistryFriendlyByteBuf data) {
        BurnState state = BurnState.byOrdinal(data.readByte());
        double rate = data.readDouble();
        int essentia = data.readVarInt();
        ResourceLocation aspect = data.readBoolean() ? data.readResourceLocation() : null;
        return new Streamed(state, rate, essentia, aspect);
    }

    /**
     * Puts the stream on the machine and says whether anything moved, so the block entity can answer AE2
     * with one flag. The aspect is resolved here: the registry lives on the wire, not on the machine.
     */
    static boolean applyStreamed(BlockEntityEssentiaVibrationChamber chamber, RegistryFriendlyByteBuf data) {
        Streamed streamed = readStream(data);
        boolean changed = chamber.getBurnState() != streamed.state()
                || chamber.getAePerTick() != streamed.aePerTick()
                || chamber.getStoredEssentia() != streamed.essentia()
                || !Objects.equals(chamber.getCurrentAspect(), streamed.aspect());
        Holder<IAspect> aspect = streamed.aspect() == null
                ? null
                : Aspects.resolve(data.registryAccess(),
                        ResourceKey.create(IAspect.REGISTRY_KEY, streamed.aspect()));
        chamber.applyStreamed(streamed.state(), streamed.aePerTick(), streamed.essentia(), aspect);
        return changed;
    }

    // The save tag: what a reload needs, in its own names and order rather than the stream's.

    static void writePersistent(CompoundTag tag, BlockEntityEssentiaVibrationChamber chamber) {
        tag.putInt(KEY_ESSENTIA, chamber.getStoredEssentia());
        tag.putInt(KEY_BURN, chamber.getBurnTicksRemaining());
        tag.putInt(KEY_BURN_TOTAL, chamber.getTotalBurnTicks());
        tag.putDouble(KEY_AE_PER_TICK, chamber.getAePerTick());
        tag.putDouble(KEY_ENERGY, chamber.getStoredEnergy());
        ResourceLocation aspect = chamber.getCurrentAspect();
        if (aspect != null) {
            tag.putString(KEY_ASPECT, aspect.toString());
        }
    }

    /** The save tag read back, clamped; the block entity picks its state and resolves the aspect. */
    record Persisted(int essentia, int burnTicksRemaining, int totalBurnTicks, double aePerTick,
            double storedEnergy, @Nullable ResourceLocation aspect) {
    }

    static Persisted readPersistent(CompoundTag tag) {
        int essentia = Math.clamp(
                tag.getInt(KEY_ESSENTIA), 0, BlockEntityEssentiaVibrationChamber.MAX_ESSENTIA);
        double storedEnergy = Math.min(
                tag.getDouble(KEY_ENERGY), BlockEntityEssentiaVibrationChamber.MAX_ENERGY_STORAGE);
        ResourceLocation aspect = tag.contains(KEY_ASPECT)
                ? ResourceLocation.tryParse(tag.getString(KEY_ASPECT))
                : null;
        return new Persisted(essentia, tag.getInt(KEY_BURN), tag.getInt(KEY_BURN_TOTAL),
                tag.getDouble(KEY_AE_PER_TICK), storedEnergy, aspect);
    }
}
