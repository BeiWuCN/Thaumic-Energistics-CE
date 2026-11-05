package com.beiwu.thaumicenergistics_ce.client.render;

import com.leclowndu93150.thaumaturge.client.model.entity.BrainModel;
import com.leclowndu93150.thaumaturge.client.model.entity.JarBrineModel;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.blockentity.gachabox.BlockEntityGachaBox;

public class GachaBoxRenderer implements BlockEntityRenderer<BlockEntityGachaBox, GachaBoxRenderState> {

    public static final ModelLayerLocation BRAIN_LAYER =
            new ModelLayerLocation(ThEIds.id("gacha_box_brain"), "main");

    private static final Identifier BRAIN_TEXTURE =
            Identifier.fromNamespaceAndPath("thaumaturge", "textures/entity/brain2.png");

    private static final float BRAIN_SCALE = 0.42F;
    private static final float BRAIN_CENTER_Y = 13.0F / 16.0F;
    private static final Identifier BRINE_TEXTURE = Identifier.fromNamespaceAndPath(
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
    public GachaBoxRenderState createRenderState() {
        return new GachaBoxRenderState();
    }

    @Override
    public void extractRenderState(
            BlockEntityGachaBox box,
            GachaBoxRenderState state,
            float partialTick,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(box, state, partialTick, cameraPosition, breakProgress);

        state.hasJar = box.hasJar();
        state.fluixBrain = box.hasFluixBrain();
        long gameTime = box.getLevel() == null ? 0L : box.getLevel().getGameTime();
        state.turn = (gameTime % (long) BRAIN_TURN_TICKS + partialTick) * BRAIN_DEGREES_PER_TICK;
        state.bob = (float) Math.sin((gameTime % 10000 + partialTick) / 20.0) * 0.0125F;
        int color = box.getLevel() == null ? 0xFFC2BF : box.getLevel().registryAccess()
                .lookupOrThrow(IAspect.REGISTRY_KEY).getOrThrow(TTAspects.COGNITIO).value().color();
        state.liquidColor = 0x60000000 | (color & 0xFFFFFF);
    }

    @Override
    public void submit(
            GachaBoxRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera) {
        if (!state.hasJar) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, BRAIN_CENTER_Y + state.bob, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.turn));
        if (state.fluixBrain) {
            fluixBrain.submit(poseStack, submitNodeCollector, state.lightCoords);
        } else {
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            poseStack.scale(BRAIN_SCALE, BRAIN_SCALE, BRAIN_SCALE);
            // The native model's brain pivot is 15 model pixels below its root.
            poseStack.translate(0.0F, -15.0F / 16.0F, 0.0F);
            submitNodeCollector.submitModelPart(
                    brain.root(),
                    poseStack,
                    RenderTypes.entityCutout(BRAIN_TEXTURE),
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY,
                    null,
                    -1,
                    null);
        }
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.49F, 0.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.scale(1.175F, 0.79F, 1.175F);
        submitNodeCollector.submitModelPart(brine.root, poseStack,
                RenderTypes.entityTranslucent(BRINE_TEXTURE), state.lightCoords,
                OverlayTexture.NO_OVERLAY, null, state.liquidColor, null);
        poseStack.popPose();
    }
}
