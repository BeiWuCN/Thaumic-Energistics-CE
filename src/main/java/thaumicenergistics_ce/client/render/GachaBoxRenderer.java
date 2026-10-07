package thaumicenergistics_ce.client.render;

import com.leclowndu93150.thaumaturge.client.model.entity.BrainModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.gachabox.BlockEntityGachaBox;

/** 绘制缸中之脑：模型的 Y 轴朝下，靠姿态链把它立起来。 */
public final class GachaBoxRenderer implements BlockEntityRenderer<BlockEntityGachaBox> {

    // 自有的层定义，本 mod 自己注册，不走 Thaumaturge。
    public static final ModelLayerLocation BRAIN_LAYER =
            new ModelLayerLocation(ThEIds.id("gacha_box_brain"), "main");

    private static final ResourceLocation BRAIN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("thaumaturge", "textures/entity/brain2.png");

    // 抬升量与缩放经过求解，刚好填满罐子空腔。
    private static final float BRAIN_SCALE = 0.5F;

    private static final float BRAIN_LIFT = -0.8125F;

    private static final float BRAIN_DEGREES_PER_TICK = 0.25F;

    private static final float BRAIN_TURN_TICKS = 1440.0F;

    private final BrainModel brain;

    public GachaBoxRenderer(BlockEntityRendererProvider.Context context) {
        this.brain = new BrainModel(context.bakeLayer(BRAIN_LAYER));
    }

    @Override
    public void render(
            BlockEntityGachaBox box,
            float partialTick,
            PoseStack poses,
            MultiBufferSource buffers,
            int light,
            int overlay) {
        if (!box.hasJar()) {
            return;
        }
        Level level = box.getLevel();
        long gameTime = level == null ? 0L : level.getGameTime();
        float turn = (gameTime % (long) BRAIN_TURN_TICKS + partialTick) * BRAIN_DEGREES_PER_TICK;

        poses.pushPose();
        poses.translate(0.5F, 0.0F, 0.5F);
        poses.mulPose(Axis.XP.rotationDegrees(180.0F));
        poses.translate(0.0F, BRAIN_LIFT, 0.0F);
        poses.mulPose(Axis.YP.rotationDegrees(turn));
        poses.mulPose(Axis.YN.rotationDegrees(90.0F));
        poses.scale(BRAIN_SCALE, BRAIN_SCALE, BRAIN_SCALE);
        this.brain.root.render(
                poses, buffers.getBuffer(RenderType.entityCutout(BRAIN_TEXTURE)), light, overlay);
        poses.popPose();
    }
}
