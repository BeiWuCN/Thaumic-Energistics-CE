package com.beiwu.thaumicenergistics_ce.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.beiwu.thaumicenergistics_ce.ThEIds;

public final class TECECreativeTab {
    public static final DeferredRegister<CreativeModeTab> REGISTRY =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ThEIds.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = REGISTRY.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.thaumicenergistics_ce"))
                    .icon(() -> com.beiwu.thaumicenergistics_ce.item.ItemFocusAEWrench.assembledStack())
                    .displayItems((parameters, output) -> {
                        output.accept(TECEItems.MYSTIC_CRYSTAL.get());
                        output.accept(TECEItems.MYSTIC_CRYSTAL_DUST.get());
                        output.accept(TECEItems.MYSTIC_CIRCUIT_BOARD.get());
                        output.accept(TECEItems.MYSTIC_PROCESSOR.get());
                        output.accept(TECEItems.THAUMIC_PROCESSOR_PRESS.get());
                        output.accept(TECEItems.THAUMIC_UPGRADE_TEMPLATE.get());
                        output.accept(TECEItems.THAUMIC_CRYSTAL_SWORD.get());
                        output.accept(TECEItems.THAUMIC_CRYSTAL_PICKAXE.get());
                        output.accept(TECEItems.THAUMIC_CRYSTAL_AXE.get());
                        output.accept(TECEItems.THAUMIC_CRYSTAL_SHOVEL.get());
                        output.accept(TECEItems.THAUMIC_CRYSTAL_HOE.get());
                        output.accept(TECEItems.THAUMIC_CRYSTAL_WRENCH.get());
                        output.accept(TECEItems.THAUMIC_CRYSTAL_KNIFE.get());
                        output.accept(TECEItems.FLUIX_BRAIN.get());
                        output.accept(TECEItems.MYSTIC_CRYSTAL_BLOCK.get());
                        output.accept(TECEItems.CHIPPED_BUDDING_THAUMIC_CRYSTAL.get());
                        output.accept(TECEItems.DAMAGED_BUDDING_THAUMIC_CRYSTAL.get());
                        output.accept(TECEItems.FLAWLESS_BUDDING_THAUMIC_CRYSTAL.get());
                        output.accept(TECEItems.FLAWED_BUDDING_THAUMIC_CRYSTAL.get());
                        output.accept(TECEItems.SMALL_MYSTIC_CRYSTAL_BUD.get());
                        output.accept(TECEItems.MEDIUM_MYSTIC_CRYSTAL_BUD.get());
                        output.accept(TECEItems.LARGE_MYSTIC_CRYSTAL_BUD.get());
                        output.accept(TECEItems.MYSTIC_CRYSTAL_CLUSTER.get());
                        output.accept(TECEItems.KNOWLEDGE_CORE.get());
                        output.accept(TECEItems.ARCANE_ASSEMBLER.get());
                        output.accept(TECEItems.KNOWLEDGE_INSCRIBER.get());
                        output.accept(TECEItems.ESSENTIA_CELL_WORKBENCH.get());
                        output.accept(TECEItems.ESSENTIA_VIBRATION_CHAMBER.get());
                        output.accept(TECEItems.ALCHEMY_PROVIDER.get());
                        output.accept(TECEItems.INFUSION_PROVIDER.get());
                        output.accept(TECEItems.DISTILLATION_ENCODER.get());
                        output.accept(TECEItems.OCCULT_MONITOR.get());
                        output.accept(TECEItems.ALCHEMY_PROVIDER_CONNECTION.get());
                        output.accept(TECEItems.WIRELESS_CONNECTOR.get());
                        output.accept(TECEItems.ALKUSURE86_FUMO.get());
                        output.accept(TECEItems.BEIWU_CN_FUMO.get());
                        output.accept(TECEItems.XIAO_LENG_FUMO.get());
                        output.accept(TECEItems.STORAGE_CASING.get());
                        output.accept(TECEItems.STORAGE_COMPONENT_1K.get());
                        output.accept(TECEItems.STORAGE_COMPONENT_4K.get());
                        output.accept(TECEItems.STORAGE_COMPONENT_16K.get());
                        output.accept(TECEItems.STORAGE_COMPONENT_64K.get());
                        output.accept(TECEItems.STORAGE_COMPONENT_256K.get());
                        output.accept(TECEItems.ESSENTIA_CELL_1K.get());
                        output.accept(TECEItems.ESSENTIA_CELL_4K.get());
                        output.accept(TECEItems.ESSENTIA_CELL_16K.get());
                        output.accept(TECEItems.ESSENTIA_CELL_64K.get());
                        output.accept(TECEItems.ESSENTIA_CELL_256K.get());
                        output.accept(TECEItems.ESSENTIA_CELL_CREATIVE.get());
                        output.accept(TECEItems.ESSENTIA_TERMINAL.get());
                        output.accept(TECEItems.ARCANE_CRAFTING_TERMINAL.get());
                        output.accept(TECEItems.VIS_INTERFACE.get());
                        output.accept(TECEItems.DIFFUSION_CORE.get());
                        output.accept(TECEItems.COALESCENCE_CORE.get());
                        output.accept(TECEItems.FLUX_TRANSFER_INTERFACE.get());
                        output.accept(TECEItems.ESSENTIA_LEVEL_EMITTER.get());
                        output.accept(TECEItems.WIRELESS_ESSENTIA_TERMINAL.get());
                        output.accept(charged(TECEItems.WIRELESS_ESSENTIA_TERMINAL.get()));
                        output.accept(TECEItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get());
                        output.accept(charged(TECEItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get()));
                        output.accept(TECEItems.ESSENTIA_ACCESS_CARD.get());
                        output.accept(TECEItems.VIS_CONNECTION_CARD.get());
                        output.accept(com.beiwu.thaumicenergistics_ce.item.ItemFocusAEWrench.assembledStack());
                        output.accept(TECEItems.GOLEM_WIFI_BACKPACK.get());
                        output.accept(TECEItems.GACHA_BOX.get());
                    })
                    .build());

    private static ItemStack charged(appeng.items.tools.powered.WirelessTerminalItem item) {
        var stack = new ItemStack(item);
        item.injectAEPower(stack, item.getAEMaxPower(stack), appeng.api.config.Actionable.MODULATE);
        return stack;
    }

    private TECECreativeTab() {}

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
