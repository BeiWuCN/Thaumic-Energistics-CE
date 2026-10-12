package thaumicenergistics_ce.registry;

import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.BlockEntityDistillationEncoder;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProvider;
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProviderConnection;
import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor;
import thaumicenergistics_ce.blockentity.BlockEntityInfusionProvider;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.blockentity.gachabox.BlockEntityGachaBox;

public final class TECEBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTRY =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ThEIds.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityArcaneAssembler>>
            ARCANE_ASSEMBLER = REGISTRY.register(
                    "arcane_assembler",
                    () -> new BlockEntityType<>(
                            BlockEntityArcaneAssembler::new, Set.of(TECEBlocks.ARCANE_ASSEMBLER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityKnowledgeInscriber>>
            KNOWLEDGE_INSCRIBER = REGISTRY.register(
                    "knowledge_inscriber",
                    () -> new BlockEntityType<>(
                            BlockEntityKnowledgeInscriber::new,
                            Set.of(TECEBlocks.KNOWLEDGE_INSCRIBER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityEssentiaCellWorkbench>>
            ESSENTIA_CELL_WORKBENCH = REGISTRY.register(
                    "essentia_cell_workbench",
                    () -> new BlockEntityType<>(
                            BlockEntityEssentiaCellWorkbench::new,
                            Set.of(TECEBlocks.ESSENTIA_CELL_WORKBENCH.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityEssentiaVibrationChamber>>
            ESSENTIA_VIBRATION_CHAMBER = REGISTRY.register(
                    "essentia_vibration_chamber",
                    () -> new BlockEntityType<>(
                            BlockEntityEssentiaVibrationChamber::new,
                            Set.of(TECEBlocks.ESSENTIA_VIBRATION_CHAMBER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityAlchemyProvider>>
            ALCHEMY_PROVIDER = REGISTRY.register(
                    "alchemy_provider",
                    () -> new BlockEntityType<>(
                            BlockEntityAlchemyProvider::new,
                            Set.of(TECEBlocks.ALCHEMY_PROVIDER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityInfusionProvider>>
            INFUSION_PROVIDER = REGISTRY.register(
                    "infusion_provider",
                    () -> new BlockEntityType<>(
                            BlockEntityInfusionProvider::new,
                            Set.of(TECEBlocks.INFUSION_PROVIDER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityDistillationEncoder>>
            DISTILLATION_ENCODER = REGISTRY.register(
                    "distillation_encoder",
                    () -> new BlockEntityType<>(
                            BlockEntityDistillationEncoder::new,
                            Set.of(TECEBlocks.DISTILLATION_ENCODER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityOccultMonitor>>
            OCCULT_MONITOR = REGISTRY.register(
                    "occult_monitor",
                    () -> new BlockEntityType<>(
                            BlockEntityOccultMonitor::new,
                            Set.of(TECEBlocks.OCCULT_MONITOR.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityAlchemyProviderConnection>>
            ALCHEMY_PROVIDER_CONNECTION = REGISTRY.register(
                    "alchemy_provider_connection",
                    () -> new BlockEntityType<>(
                            BlockEntityAlchemyProviderConnection::new,
                            Set.of(TECEBlocks.ALCHEMY_PROVIDER_CONNECTION.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityGachaBox>>
            GACHA_BOX = REGISTRY.register(
                    "gacha_box",
                    () -> new BlockEntityType<>(
                            BlockEntityGachaBox::new, Set.of(TECEBlocks.GACHA_BOX.get())));

    private TECEBlockEntities() {}

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
