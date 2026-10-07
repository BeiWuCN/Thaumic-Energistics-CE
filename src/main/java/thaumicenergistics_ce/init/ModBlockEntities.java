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
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProvider;
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProviderConnection;
import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor;
import thaumicenergistics_ce.blockentity.BlockEntityInfusionProvider;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.blockentity.gachabox.BlockEntityGachaBox;

/** 方块实体类型注册。 */
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityAlchemyProvider>>
            ALCHEMY_PROVIDER = REGISTRY.register(
                    "alchemy_provider",
                    () -> new BlockEntityType<>(
                            BlockEntityAlchemyProvider::new,
                            Set.of(ModBlocks.ALCHEMY_PROVIDER.get()),
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityOccultMonitor>>
            OCCULT_MONITOR = REGISTRY.register(
                    "occult_monitor",
                    () -> new BlockEntityType<>(
                            BlockEntityOccultMonitor::new,
                            Set.of(ModBlocks.OCCULT_MONITOR.get()),
                            null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityAlchemyProviderConnection>>
            ALCHEMY_PROVIDER_CONNECTION = REGISTRY.register(
                    "alchemy_provider_connection",
                    () -> new BlockEntityType<>(
                            BlockEntityAlchemyProviderConnection::new,
                            Set.of(ModBlocks.ALCHEMY_PROVIDER_CONNECTION.get()),
                            null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntityGachaBox>>
            GACHA_BOX = REGISTRY.register(
                    "gacha_box",
                    () -> new BlockEntityType<>(
                            BlockEntityGachaBox::new,
                            Set.of(ModBlocks.GACHA_BOX.get()),
                            null));

    private ModBlockEntities() {}

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
