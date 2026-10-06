package thaumicenergistics_ce.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * The backpack: a box with an antenna, and a pearl that says whether the network is there.
 * It is baked from a {@link LayerDefinition}, not a registered model layer. The three boxes are the
 * reference build's own units and rotations, matching the ten skin textures, and lie sideways: the
 * renderer turns the model a quarter turn about Y, and swapping the box dimensions instead would
 * rotate the texture on every face. The pearl is four double-sided faces built by hand, so it can
 * be red as easily as green.
 */
public final class GolemBackpackModel {

    private static final int TEXTURE_WIDTH = 16;
    private static final int TEXTURE_HEIGHT = 16;

    private static final float PEARL_SIZE = 0.125F;

    /**
     * How far above the pack's origin the pearl's underside sits, in the pearl's own units: 2.85 puts it on
     * the antenna's tip (3.0 in these units) rather than floating above it.
     */
    private static final float PEARL_BOTTOM = 2.85F;

    private static final float PEARL_HALF_WIDTH = 0.55F;

    /** The pearl's corner in the skin texture, in sixteenths. */
    private static final float PEARL_MIN_U = 8.0F / 16.0F;
    private static final float PEARL_MAX_U = 13.5F / 16.0F;
    private static final float PEARL_MIN_V = 6.0F / 16.0F;
    private static final float PEARL_MAX_V = 11.5F / 16.0F;

    private final ModelPart root;

    private GolemBackpackModel(ModelPart root) {
        this.root = root;
    }

    public static GolemBackpackModel create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();

        parts.addOrReplaceChild("antenna",
                CubeListBuilder.create().texOffs(10, 0).addBox(-0.5F, -6.0F, -0.5F, 1, 3, 1),
                PartPose.rotation((float) Math.PI, 0.0F, 0.0F));
        parts.addOrReplaceChild("pack_back",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -3.0F, -3.0F, 2, 6, 6),
                PartPose.rotation((float) Math.PI, 0.0F, 0.0F));
        parts.addOrReplaceChild("pack_front",
                CubeListBuilder.create().texOffs(2, 0).addBox(-1.5F, -1.0F, -2.0F, 1, 2, 4),
                PartPose.rotation((float) Math.PI, 0.0F, 0.0F));

        return new GolemBackpackModel(
                LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT).bakeRoot());
    }

    public void renderPack(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay) {
        root.render(poseStack, buffer, packedLight, packedOverlay);
    }

    public void renderPearl(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
            float spin, boolean inRange) {
        int red = 255;
        int green = inRange ? 255 : 0;
        int blue = inRange ? 255 : 0;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        poseStack.scale(PEARL_SIZE, PEARL_SIZE, PEARL_SIZE);
        poseStack.translate(0.0F, PEARL_BOTTOM, 0.0F);

        for (int face = 0; face < 4; face++) {
            poseStack.pushPose();
            // Faces 1 and 3 turn a quarter turn, which puts them on the other two sides of the antenna.
            if ((face & 1) == 1) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            }
            poseStack.translate(0.0F, 0.0F, (face & 2) == 0 ? PEARL_HALF_WIDTH : -PEARL_HALF_WIDTH);
            drawPearlFace(poseStack, buffer, packedLight, packedOverlay, red, green, blue);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private static void drawPearlFace(PoseStack poseStack, VertexConsumer buffer, int packedLight,
            int packedOverlay, int red, int green, int blue) {
        Matrix4f matrix = poseStack.last().pose();
        float[][] corners = {{-0.5F, 0.5F}, {0.5F, 0.5F}, {0.5F, -0.5F}, {-0.5F, -0.5F}};
        float[][] uvs = {
                {PEARL_MAX_U, PEARL_MAX_V},
                {PEARL_MIN_U, PEARL_MAX_V},
                {PEARL_MIN_U, PEARL_MIN_V},
                {PEARL_MAX_U, PEARL_MIN_V}};
        Vector4f position = new Vector4f();

        for (int i = 0; i < 4; i++) {
            addVertex(buffer, matrix, position, corners[i], uvs[i], red, green, blue,
                    packedLight, packedOverlay, 1.0F);
        }
        for (int i = 3; i >= 0; i--) {
            addVertex(buffer, matrix, position, corners[i], uvs[i], red, green, blue,
                    packedLight, packedOverlay, -1.0F);
        }
    }

    /**
     * One corner, transformed and written. The scratch vector is reused rather than allocated: this runs
     * once per vertex per golem per frame, and a backpack should not be the reason frame time moves.
     */
    private static void addVertex(VertexConsumer buffer, Matrix4f matrix, Vector4f scratch, float[] corner,
            float[] uv, int red, int green, int blue, int packedLight, int packedOverlay, float normal) {
        scratch.set(corner[0], corner[1], 0.0F, 1.0F);
        matrix.transform(scratch);
        buffer.addVertex(scratch.x, scratch.y, scratch.z)
                .setColor(red, green, blue, 255)
                .setUv(uv[0], uv[1])
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(0.0F, 0.0F, normal);
    }
}
