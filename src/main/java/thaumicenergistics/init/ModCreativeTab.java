package thaumicenergistics.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics.ThEIds;

/** The mod's creative tab. */
public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> REGISTRY =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ThEIds.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = REGISTRY.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.thaumicenergistics"))
                    .icon(() -> new ItemStack(ModItems.ARCANE_ASSEMBLER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.KNOWLEDGE_CORE.get());
                        output.accept(ModItems.ARCANE_ASSEMBLER.get());
                        output.accept(ModItems.KNOWLEDGE_INSCRIBER.get());
                        output.accept(ModItems.ESSENTIA_CELL_WORKBENCH.get());
                        output.accept(ModItems.ESSENTIA_VIBRATION_CHAMBER.get());
                        output.accept(ModItems.ESSENTIA_PROVIDER.get());
                        output.accept(ModItems.INFUSION_PROVIDER.get());
                        output.accept(ModItems.DISTILLATION_ENCODER.get());
                        output.accept(ModItems.INFUSION_MONITOR.get());
                        output.accept(ModItems.ESSENTIA_PROVIDER_CONNECTION.get());
                        output.accept(ModItems.WIRELESS_CONNECTOR.get());
                        output.accept(ModItems.ALKUSURE86_FUMO.get());
                        // Essentia storage, smallest first, so the tier order reads down the tab.
                        output.accept(ModItems.STORAGE_CASING.get());
                        output.accept(ModItems.STORAGE_COMPONENT_1K.get());
                        output.accept(ModItems.STORAGE_COMPONENT_4K.get());
                        output.accept(ModItems.STORAGE_COMPONENT_16K.get());
                        output.accept(ModItems.STORAGE_COMPONENT_64K.get());
                        output.accept(ModItems.ESSENTIA_CELL_1K.get());
                        output.accept(ModItems.ESSENTIA_CELL_4K.get());
                        output.accept(ModItems.ESSENTIA_CELL_16K.get());
                        output.accept(ModItems.ESSENTIA_CELL_64K.get());
                        output.accept(ModItems.ESSENTIA_CELL_CREATIVE.get());
                        output.accept(ModItems.ESSENTIA_TERMINAL.get());
                        output.accept(ModItems.ARCANE_CRAFTING_TERMINAL.get());
                        output.accept(ModItems.VIS_INTERFACE.get());
                        output.accept(ModItems.DIFFUSION_CORE.get());
                        output.accept(ModItems.COALESCENCE_CORE.get());
                        output.accept(ModItems.ESSENTIA_IMPORT_BUS.get());
                        output.accept(ModItems.ESSENTIA_EXPORT_BUS.get());
                        output.accept(ModItems.ESSENTIA_STORAGE_BUS.get());
                        output.accept(ModItems.ESSENTIA_LEVEL_EMITTER.get());
                        output.accept(ModItems.WIRELESS_ESSENTIA_TERMINAL.get());
                        // Accepted through the same call the item uses, so the stack in the tab is not a
                        // second, unassembled way to get this item. A creative-tab stack is never ticked
                        // and never passes through a recipe, so nothing else would fix it up.
                        output.accept(thaumicenergistics.item.ItemFocusAEWrench.assembledStack());
                        output.accept(ModItems.GOLEM_WIFI_BACKPACK.get());
                    })
                    .build());

    private ModCreativeTab() {}

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
