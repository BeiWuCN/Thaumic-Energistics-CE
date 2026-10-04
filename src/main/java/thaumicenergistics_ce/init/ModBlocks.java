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
import thaumicenergistics_ce.block.BlockEssentiaProvider;
import thaumicenergistics_ce.block.BlockEssentiaProviderConnection;
import thaumicenergistics_ce.block.BlockEssentiaVibrationChamber;
import thaumicenergistics_ce.block.BlockInfusionMonitor;
import thaumicenergistics_ce.block.BlockInfusionProvider;
import thaumicenergistics_ce.block.BlockKnowledgeInscriber;

/**
 * Block registration.
 *
 * <ul>
 *   <li>The Arcane Assembler is an AE2 crafting machine that performs Thaumaturge arcane recipes on demand.
 * </ul>
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(ThEIds.MODID);

    /**
     * Properties for the Arcane Assembler. {@code noOcclusion()} is load-bearing, not cosmetic: an open
     * frame with gaps over the default full-cube occlusion shape would have its contact face culled.
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

    /** Properties for the Knowledge Inscriber: a full cube, so it keeps the default occlusion shape. */
    private static final BlockBehaviour.Properties INSCRIBER_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(2.5F, 6.0F)
            .sound(SoundType.WOOD)
            .requiresCorrectToolForDrops();

    public static final DeferredBlock<BlockKnowledgeInscriber> KNOWLEDGE_INSCRIBER = REGISTRY.register(
            "knowledge_inscriber",
            () -> new BlockKnowledgeInscriber(INSCRIBER_PROPERTIES));

    /** Where a storage cell is told which aspects it may hold. */
    public static final DeferredBlock<BlockEssentiaCellWorkbench> ESSENTIA_CELL_WORKBENCH = REGISTRY.register(
            "essentia_cell_workbench",
            () -> new BlockEssentiaCellWorkbench(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    /** Properties for the Essentia Vibration Chamber: metal and a pickaxe, the same as the other machines. */
    private static final BlockBehaviour.Properties VIBRATION_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F, 7.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops();

    /** Burns essentia into AE. */
    public static final DeferredBlock<BlockEssentiaVibrationChamber> ESSENTIA_VIBRATION_CHAMBER =
            REGISTRY.register(
                    "essentia_vibration_chamber",
                    () -> new BlockEssentiaVibrationChamber(VIBRATION_PROPERTIES));

    /** Hands essentia from the network to whatever container it touches. A full cube, like the machines. */
    public static final DeferredBlock<BlockEssentiaProvider> ESSENTIA_PROVIDER = REGISTRY.register(
            "essentia_provider",
            () -> new BlockEssentiaProvider(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 7.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    /** Lets an Infusion Altar draw its essentia from the network instead of from jars. */
    public static final DeferredBlock<BlockInfusionProvider> INFUSION_PROVIDER = REGISTRY.register(
            "infusion_provider",
            () -> new BlockInfusionProvider(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 7.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    /** Writes "this item distils into that essentia" as an ME processing pattern. */
    public static final DeferredBlock<BlockDistillationEncoder> DISTILLATION_ENCODER = REGISTRY.register(
            "distillation_encoder",
            () -> new BlockDistillationEncoder(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 7.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    /** Watches an Infusion Altar and reports what the ritual is about to do. */
    public static final DeferredBlock<BlockInfusionMonitor> INFUSION_MONITOR = REGISTRY.register(
            "infusion_monitor",
            () -> new BlockInfusionMonitor(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 7.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    /**
     * The far end of a wireless essentia link. {@code noOcclusion()} as on the Arcane Assembler: the plug
     * model has gaps, so the touching face of the block behind it would be culled.
     */
    public static final DeferredBlock<BlockEssentiaProviderConnection> ESSENTIA_PROVIDER_CONNECTION =
            REGISTRY.register(
                    "essentia_provider_connection",
                    () -> new BlockEssentiaProviderConnection(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0F, 6.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()));

    /** A decorative figure: {@code noOcclusion()} and a small shape, so nothing about it is a solid block. */
    public static final DeferredBlock<BlockDecorativeFigure> ALKUSURE86_FUMO = REGISTRY.register(
            "alkusure86fumo",
            () -> new BlockDecorativeFigure(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOL)
                    .strength(0.5F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()));

    private ModBlocks() {}

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
