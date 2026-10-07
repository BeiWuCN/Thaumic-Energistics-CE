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
 * ME 网络眼中的一种要素：不可变，按注册表 id 驻留。
 * 身份用 id，网络外造的键才等于从存储元件读回的键，否则同一要素会有两份。
 * 只存 id：名称、颜色、发现状态都按需解析，键还会在没有 level 时构建
 * （NBT、数据包、合成规划器）。
 */
public final class AEssentiaKey extends AEKey {

    /** 每个要素 id 一个键，让身份相等与引用相等一致。 */
    private static final Map<ResourceLocation, AEssentiaKey> CACHE = new ConcurrentHashMap<>();

    /**
     * 解码一律走 {@link #of}，绝不用构造函数：AE2 按引用在 {@link #getPrimaryKey()} 上映射键，
     * 新造的实例会答 0，重启时从 NBT 读回才显现。
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
     * AE2 给键分组所用的对象：id 本身。映射按引用比较，
     * 同一要素的两个键得是同一个对象。
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
     * 键作为独立标签的形式。走 {@link AEKey#CODEC} 写出：
     * 本类自己的映射编解码器会省略 {@code #t} 类型字段，AE2 的读取器会读成内容缺失。
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
     * 真正的名称，不用 {@code AspectComponents.name}：后者在未发现时给出 "Unknown"，
     * AE2 把它缓存在共享键上，先进去的 "Unknown" 会一直留着。
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
        // 有意留空。
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
