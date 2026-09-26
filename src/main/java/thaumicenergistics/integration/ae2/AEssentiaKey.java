package thaumicenergistics.integration.ae2;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.leclowndu93150.thaumaturge.api.aspect.AspectComponents;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * One aspect, as an ME network sees it.
 *
 * <p>Immutable and interned. Its identity is the aspect's registry id and nothing else, which is what
 * lets a key that arrived over the network equal a key that was read back out of a storage cell - if
 * they did not, a cell would appear to hold two of everything.
 *
 * <p>A key holds only an id. Everything the player sees - the name, the colour, whether they have
 * discovered it - is resolved from that id when it is asked for, because a key is built in places that
 * have no level: from NBT, from a packet, from the crafting planner. Carrying a resolved aspect would
 * make the same essentia into two different keys depending on which side built it.
 */
public final class AEssentiaKey extends AEKey {

    /** One key per aspect id, so identity and reference equality agree. */
    private static final Map<ResourceLocation, AEssentiaKey> CACHE = new ConcurrentHashMap<>();

    /**
     * Read back through {@link #of}, never through the constructor.
     *
     * <p>Identity is the whole point of the cache above, and {@link AEKey} does not override
     * {@code equals}/{@code hashCode}: an equal key of a different instance is a key AE2 cannot find.
     * {@code KeyCounter} is a {@code Reference2ObjectMap} keyed on {@link #getPrimaryKey()}, so a key
     * decoded from a cell's tag into a fresh instance answers {@code get} with <b>zero</b> while the very
     * same counter iterates over the aspect with its full amount - the terminal lists it, and nothing the
     * server does can find it.
     *
     * <p>This cost a round: everything worked within one session, because depositing built its key
     * through {@code of} and the fill path's cache hit returned that same instance. Only after a restart -
     * when the storage cell was reloaded from NBT and every key came back through this codec - did aspects
     * other than the last one deposited become unfillable. "Measure, do not infer": the two log lines that
     * settled it were {@code get()} returning 0 and the counter printing 218 for the same aspect.
     */
    public static final MapCodec<AEssentiaKey> MAP_CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(AEssentiaKey::getId)
    ).apply(builder, AEssentiaKey::of));

    public static final StreamCodec<RegistryFriendlyByteBuf, AEssentiaKey> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC,
                    AEssentiaKey::getId,
                    AEssentiaKey::of);

    private final ResourceLocation id;

    private AEssentiaKey(ResourceLocation id) {
        this.id = id;
    }

    /** The key for an aspect id, interned. */
    public static AEssentiaKey of(ResourceLocation id) {
        return CACHE.computeIfAbsent(id, AEssentiaKey::new);
    }

    /** The key for an aspect, or {@code null} when the holder is not backed by a registry entry. */
    public static @Nullable AEssentiaKey of(Holder<IAspect> aspect) {
        return aspect.unwrapKey().map(key -> of(key.location())).orElse(null);
    }

    public static AEssentiaKey fromPacket(RegistryFriendlyByteBuf buffer) {
        return of(ResourceLocation.STREAM_CODEC.decode(buffer));
    }

    @Override
    public AEKeyType getType() {
        return AEssentiaKeyType.INSTANCE;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    /**
     * The object AE2 groups keys by.
     *
     * <p>The id itself, not a wrapper around it. AE2's internal maps are keyed on this by reference, so
     * two keys for one aspect must hand back the same object - which they do, because {@code of} interns
     * by id and a {@code ResourceLocation} is interned by its own constructor.
     */
    @Override
    public Object getPrimaryKey() {
        return id;
    }

    /** Essentia has no secondary form to drop. */
    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public boolean hasComponents() {
        return false;
    }

    /**
     * The key as a standalone tag.
     *
     * <p>Written through {@link AEKey#CODEC}, not through this class's own map codec, and the difference
     * is measurable: a {@code MapCodec} does not write the {@code #t} field that says which key type it
     * is - that field is added by {@link AEKey#MAP_CODEC}, which dispatches on the type. A tag encoded
     * from the map codec alone carries the id and nothing else, and AE2's own reader resolves it to
     * <em>missing content</em> without raising anything.
     *
     * <p>Not a hypothetical: the self-test printed {@code {id:"thaumaturge:aer"}} from the map codec and
     * the key came back as an item key, while the same key decoded through its own map codec was correct.
     * The generic codec is the one the rest of AE2 uses, so it is the one that has to work.
     */
    @Override
    public CompoundTag toTag(HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        return (CompoundTag) AEKey.CODEC.encodeStart(ops, this).getOrThrow();
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf buffer) {
        ResourceLocation.STREAM_CODEC.encode(buffer, id);
    }

    /**
     * The aspect's name.
     *
     * <p>The true name rather than {@code AspectComponents.name}, which answers "Unknown" for an aspect
     * the current player has not discovered. AE2 caches what this returns on the key, and the key is
     * shared - a name cached while the player knew nothing of an aspect would stay "Unknown" after they
     * learned it. The research system decides where an undiscovered aspect may be named; a storage
     * terminal showing what a cell holds is not one of those places.
     */
    @Override
    protected Component computeDisplayName() {
        Holder<IAspect> aspect = resolveAspect();
        return aspect == null
                ? Component.literal(id.getPath())
                : AspectComponents.trueName(aspect);
    }

    /** The aspect this key names, or {@code null} when there are no registries to resolve it against. */
    public @Nullable Holder<IAspect> resolveAspect() {
        var registries = AEssentiaKeyType.clientOrServerRegistries();
        return registries == null ? null : AEssentiaKeyType.aspectOf(registries, id);
    }

    /**
     * Essentia does not drop as an item.
     *
     * <p>Called when a cell's contents are spilled. There is no essentia item to spill - the aspect
     * exists only as this key - so the contents are simply lost, which is also what the reference build
     * does. Vaporising essentia back into the aura would be a second, unrelated mechanic.
     */
    @Override
    public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        // Intentionally nothing.
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof AEssentiaKey key && id.equals(key.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "AEssentiaKey{" + id + "}";
    }
}
