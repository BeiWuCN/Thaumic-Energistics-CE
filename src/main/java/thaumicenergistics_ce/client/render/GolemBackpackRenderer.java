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
import net.minecraft.client.renderer.RenderType;
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
 * Draws every visible backpack once per frame, from the world stage rather than from the golem's renderer.
 *
 * <p>There is no way into the golem's own render frame: Thaumaturge's golem renderer is an
 * {@code EntityRenderer} rather than the living kind NeoForge lets a mod add a layer to, and its mesh parts
 * come from a private map of the five built-ins. So the pack is drawn beside the golem, in the same body
 * rotation its renderer uses - which is why {@link #PACK_HEIGHT} and {@link #PACK_DEPTH} had to be placed by
 * eye rather than inherited from the golem's model.
 *
 * <p>Drawn at {@code AFTER_ENTITIES}, with the golems the packs belong to and before anything transparent
 * goes over the world. The batch is ended here because the stage's buffer is not this code's to leave open.
 */
@EventBusSubscriber(modid = ThEIds.MODID, value = Dist.CLIENT)
public final class GolemBackpackRenderer {

    /** How high up the golem's body the pack sits, in blocks above its feet. */
    private static final float PACK_HEIGHT = 0.42F;

    /** How far behind the golem's centre the pack sits, in blocks. */
    private static final float PACK_DEPTH = 0.16F;

    /** The pack's scale. One, because the model is already authored in blocks. */
    private static final float PACK_SCALE = 1.0F;

    /** Degrees per tick the pearl turns. */
    private static final float PEARL_SPIN_PER_TICK = 2.0F;

    private static final GolemBackpackModel MODEL = GolemBackpackModel.create();

    /**
     * Render types by skin, built once each.
     *
     * <p>{@code RenderType.entityCutoutNoCull(texture)} builds a new object every call, so asking for one
     * per golem per frame is a per-frame allocation for each visible golem - and, worse, a type switch in
     * the buffer source, which flushes the batch it was in the middle of. Ten skins, ten types.
     */
    private static final Map<BackpackSkins, RenderType> PACK_TYPES = new EnumMap<>(BackpackSkins.class);
    private static final Map<BackpackSkins, RenderType> PEARL_TYPES = new EnumMap<>(BackpackSkins.class);

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
        // The frame's fraction of a tick, which is what makes an interpolated golem position land
        // between two ticks rather than snapping to the last one.
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
            boolean inRange = GolemBackpackClientData.isInRange(golem);
            double x = Mth.lerp(partialTick, golem.xOld, golem.getX()) - camera.x;
            double y = Mth.lerp(partialTick, golem.yOld, golem.getY()) - camera.y;
            double z = Mth.lerp(partialTick, golem.zOld, golem.getZ()) - camera.z;
            float bodyRotation = Mth.rotLerp(partialTick, golem.yBodyRotO, golem.yBodyRot);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(golem.getX(), golem.getEyeY(), golem.getZ()));

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            // The golem renderer's own body rotation. Model space's +Z is the golem's back, which is why
            // the pack is placed at a positive depth below.
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyRotation));
            poseStack.translate(0.0F, PACK_HEIGHT, PACK_DEPTH);
            // A quarter turn, because the model's boxes are authored lying sideways: the pack is two pixels
            // thick along X, which without this turn stands across the golem's back like a plank. The turn
            // is here and not in the model so the skin textures keep matching the faces they were drawn for.
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            poseStack.scale(PACK_SCALE, PACK_SCALE, PACK_SCALE);

            MODEL.renderPack(poseStack,
                    buffers.getBuffer(PACK_TYPES.computeIfAbsent(
                            skin, s -> RenderType.entityCutoutNoCull(s.texture()))),
                    light, OverlayTexture.NO_OVERLAY);
            MODEL.renderPearl(poseStack,
                    buffers.getBuffer(PEARL_TYPES.computeIfAbsent(
                            skin, s -> RenderType.entityTranslucent(s.texture()))),
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
