package thaumicenergistics_ce.client.render;

import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
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
import thaumicenergistics_ce.integration.thaumaturge.golem.BackpackSkins;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

@EventBusSubscriber(modid = ThEIds.MODID, value = Dist.CLIENT)
public final class GolemBackpackRenderer {

    private GolemBackpackRenderer() {}

    @SubscribeEvent
    public static void registerModelReload(AddClientReloadListenersEvent event) {
        event.addListener(ThEIds.id("golem_backpack"), (ResourceManagerReloadListener) GolemBackpackModel::reload);
    }

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
            boolean working = GolemBackpackClientData.isWorking(golem);
            double x = Mth.lerp(partialTick, golem.xOld, golem.getX()) - camera.x;
            double y = Mth.lerp(partialTick, golem.yOld, golem.getY()) - camera.y;
            double z = Mth.lerp(partialTick, golem.zOld, golem.getZ()) - camera.z;
            float bodyRotation = Mth.rotLerp(partialTick, golem.yBodyRotO, golem.yBodyRot);
            int light = LevelRenderer.getLightCoords(
                    level, BlockPos.containing(golem.getX(), golem.getEyeY(), golem.getZ()));

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyRotation));
            GolemBackpackModel.current().render(poseStack, buffers, skin, working, light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
            drewAny = true;
        }

        if (drewAny) {
            buffers.endBatch();
        }
    }
}
