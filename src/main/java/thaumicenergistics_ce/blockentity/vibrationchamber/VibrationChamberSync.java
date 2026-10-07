package thaumicenergistics_ce.blockentity.vibrationchamber;

import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber.BurnState;

/**
 * 源质振动室怎么读、怎么发、怎么存的唯一出处。
 * 菜单读数的顺序就是 [ContainerData] 承载它们的顺序，{@link #writeStream} 的字节序照着排，
 * 两者都是跟已打开界面的契约。以前保存标签、AE2 字节流、菜单读数各编一份同样的状态。
 */
public final class VibrationChamberSync {

    // 各项读数按界面从 [ContainerData] 读取的顺序排。
    // 契约认的是数字不是名字：已打开的菜单按数字解析。

    public static final int ESSENTIA = 0;
    public static final int ESSENTIA_MAX = 1;
    public static final int ENERGY = 2;
    public static final int ENERGY_MAX = 3;
    public static final int BURN = 4;
    public static final int BURN_TOTAL = 5;

    /** 每 tick 功率乘十后的值：读数带一位小数，[ContainerData] 只能装 int。 */
    public static final int AE_PER_TICK = 6;

    public static final int ASPECT_COLOUR = 7;
    public static final int STATE = 8;
    public static final int COUNT = 9;

    // 保存键。旧世界用的就是这些名字，不能改。

    private static final String KEY_ESSENTIA = "StoredEssentia";
    private static final String KEY_BURN = "BurnTicksRemaining";
    private static final String KEY_BURN_TOTAL = "TotalBurnTicks";
    private static final String KEY_AE_PER_TICK = "AePerTick";
    private static final String KEY_ENERGY = "StoredEnergy";
    private static final String KEY_ASPECT = "CurrentAspect";

    private VibrationChamberSync() {
    }

    /**
     * 服务端从机器上读到的数。客户端要改读发给它的数：
     * 客户端看到的机器不持有燃料，照它画会是错的。
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

    /** 所持要素的颜色，置成不透明；什么都没持有就是 0。 */
    private static int aspectColour(BlockEntityEssentiaVibrationChamber chamber) {
        Holder<IAspect> aspect = chamber.currentAspectHolder();
        return aspect == null ? 0 : aspect.value().color() | 0xFF000000;
    }

    // AE2 字节流：状态、燃烧速率、缓冲燃料，最后是客户端要画的要素。

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

    /** {@link #writeStream} 发到线上的字段，方块实体用它比较和应用。 */
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
     * 把字节流写进机器，并告诉调用方有没有变化，方块实体用一个标志回 AE2。
     * 要素在这里解析：注册表在线上，不在机器上。
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

    // 保存标签：重载要用的字段，名字和顺序自成一套，不跟字节流共用。

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

    /** 读回来的保存标签，已钳制；状态由方块实体自己挑，要素也由它解析。 */
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

    static void applyPersistent(BlockEntityEssentiaVibrationChamber chamber, CompoundTag tag,
            HolderLookup.Provider registries) {
        Persisted persisted = readPersistent(tag);
        chamber.tank().set(persisted.essentia(), persisted.aspect() == null
                ? null
                : Aspects.resolve(registries, ResourceKey.create(IAspect.REGISTRY_KEY, persisted.aspect())));
        chamber.burn().restore(persisted.burnTicksRemaining(), persisted.totalBurnTicks(),
                persisted.aePerTick());
        chamber.energy().restore(persisted.storedEnergy());
        // 从槽位读出，不跟着保存：两者都写进标签会互相矛盾。
        chamber.burn().setState(chamber.energy().isFull() ? BurnState.PAUSED_FULL : BurnState.IDLE);
    }

}
