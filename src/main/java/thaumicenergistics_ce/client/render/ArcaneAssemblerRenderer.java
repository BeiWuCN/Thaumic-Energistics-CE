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

public class ArcaneAssemblerRenderer
        implements BlockEntityRenderer<BlockEntityArcaneAssembler, ArcaneAssemblerRenderState> {

    private static final float ITEM_DROP = 0.3F;
    private static final float BLOCK_DROP = 0.2F;

    private static final float SPIN_PER_TICK = 1.5F;

    private static final float BOB_HEIGHT = 0.03F;
    private static final float BOB_PERIOD = 25.0F;

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
