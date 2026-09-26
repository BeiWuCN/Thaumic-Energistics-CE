package thaumicenergistics.client;

import appeng.api.client.AEKeyRenderHandler;
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
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.integration.ae2.AEssentiaKey;
import thaumicenergistics.integration.ae2.AEssentiaKeyType;

/**
 * How AE2 draws an essentia key.
 *
 * <p>Registering one of these is not optional. AE2 asks for a handler by key type whenever it draws a key
 * - in a terminal row, on a storage cell's tooltip, on a bus's config slot - and throws when there is
 * none. The failure looks nothing like a missing registration: it arrives as a crash while rendering a
 * screen, from inside AE2's own tooltip code, the first time a cell holding essentia is hovered.
 *
 * <p>The icon comes from Thaumaturge's own {@link AspectRendering}, which is its public drawing entry
 * point and already knows about the aspect textures, their blend mode and whether the player has
 * discovered the aspect. Drawing the texture here by hand was the first attempt and is the wrong one -
 * the aspect chips are not plain textures, and a hand-rolled blit loses the discovery masking.
 */
public class EssentiaKeyRenderHandler implements AEKeyRenderHandler<AEssentiaKey> {

    /**
     * Ids already reported as unresolvable.
     *
     * <p>Once each, not once per slot per frame: this is a diagnostic, and a terminal of eight aspects
     * redrawing sixty times a second would otherwise fill the log with one fact.
     */
    private static final Set<ResourceLocation> REPORTED = ConcurrentHashMap.newKeySet();

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics graphics, int x, int y, AEssentiaKey key) {
        Holder<IAspect> aspect = key.resolveAspect();
        if (aspect == null) {
            // A chip reading "unknown" is a report; an empty slot is not. Nothing else here is visible:
            // AE2 catches whatever a render handler throws and carries on, so a key whose aspect cannot be
            // resolved disappears from the screen without a word - which is how this took three rounds to
            // see. The line below is the only evidence that this path ran at all.
            if (REPORTED.add(key.getId())) {
                ThaumicEnergistics.LOG.warn(
                        "[essentia-icon] {} has no aspect ({}); drawing the missing chip",
                        key.getId(),
                        AEssentiaKeyType.whyNoAspect(key.getId()));
            }
            AspectRendering.renderMissingGui(graphics, x, y, key.getId());
            return;
        }
        // The amount is zero because AE2 draws the count itself, right after this and on top. Asking for
        // the chip's own label as well would print the number twice.
        AspectRendering.renderGui(graphics, minecraft.font, x, y, aspect, 0.0F);
    }

    /**
     * Not drawn on block faces.
     *
     * <p>AE2 calls this for keys shown in the world - on a storage monitor, or a facade. Essentia has no
     * such display here, and the reference build leaves it empty for the same reason.
     */
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
        return List.of(key.getDisplayName(), Component.literal(appeng.util.Platform.formatModName(key.getModId())));
    }
}
