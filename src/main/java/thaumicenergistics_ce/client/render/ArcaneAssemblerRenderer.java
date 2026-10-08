package thaumicenergistics_ce.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;

/**
 * 在奥术组装机里画正在进行的合成的产物，做法和分子组装机一样。
 * 物品堆取自方块实体的 update 标签，机器的真实产物仍留在服务端。
 * 最后一件产物在合成结束后留 {@link #LINGER_TICKS}，不看合成进度：
 * 进度每合成一次就重置，会让物品跳。
 */
public class ArcaneAssemblerRenderer
        implements BlockEntityRenderer<BlockEntityArcaneAssembler, ArcaneAssemblerRenderState> {

    /**
     * 物品下移到方块中心以下的距离：物品模型从自身底端往上画，
     * 方块模型以原点为中心，两者数值不一样。
     */
    private static final float ITEM_DROP = 0.3F;
    private static final float BLOCK_DROP = 0.2F;

    private static final float SPIN_PER_TICK = 1.5F;

    private static final float BOB_HEIGHT = 0.03F;
    private static final float BOB_PERIOD = 25.0F;

    /**
     * 最后一件产物在合成结束后悬留的 tick 数：两秒，够跨过合成 CPU 推下一次合成的那几个 tick。
     */
    private static final float LINGER_TICKS = 40.0F;

    private static final class LastDraw {
        ItemStack stack = ItemStack.EMPTY;
        float seenAt;
    }

    private final Map<BlockEntityArcaneAssembler, LastDraw> lastDrawn = new WeakHashMap<>();

    private final ItemModelResolver itemModelResolver;

    public ArcaneAssemblerRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public ArcaneAssemblerRenderState createRenderState() {
        return new ArcaneAssemblerRenderState();
    }

    @Override
    public void extractRenderState(
            BlockEntityArcaneAssembler assembler,
            ArcaneAssemblerRenderState state,
            float partialTick,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(assembler, state, partialTick, cameraPosition, breakProgress);

        long gameTime = assembler.getLevel() == null ? 0L : assembler.getLevel().getGameTime();
        float age = gameTime + partialTick;

        ItemStack product = assembler.previewStack();
        LastDraw remembered = lastDrawn.computeIfAbsent(assembler, key -> new LastDraw());

        ItemStack shown = ItemStack.EMPTY;
        if (!product.isEmpty() && assembler.isCrafting()) {
            remembered.stack = product;
            remembered.seenAt = age;
            shown = product;
        } else if (!remembered.stack.isEmpty()) {
            float since = age - remembered.seenAt;
            if (since > LINGER_TICKS || since < 0.0F) {
                remembered.stack = ItemStack.EMPTY;
            } else {
                shown = remembered.stack;
            }
        }

        state.age = age;
        state.item = null;
        if (shown.isEmpty()) {
            return;
        }
        state.drop = shown.getItem() instanceof BlockItem ? BLOCK_DROP : ITEM_DROP;
        // 每次 extract 都新建渲染状态：上一个属于模型上次被解析的那一帧，
        // 复用会在产物换掉后留下一个过时的模型。
        ItemStackRenderState item = new ItemStackRenderState();
        this.itemModelResolver.updateForTopItem(
                item, shown, ItemDisplayContext.GROUND, assembler.getLevel(), null, 0);
        state.item = item;
    }

    @Override
    public void submit(
            ArcaneAssemblerRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera) {
        ItemStackRenderState item = state.item;
        if (item == null) {
            return;
        }
        float lift = Mth.sin(state.age / BOB_PERIOD * Mth.TWO_PI) * BOB_HEIGHT;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.age * SPIN_PER_TICK));
        poseStack.translate(0.0, lift - state.drop, 0.0);
        item.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
