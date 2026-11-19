package com.beiwu.thaumicenergistics_ce.registry;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import com.beiwu.thaumicenergistics_ce.block.BlockMysticCrystalMatrix;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.block.BlockArcaneAssembler;
import com.beiwu.thaumicenergistics_ce.block.BlockBeiWuCnFumo;
import com.beiwu.thaumicenergistics_ce.block.BlockDecorativeFigure;
import com.beiwu.thaumicenergistics_ce.block.BlockDistillationEncoder;
import com.beiwu.thaumicenergistics_ce.block.BlockEssentiaCellWorkbench;
import com.beiwu.thaumicenergistics_ce.block.BlockAlchemyProvider;
import com.beiwu.thaumicenergistics_ce.block.BlockAlchemyProviderConnection;
import com.beiwu.thaumicenergistics_ce.block.BlockEssentiaVibrationChamber;
import com.beiwu.thaumicenergistics_ce.block.BlockOccultMonitor;
import com.beiwu.thaumicenergistics_ce.block.BlockInfusionProvider;
import com.beiwu.thaumicenergistics_ce.block.BlockGachaBox;
import com.beiwu.thaumicenergistics_ce.block.BlockGachaBoxAggregator;
import com.beiwu.thaumicenergistics_ce.block.BlockKnowledgeInscriber;

/**
 * 奥术组装机是一台 AE2 合成机器，按需执行 Thaumaturge 的奥术配方。
 */
public final class TECEBlocks {
    public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(ThEIds.MODID);

    public static final DeferredBlock<AmethystBlock> MYSTIC_CRYSTAL_BLOCK =
            REGISTRY.registerBlock("mystic_crystal_block", AmethystBlock::new,
                    crystalProperties().strength(1.5F));
    public static final DeferredBlock<BlockMysticCrystalMatrix>
            FLAWLESS_BUDDING_THAUMIC_CRYSTAL = matrix("flawless_budding_thaumic_crystal"),
            FLAWED_BUDDING_THAUMIC_CRYSTAL = matrix("flawed_budding_thaumic_crystal"),
            CHIPPED_BUDDING_THAUMIC_CRYSTAL = matrix("chipped_budding_thaumic_crystal"),
            DAMAGED_BUDDING_THAUMIC_CRYSTAL = matrix("damaged_budding_thaumic_crystal");
    public static final DeferredBlock<AmethystClusterBlock>
            SMALL_MYSTIC_CRYSTAL_BUD = bud("small_mystic_crystal_bud", 3.0F, 4.0F),
            MEDIUM_MYSTIC_CRYSTAL_BUD = bud("medium_mystic_crystal_bud", 4.0F, 3.0F),
            LARGE_MYSTIC_CRYSTAL_BUD = bud("large_mystic_crystal_bud", 5.0F, 3.0F),
            MYSTIC_CRYSTAL_CLUSTER = bud("mystic_crystal_cluster", 7.0F, 3.0F);

    private static BlockBehaviour.Properties crystalProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                .strength(1.5F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops();
    }

    private static DeferredBlock<BlockMysticCrystalMatrix> matrix(String id) {
        return REGISTRY.registerBlock(id, BlockMysticCrystalMatrix::new,
                crystalProperties().strength(3.0F).randomTicks());
    }

    private static DeferredBlock<AmethystClusterBlock> bud(
            String id, float height, float width) {
        return REGISTRY.registerBlock(id,
                // In 1.21.1 the second argument is the inset from each edge, as in AE2.
                properties -> new AmethystClusterBlock(height, width, properties),
                crystalProperties().noOcclusion().lightLevel(state -> 3));
    }

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

    public static final DeferredBlock<BlockDecorativeFigure> XIAO_LENG_FUMO = REGISTRY.register(
            "xiao_leng_fumo",
            () -> new BlockDecorativeFigure(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOL)
                    .strength(0.5F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()));

    /** 第二个玩偶，模型只有坐姿。属性与上一个一致，各自一个 lambda：注册会把 id 盖到属性对象上。 */
    public static final DeferredBlock<BlockBeiWuCnFumo> BEIWU_CN_FUMO = REGISTRY.register(
            "beiwu_cn_fumo",
            () -> new BlockBeiWuCnFumo(BlockBehaviour.Properties.of()
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

    public static final DeferredBlock<com.beiwu.thaumicenergistics_ce.block.BlockGachaBoxSpace> GACHA_BOX_SPACE = REGISTRY.registerBlock(
            "gacha_box_space", com.beiwu.thaumicenergistics_ce.block.BlockGachaBoxSpace::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 8.0F)
                    .sound(SoundType.METAL).noOcclusion().noLootTable()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));

    public static final DeferredBlock<BlockGachaBoxAggregator> GACHA_BOX_AGGREGATOR = REGISTRY.register(
            "gacha_box_aggregator",
            () -> new BlockGachaBoxAggregator(GACHA_BOX_PROPERTIES));

    private TECEBlocks() {}

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
