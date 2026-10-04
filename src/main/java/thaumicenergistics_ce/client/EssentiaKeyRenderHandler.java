package thaumicenergistics_ce.client;

import appeng.api.client.AEKeyRenderHandler;
import appeng.util.Platform;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

/**
 * How AE2 draws an essentia key.
 * <ul>
 *   <li>Registration is mandatory: AE2 asks for a handler by key type whenever it draws a key, and
 *       throws when there is none.
 *   <li>The icon comes from Thaumaturge's own {@link AspectRendering}, which knows the aspect textures
 *       and the player's discovery state - a hand-rolled blit loses the masking.
 * </ul>
 */
public class EssentiaKeyRenderHandler implements AEKeyRenderHandler<AEssentiaKey> {

    /** Ids already reported as unresolvable, once each rather than once per slot per frame. */
    private static final Set<ResourceLocation> REPORTED = ConcurrentHashMap.newKeySet();

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics graphics, int x, int y, AEssentiaKey key) {
        Holder<IAspect> aspect = key.resolveAspect();
        if (aspect == null) {
            // AE2 swallows anything a render handler throws, so an unresolved key vanishes silently:
            // this warning is the only evidence the path ran.
            if (REPORTED.add(key.getId())) {
                ThaumicEnergistics.LOG.warn(
                        "[essentia-icon] {} has no aspect ({}); drawing the missing chip",
                        key.getId(),
                        AEssentiaKeyType.whyNoAspect(key.getId()));
            }
            AspectRendering.renderMissingGui(graphics, x, y);
            return;
        }
        // The amount is zero because AE2 draws the count itself, right after this and on top. Asking for
        // the chip's own label as well would print the number twice.
        AspectRendering.renderGui(graphics, minecraft.font, x, y, aspect, 0.0F);
    }

    /** Not drawn on block faces: an essentia key has no in-world display here. */
    @Override
    public void drawOnBlockFace(
            PoseStack poseStack,
            MultiBufferSource buffers,
            AEssentiaKey key,
            float scale,
            int packedLight,
            Level level) {
        // Intentionally nothing.
    }

    @Override
    public Component getDisplayName(AEssentiaKey key) {
        return key.getDisplayName();
    }

    @Override
    public List<Component> getTooltip(AEssentiaKey key) {
        return List.of(key.getDisplayName(), Component.literal(Platform.formatModName(key.getModId())));
    }
}
