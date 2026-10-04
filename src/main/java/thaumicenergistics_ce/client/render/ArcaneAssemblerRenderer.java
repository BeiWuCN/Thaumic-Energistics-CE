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
import thaumicenergistics_ce.blockentity.BlockEntityArcaneAssembler;

/**
 * Draws the product of a running craft inside the Arcane Assembler, as a molecular assembler does.
 * <ul>
 * <li>The stack is the client's copy out of the block entity's update tag; rendering it is all this
 * class does with it, and the machine's real product stays in the server's well.</li>
 * <li>The last product lingers for {@link #LINGER_TICKS} after its craft ends - a craft is twenty
 * ticks, four with speed upgrades, so drawing only while crafting blinked several times a job.</li>
 * <li>Nothing here is a function of craft progress: that number resets every craft, and a position
 * that depends on it jumps. The item turns and bobs on the clock instead.</li>
 * </ul>
 */
public class ArcaneAssemblerRenderer implements BlockEntityRenderer<BlockEntityArcaneAssembler> {

    /**
     * How far the item is dropped below the block's middle: an item model is drawn upward from its
     * own feet, and a block model is centred on its origin while an item model is not, so the two
     * numbers differ. Both are the molecular assembler's.
     */
    private static final float ITEM_DROP = 0.3F;
    private static final float BLOCK_DROP = 0.2F;

    /** Degrees a tick. Slow enough to read as a machine turning something over, not as a spinner. */
    private static final float SPIN_PER_TICK = 1.5F;

    /** The rise and fall, in blocks, and how many ticks one full bob takes. */
    private static final float BOB_HEIGHT = 0.03F;
    private static final float BOB_PERIOD = 25.0F;

    /**
     * Ticks the last product keeps hanging there, unmoving, after its craft ends: two seconds, enough
     * to bridge the few ticks the crafting CPU takes to push the next craft.
     */
    private static final float LINGER_TICKS = 40.0F;

    /** What each block was last seen drawing, and when. Weakly keyed, so an unloaded block is forgotten. */
    private static final class LastDraw {
        ItemStack stack = ItemStack.EMPTY;
        float seenAt;
    }

    private final Map<BlockEntityArcaneAssembler, LastDraw> lastDrawn = new WeakHashMap<>();

    public ArcaneAssemblerRenderer(BlockEntityRendererProvider.Context context) {
        // No model to take from the context: the block draws the game's own item models.
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

    /** One item, in the middle of the block, turning slowly on the clock and nothing else. */
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
