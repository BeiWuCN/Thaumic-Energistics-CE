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
 * 背包：带天线的盒子，加一颗表示网络在不在的珍珠。由 {@link LayerDefinition} 烘出来，
 * 不是注册的模型层。三个盒子用参考实现自己的单位和旋转，对十张皮肤纹理，而且是侧躺的：
 * 渲染器把模型绕 Y 轴转四分之一圈，改成交换盒子尺寸会让每个面的纹理都转过去。
 * 珍珠是手工建的四个双面面，变红和变绿一样容易。
 */
public final class GolemBackpackModel {

    private static final int TEXTURE_WIDTH = 16;
    private static final int TEXTURE_HEIGHT = 16;

    private static final float PEARL_SIZE = 0.125F;

    /**
     * 珍珠底面在背包原点上方多高，按珍珠自己的单位：2.85 让它贴住天线尖端（这单位下 3.0），不浮在上面。
     */
    private static final float PEARL_BOTTOM = 2.85F;

    private static final float PEARL_HALF_WIDTH = 0.55F;

    /** 珍珠在皮肤纹理里的一角，单位十六分之一。 */
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
            // 面 1 和面 3 转四分之一圈，落到天线另外两侧。
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
     * 一个角点，变换后写出。临时向量复用不新分配：每帧对每个傀儡的每个顶点都跑一次，
     * 背包不该是帧时间变化的理由。
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
