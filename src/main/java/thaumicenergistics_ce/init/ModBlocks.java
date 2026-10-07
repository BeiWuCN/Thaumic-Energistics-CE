package thaumicenergistics_ce.init;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.block.BlockArcaneAssembler;
import thaumicenergistics_ce.block.BlockDecorativeFigure;
import thaumicenergistics_ce.block.BlockDistillationEncoder;
import thaumicenergistics_ce.block.BlockEssentiaCellWorkbench;
import thaumicenergistics_ce.block.BlockAlchemyProvider;
import thaumicenergistics_ce.block.BlockAlchemyProviderConnection;
import thaumicenergistics_ce.block.BlockEssentiaVibrationChamber;
import thaumicenergistics_ce.block.BlockOccultMonitor;
import thaumicenergistics_ce.block.BlockInfusionProvider;
import thaumicenergistics_ce.block.BlockGachaBox;
import thaumicenergistics_ce.block.BlockGachaBoxAggregator;
import thaumicenergistics_ce.block.BlockKnowledgeInscriber;

/**
 * 奥术组装机是一台 AE2 合成机器，按需执行 Thaumaturge 的奥术配方。
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(ThEIds.MODID);

    /**
     * {@code noOcclusion()} 是功能所需，不是外观修饰。
     * 带缝隙的开放式框架沿用默认的整方块遮挡形状，接触面会被剔除。
     */
    private static final BlockBehaviour.Properties ASSEMBLER_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.5F, 8.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
            .noOcclusion();

    public static final DeferredBlock<BlockArcaneAssembler> ARCANE_ASSEMBLER = REGISTRY.register(
            "arcane_assembler",
            () -> new BlockArcaneAssembler(ASSEMBLER_PROPERTIES));

    private static final BlockBehaviour.Properties INSCRIBER_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(2.5F, 6.0F)
            .sound(SoundType.WOOD)
            .requiresCorrectToolForDrops();

    public static final DeferredBlock<BlockKnowledgeInscriber> KNOWLEDGE_INSCRIBER = REGISTRY.register(
            "knowledge_inscriber",
            () -> new BlockKnowledgeInscriber(INSCRIBER_PROPERTIES));

    public static final DeferredBlock<BlockEssentiaCellWorkbench> ESSENTIA_CELL_WORKBENCH = REGISTRY.register(
            "essentia_cell_workbench",
            () -> new BlockEssentiaCellWorkbench(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    private static final BlockBehaviour.Properties VIBRATION_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F, 7.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops();

    public static final DeferredBlock<BlockEssentiaVibrationChamber> ESSENTIA_VIBRATION_CHAMBER =
            REGISTRY.register(
                    "essentia_vibration_chamber",
                    () -> new BlockEssentiaVibrationChamber(VIBRATION_PROPERTIES));

    public static final DeferredBlock<BlockAlchemyProvider> ALCHEMY_PROVIDER = REGISTRY.register(
            "alchemy_provider",
            () -> new BlockAlchemyProvider(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 7.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<BlockInfusionProvider> INFUSION_PROVIDER = REGISTRY.register(
            "infusion_provider",
            () -> new BlockInfusionProvider(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 7.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<BlockDistillationEncoder> DISTILLATION_ENCODER = REGISTRY.register(
            "distillation_encoder",
            () -> new BlockDistillationEncoder(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 7.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<BlockOccultMonitor> OCCULT_MONITOR = REGISTRY.register(
            "occult_monitor",
            () -> new BlockOccultMonitor(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 7.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    /**
     * 无线源质链路的另一端。与奥术组装机一样用 {@code noOcclusion()}。
     * 插头模型有缝隙，用默认遮挡形状会让它后方接触的面被剔除。
     */
    public static final DeferredBlock<BlockAlchemyProviderConnection> ALCHEMY_PROVIDER_CONNECTION =
            REGISTRY.register(
                    "alchemy_provider_connection",
                    () -> new BlockAlchemyProviderConnection(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0F, 6.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()));

    public static final DeferredBlock<BlockDecorativeFigure> ALKUSURE86_FUMO = REGISTRY.register(
            "alkusure86fumo",
            () -> new BlockDecorativeFigure(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOL)
                    .strength(0.5F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()));

    /**
     * Gacha Box 的两个方块：主体（方块状态携带罐子）与上半部分。
     * 两个都不是完整立方体，与奥术组装机一样要 {@code noOcclusion()}。
     */
    private static final BlockBehaviour.Properties GACHA_BOX_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F, 7.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
            .noOcclusion();

    public static final DeferredBlock<BlockGachaBox> GACHA_BOX = REGISTRY.register(
            "gacha_box",
            () -> new BlockGachaBox(GACHA_BOX_PROPERTIES));

    public static final DeferredBlock<BlockGachaBoxAggregator> GACHA_BOX_AGGREGATOR = REGISTRY.register(
            "gacha_box_aggregator",
            () -> new BlockGachaBoxAggregator(GACHA_BOX_PROPERTIES));

    private ModBlocks() {}

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
