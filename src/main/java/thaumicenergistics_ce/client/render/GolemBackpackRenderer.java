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
import thaumicenergistics_ce.golem.BackpackSkins;

/**
 * 每帧从世界渲染阶段画出所有可见背包。
 * Thaumaturge 的傀儡渲染器不是 NeoForge 能加层的 living 类型，没有钩子可挂。
 * 附着位置与部件几何由可重载的 JSON 模型提供。
 * 绘制在 AFTER_ENTITIES 阶段，批次在这里收尾。
 */
@EventBusSubscriber(modid = ThEIds.MODID, value = Dist.CLIENT)
public final class GolemBackpackRenderer {

    private GolemBackpackRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        // 本帧在 tick 内的比例；插值后的傀儡位置就落在两个 tick 之间。
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
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
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(golem.getX(), golem.getEyeY(), golem.getZ()));

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            // 傀儡渲染器自身的主体旋转；模型空间 +Z 是傀儡背面，取 +depth。
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
