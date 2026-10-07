package thaumicenergistics_ce.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;

/**
 * 在奥术组装机内部绘制正在进行的合成的产物，做法与分子组装机相同。
 * 物品堆来自方块实体的 update 标签，所以机器的真实产物仍留在服务端。
 * 最后一件产物在合成结束后保留 {@link #LINGER_TICKS}，且不依赖合成进度——
 * 进度每次合成都会重置，会让物品跳变。
 */
public class ArcaneAssemblerRenderer implements BlockEntityRenderer<BlockEntityArcaneAssembler> {

    /**
     * 物品下移到方块中心以下的距离：物品模型从自身底端向上绘制，
     * 而方块模型以原点为中心，所以两者数值不同。
     */
    private static final float ITEM_DROP = 0.3F;
    private static final float BLOCK_DROP = 0.2F;

    private static final float SPIN_PER_TICK = 1.5F;

    private static final float BOB_HEIGHT = 0.03F;
    private static final float BOB_PERIOD = 25.0F;

    /**
     * 最后一件产物在合成结束后静止悬留的 tick 数：两秒，足以
     * 跨过合成 CPU 推送下一次合成所需的那几个 tick。
     */
    private static final float LINGER_TICKS = 40.0F;

    private static final class LastDraw {
        ItemStack stack = ItemStack.EMPTY;
        float seenAt;
    }

    private final Map<BlockEntityArcaneAssembler, LastDraw> lastDrawn = new WeakHashMap<>();

    public ArcaneAssemblerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BlockEntityArcaneAssembler assembler, float partialTick, PoseStack poses,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        long gameTime = assembler.getLevel() == null ? 0L : assembler.getLevel().getGameTime();
        float age = gameTime + partialTick;

        ItemStack product = assembler.previewStack();
        LastDraw remembered = lastDrawn.computeIfAbsent(assembler, key -> new LastDraw());

        if (!product.isEmpty() && assembler.isCrafting()) {
            remembered.stack = product;
            remembered.seenAt = age;
            draw(assembler, product, age, poses, buffers, packedLight);
            return;
        }

        if (remembered.stack.isEmpty()) {
            return;
        }
        float since = age - remembered.seenAt;
        if (since > LINGER_TICKS || since < 0.0F) {
            remembered.stack = ItemStack.EMPTY;
            return;
        }
        draw(assembler, remembered.stack, age, poses, buffers, packedLight);
    }

    private static void draw(BlockEntityArcaneAssembler assembler, ItemStack product, float age,
            PoseStack poses, MultiBufferSource buffers, int packedLight) {
        float drop = product.getItem() instanceof BlockItem ? BLOCK_DROP : ITEM_DROP;
        float lift = Mth.sin(age / BOB_PERIOD * Mth.TWO_PI) * BOB_HEIGHT;

        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees(age * SPIN_PER_TICK));
        poses.translate(0.0, lift - drop, 0.0);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                product, ItemDisplayContext.GROUND, packedLight, OverlayTexture.NO_OVERLAY,
                poses, buffers, assembler.getLevel(), 0);
        poses.popPose();
    }
}
