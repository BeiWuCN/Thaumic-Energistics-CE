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
 * <p>Phase 1 content is the Arcane Assembler: an AE2 crafting machine that performs Thaumaturge arcane
 * recipes on demand, taking them straight from the recipe manager.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(ThEIds.MODID);

    /**
     * Properties for the Arcane Assembler.
     *
     * <p>{@code noOcclusion()} is load-bearing, not cosmetic. The assembler renders an open frame whose
     * model has gaps, but the block does not override {@code getShape}, so its occlusion shape would
     * otherwise be the default full cube. {@code Block.shouldRenderFace} culls a face when
     * {@code level.getBlockState(pos).isShapeFullBlock(...)} is true, so without this the game believes
     * a hollow frame is a solid block and **deletes the neighbouring block's contact face** - place the
     * assembler against anything and that block's touching side loses its texture, showing the void
     * through it. {@code noOcclusion()} makes the occlusion test always report false, so neighbours keep
     * the faces that touch this block.
     *
     * <p>The upstream project uses the same property for this block, for the same reason.
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

    /**
     * Properties for the Knowledge Inscriber.
     *
     * <p>A full cube, so it keeps the default occlusion shape and correctly hides the faces of whatever
     * it is placed against.
     */
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

    /**
     * Properties for the Essentia Vibration Chamber.
     *
     * <p>A machine that burns essentia, so it is metal like the other machines and takes a pickaxe.
     */
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

    /**
     * Hands essentia from the network to whatever container it touches.
     *
     * <p>A full cube like the other machines, so it keeps the default occlusion shape.
     */
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
     * The far end of a wireless essentia link.
     *
     * <p>{@code noOcclusion()} for the same reason the Arcane Assembler needs it: the model is a plug with
     * gaps, so without it the game believes a hollow shape is a solid block and deletes the neighbouring
     * block's contact face.
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

    /**
     * A decorative figure.
     *
     * <p>{@code noOcclusion()} and a small shape: it is a prop sitting on the floor, so nothing about it
     * should behave like a solid block.
     */
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
