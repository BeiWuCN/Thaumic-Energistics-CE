package thaumicenergistics_ce.client.render;

import com.leclowndu93150.thaumaturge.client.model.entity.BrainModel;
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
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.gachabox.BlockEntityGachaBox;

public class GachaBoxRenderer implements BlockEntityRenderer<BlockEntityGachaBox, GachaBoxRenderState> {

    public static final ModelLayerLocation BRAIN_LAYER =
            new ModelLayerLocation(ThEIds.id("gacha_box_brain"), "main");

    private static final Identifier BRAIN_TEXTURE =
            Identifier.fromNamespaceAndPath("thaumaturge", "textures/entity/brain2.png");

    private static final float BRAIN_SCALE = 0.5F;

    private static final float BRAIN_LIFT = -0.8125F;

    private static final float BRAIN_DEGREES_PER_TICK = 0.25F;

    private static final float BRAIN_TURN_TICKS = 1440.0F;

    private final BrainModel brain;

    public GachaBoxRenderer(BlockEntityRendererProvider.Context context) {
        this.brain = new BrainModel(context.bakeLayer(BRAIN_LAYER));
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
        long gameTime = box.getLevel() == null ? 0L : box.getLevel().getGameTime();
        state.turn = (gameTime % (long) BRAIN_TURN_TICKS + partialTick) * BRAIN_DEGREES_PER_TICK;
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
        poseStack.translate(0.5F, 0.01F, 0.5F);
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        poseStack.pushPose();
        poseStack.translate(0.0F, BRAIN_LIFT, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.turn));
        poseStack.mulPose(Axis.YN.rotationDegrees(90.0F));
        poseStack.scale(BRAIN_SCALE, BRAIN_SCALE, BRAIN_SCALE);
        submitNodeCollector.submitModelPart(
                brain.root(),
                poseStack,
                RenderTypes.entityCutout(BRAIN_TEXTURE),
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                null,
                -1,
                null);
        poseStack.popPose();
        poseStack.popPose();
    }
}
