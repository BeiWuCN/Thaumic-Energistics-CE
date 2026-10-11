package thaumicenergistics_ce.util;

import java.util.Optional;

import com.mojang.serialization.DynamicOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class ThEItemTags {
    private ThEItemTags() {
    }

    private static DynamicOps<Tag> ops(HolderLookup.Provider registries) {
        return registries == null ? NbtOps.INSTANCE : registries.createSerializationContext(NbtOps.INSTANCE);
    }

    public static Tag save(ItemStack stack, HolderLookup.Provider registries) {
        return ItemStack.CODEC.encodeStart(ops(registries), stack).getOrThrow();
    }

    public static ItemStack load(Tag tag, HolderLookup.Provider registries) {
        if (tag == null) {
            return ItemStack.EMPTY;
        }
        if (tag instanceof CompoundTag compound && compound.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ItemStack.OPTIONAL_CODEC.parse(ops(registries), tag).result().orElse(ItemStack.EMPTY);
    }

    public static Optional<ItemStack> loadOptional(Tag tag, HolderLookup.Provider registries) {
        if (tag == null || (tag instanceof CompoundTag compound && compound.isEmpty())) {
            return Optional.empty();
        }
        return ItemStack.OPTIONAL_CODEC.parse(ops(registries), tag).result();
    }
}
