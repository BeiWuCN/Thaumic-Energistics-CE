package com.beiwu.thaumicenergistics_ce.client.render;

import com.leclowndu93150.thaumaturge.client.model.entity.BrainModel;
import com.leclowndu93150.thaumaturge.client.model.entity.JarBrineModel;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.blockentity.gachabox.BlockEntityGachaBox;

/** 绘制缸中之脑：模型的 Y 轴朝下，靠姿态链把它立起来。 */
public final class GachaBoxRenderer implements BlockEntityRenderer<BlockEntityGachaBox> {

    // 自有的层定义，本 mod 自己注册，不走 Thaumaturge。
    public static final ModelLayerLocation BRAIN_LAYER =
            new ModelLayerLocation(ThEIds.id("gacha_box_brain"), "main");

    private static final ResourceLocation BRAIN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("thaumaturge", "textures/entity/brain2.png");

    private static final float BRAIN_SCALE = 0.42F;
    private static final float BRAIN_CENTER_Y = 13.0F / 16.0F;
    private static final ResourceLocation BRINE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "textures/block/white_concrete.png");

    private static final float BRAIN_DEGREES_PER_TICK = 0.25F;

    private static final float BRAIN_TURN_TICKS = 1440.0F;

    private final BrainModel brain;
    private final FluixBrainModel fluixBrain;
    private final JarBrineModel brine;

    public GachaBoxRenderer(BlockEntityRendererProvider.Context context) {
        this.brain = new BrainModel(context.bakeLayer(BRAIN_LAYER));
        this.fluixBrain = new FluixBrainModel();
        this.brine = new JarBrineModel(JarBrineModel.createLayer().bakeRoot());
    }

    @Override
    public void render(BlockEntityGachaBox box, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        if (!box.hasJar()) return;
        long time = box.getLevel() == null ? 0 : box.getLevel().getGameTime();
        float bob = (float)Math.sin((time % 10000 + partialTick) / 20.0) * 0.0125F;
        pose.pushPose(); pose.translate(0.5F, BRAIN_CENTER_Y + bob, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees((time % 1440 + partialTick) * BRAIN_DEGREES_PER_TICK));
        if (box.hasFluixBrain()) fluixBrain.render(pose, buffers, light);
        else {
            pose.mulPose(Axis.ZP.rotationDegrees(180)); pose.scale(BRAIN_SCALE, BRAIN_SCALE, BRAIN_SCALE);
            pose.translate(0, -15.0F / 16.0F, 0);
            brain.root.render(pose, buffers.getBuffer(RenderType.entityCutout(BRAIN_TEXTURE)), light, overlay);
        }
        pose.popPose(); pose.pushPose(); pose.translate(0.5F, 0.49F, 0.5F);
        pose.mulPose(Axis.ZP.rotationDegrees(180)); pose.scale(1.175F, 0.79F, 1.175F);
        int color = box.getLevel() == null ? 0xFFC2BF : box.getLevel().registryAccess()
                .lookupOrThrow(IAspect.REGISTRY_KEY).getOrThrow(TTAspects.COGNITIO).value().color();
        brine.root.render(pose, buffers.getBuffer(RenderType.entityTranslucent(BRINE_TEXTURE)), light, overlay, 0x60000000 | (color & 0xFFFFFF));
        pose.popPose();
    }
}
