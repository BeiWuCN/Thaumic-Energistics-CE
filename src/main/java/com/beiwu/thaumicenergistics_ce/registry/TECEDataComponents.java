package com.beiwu.thaumicenergistics_ce.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.beiwu.thaumicenergistics_ce.ThEIds;

public final class TECEDataComponents {
    private static final DeferredRegister<DataComponentType<?>> REGISTRY =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ThEIds.MODID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> QUANTUM_SINGULARITY =
            REGISTRY.register("quantum_singularity", () -> DataComponentType.<ItemContainerContents>builder()
                    .persistent(ItemContainerContents.CODEC).networkSynchronized(ItemContainerContents.STREAM_CODEC).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> WIRELESS_ARCANE_ITEMS =
            REGISTRY.register("wireless_arcane_items", () -> DataComponentType.<ItemContainerContents>builder()
                    .persistent(ItemContainerContents.CODEC).networkSynchronized(ItemContainerContents.STREAM_CODEC).build());

    /** 无线奥术编码终端：九格编码网格。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> WIRELESS_ARCANE_ENCODING_GRID =
            REGISTRY.register("wireless_arcane_encoding_grid", () -> DataComponentType.<ItemContainerContents>builder()
                    .persistent(ItemContainerContents.CODEC).networkSynchronized(ItemContainerContents.STREAM_CODEC).build());

    /** 无线奥术编码终端：装着样板的那个知识核心。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> WIRELESS_ARCANE_ENCODING_CORE =
            REGISTRY.register("wireless_arcane_encoding_core", () -> DataComponentType.<ItemContainerContents>builder()
                    .persistent(ItemContainerContents.CODEC).networkSynchronized(ItemContainerContents.STREAM_CODEC).build());

    /** 无线奥术合成终端：付费源模式，true = 水晶优先，缺省按法杖优先。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> WIRELESS_ARCANE_CRYSTAL_FIRST =
            REGISTRY.register("wireless_arcane_crystal_first", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

    /** 无线奥术编码终端：选中的样板下标，默认 -1。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WIRELESS_ARCANE_ENCODING_SELECTED =
            REGISTRY.register("wireless_arcane_encoding_selected", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    private TECEDataComponents() {}

    public static void register(IEventBus bus) {
        REGISTRY.register(bus);
    }
}
