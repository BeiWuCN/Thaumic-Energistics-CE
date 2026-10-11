package thaumicenergistics_ce.init;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.block.BlockArcaneAssembler;
import thaumicenergistics_ce.block.BlockBeiWuCnFumo;
import thaumicenergistics_ce.block.BlockDecorativeFigure;
import thaumicenergistics_ce.block.BlockDistillationEncoder;
import thaumicenergistics_ce.block.BlockEssentiaCellWorkbench;
import thaumicenergistics_ce.block.BlockGachaBox;
import thaumicenergistics_ce.block.BlockGachaBoxAggregator;
import thaumicenergistics_ce.block.BlockAlchemyProvider;
import thaumicenergistics_ce.block.BlockAlchemyProviderConnection;
import thaumicenergistics_ce.block.BlockEssentiaVibrationChamber;
import thaumicenergistics_ce.block.BlockOccultMonitor;
import thaumicenergistics_ce.block.BlockInfusionProvider;
import thaumicenergistics_ce.block.BlockKnowledgeInscriber;

public final class ModBlocks {
    public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(ThEIds.MODID);

    private static final BlockBehaviour.Properties ASSEMBLER_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.5F, 8.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
            .noOcclusion();

    public static final DeferredBlock<BlockArcaneAssembler> ARCANE_ASSEMBLER = REGISTRY.registerBlock(
            "arcane_assembler",
            BlockArcaneAssembler::new,
            () -> ASSEMBLER_PROPERTIES);

    private static final BlockBehaviour.Properties INSCRIBER_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(2.5F, 6.0F)
            .sound(SoundType.WOOD)
            .requiresCorrectToolForDrops();

    public static final DeferredBlock<BlockKnowledgeInscriber> KNOWLEDGE_INSCRIBER = REGISTRY.registerBlock(
            "knowledge_inscriber",
            BlockKnowledgeInscriber::new,
            () -> INSCRIBER_PROPERTIES);

    public static final DeferredBlock<BlockEssentiaCellWorkbench> ESSENTIA_CELL_WORKBENCH = REGISTRY.registerBlock(
            "essentia_cell_workbench",
            BlockEssentiaCellWorkbench::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    private static final BlockBehaviour.Properties VIBRATION_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F, 7.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops();

    public static final DeferredBlock<BlockEssentiaVibrationChamber> ESSENTIA_VIBRATION_CHAMBER =
            REGISTRY.registerBlock(
                    "essentia_vibration_chamber",
                    BlockEssentiaVibrationChamber::new,
                    () -> VIBRATION_PROPERTIES);

    public static final DeferredBlock<BlockAlchemyProvider> ALCHEMY_PROVIDER = REGISTRY.registerBlock(
            "alchemy_provider",
            BlockAlchemyProvider::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 7.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<BlockInfusionProvider> INFUSION_PROVIDER = REGISTRY.registerBlock(
            "infusion_provider",
            BlockInfusionProvider::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 7.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<BlockDistillationEncoder> DISTILLATION_ENCODER = REGISTRY.registerBlock(
            "distillation_encoder",
            BlockDistillationEncoder::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 7.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<BlockOccultMonitor> OCCULT_MONITOR = REGISTRY.registerBlock(
            "occult_monitor",
            BlockOccultMonitor::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 7.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion());

    public static final DeferredBlock<BlockAlchemyProviderConnection> ALCHEMY_PROVIDER_CONNECTION =
            REGISTRY.registerBlock(
                    "alchemy_provider_connection",
                    BlockAlchemyProviderConnection::new,
                    properties -> properties
                            .mapColor(MapColor.METAL)
                            .strength(2.0F, 6.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .noOcclusion());

    public static final DeferredBlock<BlockDecorativeFigure> ALKUSURE86_FUMO = REGISTRY.registerBlock(
            "alkusure86fumo",
            BlockDecorativeFigure::new,
            properties -> properties
                    .mapColor(MapColor.WOOL)
                    .strength(0.5F)
                    .sound(SoundType.WOOL)
                    .noOcclusion());

    public static final DeferredBlock<BlockBeiWuCnFumo> BEIWU_CN_FUMO = REGISTRY.registerBlock(
            "beiwu_cn_fumo",
            BlockBeiWuCnFumo::new,
            properties -> properties
                    .mapColor(MapColor.WOOL)
                    .strength(0.5F)
                    .sound(SoundType.WOOL)
                    .noOcclusion());

    public static final DeferredBlock<BlockGachaBox> GACHA_BOX = REGISTRY.registerBlock(
            "gacha_box",
            BlockGachaBox::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 8.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion());

    public static final DeferredBlock<BlockGachaBoxAggregator> GACHA_BOX_AGGREGATOR = REGISTRY.registerBlock(
            "gacha_box_aggregator",
            BlockGachaBoxAggregator::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    private ModBlocks() {}

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
