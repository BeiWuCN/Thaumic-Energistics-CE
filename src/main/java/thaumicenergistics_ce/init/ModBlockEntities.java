package thaumicenergistics_ce.init;

import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.BlockEntityDistillationEncoder;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;
import thaumicenergistics_ce.blockentity.essentiaprovider.BlockEntityEssentiaProvider;
import thaumicenergistics_ce.blockentity.essentiaprovider.BlockEntityEssentiaProviderConnection;
import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber;
import thaumicenergistics_ce.blockentity.infusionmonitor.BlockEntityInfusionMonitor;
import thaumicenergistics_ce.blockentity.BlockEntityInfusionProvider;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;

/** Block entity type registration. */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTRY =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ThEIds.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityArcaneAssembler>>
            ARCANE_ASSEMBLER = REGISTRY.register(
                    "arcane_assembler",
                    () -> new BlockEntityType<>(
                            BlockEntityArcaneAssembler::new, Set.of(ModBlocks.ARCANE_ASSEMBLER.get()), null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityKnowledgeInscriber>>
            KNOWLEDGE_INSCRIBER = REGISTRY.register(
                    "knowledge_inscriber",
                    () -> new BlockEntityType<>(
                            BlockEntityKnowledgeInscriber::new,
                            Set.of(ModBlocks.KNOWLEDGE_INSCRIBER.get()),
                            null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityEssentiaCellWorkbench>>
            ESSENTIA_CELL_WORKBENCH = REGISTRY.register(
                    "essentia_cell_workbench",
                    () -> new BlockEntityType<>(
                            BlockEntityEssentiaCellWorkbench::new,
                            Set.of(ModBlocks.ESSENTIA_CELL_WORKBENCH.get()),
                            null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityEssentiaVibrationChamber>>
            ESSENTIA_VIBRATION_CHAMBER = REGISTRY.register(
                    "essentia_vibration_chamber",
                    () -> new BlockEntityType<>(
                            BlockEntityEssentiaVibrationChamber::new,
                            Set.of(ModBlocks.ESSENTIA_VIBRATION_CHAMBER.get()),
                            null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityEssentiaProvider>>
            ESSENTIA_PROVIDER = REGISTRY.register(
                    "essentia_provider",
                    () -> new BlockEntityType<>(
                            BlockEntityEssentiaProvider::new,
                            Set.of(ModBlocks.ESSENTIA_PROVIDER.get()),
                            null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityInfusionProvider>>
            INFUSION_PROVIDER = REGISTRY.register(
                    "infusion_provider",
                    () -> new BlockEntityType<>(
                            BlockEntityInfusionProvider::new,
                            Set.of(ModBlocks.INFUSION_PROVIDER.get()),
                            null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityDistillationEncoder>>
            DISTILLATION_ENCODER = REGISTRY.register(
                    "distillation_encoder",
                    () -> new BlockEntityType<>(
                            BlockEntityDistillationEncoder::new,
                            Set.of(ModBlocks.DISTILLATION_ENCODER.get()),
                            null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityInfusionMonitor>>
            INFUSION_MONITOR = REGISTRY.register(
                    "infusion_monitor",
                    () -> new BlockEntityType<>(
                            BlockEntityInfusionMonitor::new,
                            Set.of(ModBlocks.INFUSION_MONITOR.get()),
                            null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityEssentiaProviderConnection>>
            ESSENTIA_PROVIDER_CONNECTION = REGISTRY.register(
                    "essentia_provider_connection",
                    () -> new BlockEntityType<>(
                            BlockEntityEssentiaProviderConnection::new,
                            Set.of(ModBlocks.ESSENTIA_PROVIDER_CONNECTION.get()),
                            null));

    private ModBlockEntities() {}

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
