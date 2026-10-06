package thaumicenergistics_ce.integration.ae2;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.leclowndu93150.thaumaturge.api.aspect.AspectComponents;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 * One aspect as an ME network sees it: immutable, interned by registry id. Identity by id makes
 * a key off the network equal one read back from a cell, without which there would be two of
 * everything. Only the id is held, since name, colour and discovery state resolve on demand,
 * and keys are built where there is no level (NBT, packet, crafting planner).
 */
public final class AEssentiaKey extends AEKey {

    /** One key per aspect id, so identity and reference equality agree. */
    private static final Map<ResourceLocation, AEssentiaKey> CACHE = new ConcurrentHashMap<>();

    /**
     * Decoding goes through {@link #of}, never the constructor: AE2 maps keys on {@link #getPrimaryKey()}
     * by reference, so a fresh instance answers zero and only shows after a restart from NBT.
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

    public static AEssentiaKey of(ResourceLocation id) {
        return CACHE.computeIfAbsent(id, AEssentiaKey::new);
    }

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
     * The object AE2 groups keys by: the id itself. Its maps key on it by reference, so two keys for one
     * aspect must be one object.
     */
    @Override
    public Object getPrimaryKey() {
        return id;
    }

    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public boolean hasComponents() {
        return false;
    }

    /**
     * The key as a standalone tag. Written through {@link AEKey#CODEC}: this class's own map codec omits
     * the {@code #t} type field, which AE2's reader resolves to missing content.
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
     * The true name, not {@code AspectComponents.name}, which says "Unknown" if undiscovered. AE2 caches
     * it on the shared key, so an "Unknown" cached first would stick.
     */
    @Override
    protected Component computeDisplayName() {
        Holder<IAspect> aspect = resolveAspect();
        return aspect == null
                ? Component.literal(id.getPath())
                : AspectComponents.trueName(aspect);
    }

    public @Nullable Holder<IAspect> resolveAspect() {
        var registries = AEssentiaKeyType.clientOrServerRegistries();
        return registries == null ? null : AEssentiaKeyType.aspectOf(registries, id);
    }

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
