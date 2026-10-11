package thaumicenergistics_ce.client.render;

import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.client.GolemBackpackClientData;
import thaumicenergistics_ce.golem.BackpackSkins;
import net.minecraft.client.renderer.rendertype.RenderTypes;

@EventBusSubscriber(modid = ThEIds.MODID, value = Dist.CLIENT)
public final class GolemBackpackRenderer {

    private static final float PACK_HEIGHT = 0.42F;

    private static final float PACK_DEPTH = 0.16F;

    private static final float PACK_SCALE = 1.0F;

    private static final float PEARL_SPIN_PER_TICK = 2.0F;

    private static final GolemBackpackModel MODEL = GolemBackpackModel.create();

    private static final Map<BackpackSkins, RenderType> PACK_TYPES = new EnumMap<>(BackpackSkins.class);
    private static final Map<BackpackSkins, RenderType> PEARL_TYPES = new EnumMap<>(BackpackSkins.class);

    private GolemBackpackRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent.AfterTranslucentFeatures event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        BufferSource buffers = minecraft.renderBuffers().bufferSource();
        boolean drewAny = false;

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof EntityThaumaturgeGolem golem)
                    || !GolemBackpackClientData.hasBackpack(golem)
                    || golem.isInvisibleTo(player)) {
                continue;
            }

            BackpackSkins skin = GolemBackpackClientData.skinOf(golem);
            boolean inRange = GolemBackpackClientData.isInRange(golem);
            double x = Mth.lerp(partialTick, golem.xOld, golem.getX()) - camera.x;
            double y = Mth.lerp(partialTick, golem.yOld, golem.getY()) - camera.y;
            double z = Mth.lerp(partialTick, golem.zOld, golem.getZ()) - camera.z;
            float bodyRotation = Mth.rotLerp(partialTick, golem.yBodyRotO, golem.yBodyRot);
            int light = LevelRenderer.getLightCoords(
                    level, BlockPos.containing(golem.getX(), golem.getEyeY(), golem.getZ()));

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyRotation));
            poseStack.translate(0.0F, PACK_HEIGHT, PACK_DEPTH);
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            poseStack.scale(PACK_SCALE, PACK_SCALE, PACK_SCALE);

            MODEL.renderPack(poseStack,
                    buffers.getBuffer(PACK_TYPES.computeIfAbsent(
                            skin, s -> RenderTypes.entityCutout(s.texture()))),
                    light, OverlayTexture.NO_OVERLAY);
            MODEL.renderPearl(poseStack,
                    buffers.getBuffer(PEARL_TYPES.computeIfAbsent(
                            skin, s -> RenderTypes.entityTranslucent(s.texture()))),
                    light, OverlayTexture.NO_OVERLAY,
                    (golem.tickCount + partialTick) * PEARL_SPIN_PER_TICK, inRange);
            poseStack.popPose();
            drewAny = true;
        }

        if (drewAny) {
            buffers.endBatch();
        }
    }
}
