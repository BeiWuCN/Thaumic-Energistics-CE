package thaumicenergistics_ce.util;

import java.util.Optional;

import com.mojang.serialization.DynamicOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * 26.1.2 上 {@link ItemStack} 的 NBT 往返读写。
 *
 * 原版砍掉了 {@code ItemStack#save(HolderLookup.Provider)} 和它的两个 optional 兄弟，改用
 * {@code ItemStack.CODEC}，但 codec 仍会走每个组件自己的 codec，装着要素的组件会经过
 * {@code RegistryFixedCodec}：交给它的 ops 不带注册表就抛 {@code Can't access
 * registry thaumaturge:aspect}。所以当年满足那三个签名用的 provider 这里还得留着——
 * 它买的是 ops，不是签名。
 */
public final class ThEItemTags {
    private ThEItemTags() {
    }

    /**
     * {@code ItemStack.CODEC} 用的带注册表后端；provider 为 null 就退回普通 NBT，
     * 只在物品堆上没有组件需要注册表时才管用。
     */
    private static DynamicOps<Tag> ops(HolderLookup.Provider registries) {
        return registries == null ? NbtOps.INSTANCE : registries.createSerializationContext(NbtOps.INSTANCE);
    }

    /** 写一个已知非空的物品堆；也就是旧的 {@code ItemStack#save(Provider)}。 */
    public static Tag save(ItemStack stack, HolderLookup.Provider registries) {
        return ItemStack.CODEC.encodeStart(ops(registries), stack).getOrThrow();
    }

    /** 读回物品堆，读不出来的一律给 {@link ItemStack#EMPTY}。 */
    public static ItemStack load(Tag tag, HolderLookup.Provider registries) {
        if (tag == null) {
            return ItemStack.EMPTY;
        }
        if (tag instanceof CompoundTag compound && compound.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ItemStack.OPTIONAL_CODEC.parse(ops(registries), tag).result().orElse(ItemStack.EMPTY);
    }

    /** 读回物品堆，值完全读不出来时返回空。 */
    public static Optional<ItemStack> loadOptional(Tag tag, HolderLookup.Provider registries) {
        if (tag == null || (tag instanceof CompoundTag compound && compound.isEmpty())) {
            return Optional.empty();
        }
        return ItemStack.OPTIONAL_CODEC.parse(ops(registries), tag).result();
    }
}
