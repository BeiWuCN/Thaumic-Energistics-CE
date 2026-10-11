package thaumicenergistics_ce.client.render;

import appeng.client.api.AEKeyRenderState;
import appeng.client.api.AEKeyRenderer;
import appeng.util.Platform;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.util.ThELog;

public class EssentiaKeyRenderHandler implements AEKeyRenderer<AEssentiaKey, AEKeyRenderState> {

    private static final Set<Identifier> REPORTED = ConcurrentHashMap.newKeySet();

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphicsExtractor graphics, int x, int y, AEssentiaKey key) {
        Holder<IAspect> aspect = key.resolveAspect();
        if (aspect == null) {
            if (REPORTED.add(key.getId())) {
                ThELog.LOG.warn(
                        "[essentia-icon] {} has no aspect ({}); drawing the missing chip",
                        key.getId(),
                        AEssentiaKeyType.whyNoAspect(key.getId()));
            }
            AspectRendering.renderMissingGui(graphics, x, y);
            return;
        }
        AspectRendering.renderGui(graphics, minecraft.font, x, y, aspect, 0.0F);
    }

    @Override
    public Class<AEKeyRenderState> stateClass() {
        return AEKeyRenderState.class;
    }

    @Override
    public AEKeyRenderState createState() {
        return new AEKeyRenderState();
    }

    @Override
    public void extract(AEKeyRenderState state, AEssentiaKey key, Level level, int packedLight) {
    }

    @Override
    public void submit(
            PoseStack poseStack, AEKeyRenderState state, SubmitNodeCollector submitNodeCollector, int packedLight) {
    }

    @Override
    public List<Component> getTooltip(AEssentiaKey key) {
        return List.of(key.getDisplayName(), Component.literal(Platform.formatModName(key.getModId())));
    }
}
