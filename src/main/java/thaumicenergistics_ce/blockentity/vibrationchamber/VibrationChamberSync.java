package thaumicenergistics_ce.blockentity.vibrationchamber;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber.BurnState;

public final class VibrationChamberSync {


    public static final int ESSENTIA = 0;
    public static final int ESSENTIA_MAX = 1;
    public static final int ENERGY = 2;
    public static final int ENERGY_MAX = 3;
    public static final int BURN = 4;
    public static final int BURN_TOTAL = 5;

    public static final int AE_PER_TICK = 6;

    public static final int ASPECT_COLOUR = 7;
    public static final int STATE = 8;
    public static final int COUNT = 9;


    private static final String KEY_ESSENTIA = "StoredEssentia";
    private static final String KEY_BURN = "BurnTicksRemaining";
    private static final String KEY_BURN_TOTAL = "TotalBurnTicks";
    private static final String KEY_AE_PER_TICK = "AePerTick";
    private static final String KEY_ENERGY = "StoredEnergy";
    private static final String KEY_ASPECT = "CurrentAspect";

    private VibrationChamberSync() {
    }

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

    private static int aspectColour(BlockEntityEssentiaVibrationChamber chamber) {
        Holder<IAspect> aspect = chamber.currentAspectHolder();
        return aspect == null ? 0 : aspect.value().color() | 0xFF000000;
    }


    static void writeStream(RegistryFriendlyByteBuf data, BlockEntityEssentiaVibrationChamber chamber) {
        data.writeByte(chamber.getBurnState().ordinal());
        data.writeDouble(chamber.getAePerTick());
        data.writeVarInt(chamber.getStoredEssentia());
        Identifier aspect = chamber.getCurrentAspect();
        data.writeBoolean(aspect != null);
        if (aspect != null) {
            data.writeIdentifier(aspect);
        }
    }

    record Streamed(BurnState state, double aePerTick, int essentia, @Nullable Identifier aspect) {
    }

    static Streamed readStream(RegistryFriendlyByteBuf data) {
        BurnState state = BurnState.byOrdinal(data.readByte());
        double rate = data.readDouble();
        int essentia = data.readVarInt();
        Identifier aspect = data.readBoolean() ? data.readIdentifier() : null;
        return new Streamed(state, rate, essentia, aspect);
    }

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


    static void writePersistent(ValueOutput output, BlockEntityEssentiaVibrationChamber chamber) {
        output.putInt(KEY_ESSENTIA, chamber.getStoredEssentia());
        output.putInt(KEY_BURN, chamber.getBurnTicksRemaining());
        output.putInt(KEY_BURN_TOTAL, chamber.getTotalBurnTicks());
        output.putDouble(KEY_AE_PER_TICK, chamber.getAePerTick());
        output.putDouble(KEY_ENERGY, chamber.getStoredEnergy());
        Identifier aspect = chamber.getCurrentAspect();
        if (aspect != null) {
            output.putString(KEY_ASPECT, aspect.toString());
        }
    }

    record Persisted(int essentia, int burnTicksRemaining, int totalBurnTicks, double aePerTick,
            double storedEnergy, @Nullable Identifier aspect) {
    }

    static Persisted readPersistent(ValueInput input) {
        int essentia = Math.clamp(
                input.getIntOr(KEY_ESSENTIA, 0), 0, BlockEntityEssentiaVibrationChamber.MAX_ESSENTIA);
        double storedEnergy = Math.min(
                input.getDoubleOr(KEY_ENERGY, 0.0D), BlockEntityEssentiaVibrationChamber.MAX_ENERGY_STORAGE);
        Identifier aspect = input.getString(KEY_ASPECT).map(Identifier::tryParse).orElse(null);
        return new Persisted(essentia, input.getIntOr(KEY_BURN, 0), input.getIntOr(KEY_BURN_TOTAL, 0),
                input.getDoubleOr(KEY_AE_PER_TICK, 0.0D), storedEnergy, aspect);
    }

    static void applyPersistent(BlockEntityEssentiaVibrationChamber chamber, ValueInput input) {
        Persisted persisted = readPersistent(input);
        HolderLookup.Provider registries = input.lookup();
        chamber.tank().set(persisted.essentia(), persisted.aspect() == null
                ? null
                : Aspects.resolve(registries, ResourceKey.create(IAspect.REGISTRY_KEY, persisted.aspect())));
        chamber.burn().restore(persisted.burnTicksRemaining(), persisted.totalBurnTicks(),
                persisted.aePerTick());
        chamber.energy().restore(persisted.storedEnergy());
        chamber.burn().setState(chamber.energy().isFull() ? BurnState.PAUSED_FULL : BurnState.IDLE);
    }

}
