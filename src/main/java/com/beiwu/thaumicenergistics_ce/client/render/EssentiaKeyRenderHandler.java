package com.beiwu.thaumicenergistics_ce.client.render;

import appeng.client.api.AEKeyRenderer;
import appeng.util.Platform;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.AspectKnowledge;
import com.leclowndu93150.thaumaturge.api.aspect.AspectKnowledgeAccess;
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
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import com.beiwu.thaumicenergistics_ce.util.ThELog;

public class EssentiaKeyRenderHandler implements AEKeyRenderer<AEssentiaKey, EssentiaKeyRenderHandler.State> {

    public static final class State {
        private Holder<IAspect> aspect;
        private AspectKnowledge knowledge = AspectKnowledge.UNKNOWN;
    }

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
    public Class<State> stateClass() {
        return State.class;
    }

    @Override
    public State createState() {
        return new State();
    }

    @Override
    public void extract(State state, AEssentiaKey key, Level level, int seed) {
        state.aspect = key.resolveAspect();
        state.knowledge = state.aspect == null ? AspectKnowledge.UNKNOWN : AspectKnowledgeAccess.of(state.aspect);
    }

    @Override
    public void submit(
            PoseStack poseStack, State state, SubmitNodeCollector submitNodeCollector, int packedLight) {
        poseStack.pushPose();
        // Match AE2's item/fluid screen icons: move along the monitor's local
        // outward normal, after its face rotation, to avoid coplanar flicker.
        poseStack.translate(0, 0, 0.01F);
        var aspect = state.aspect;
        var knowledge = state.knowledge;
        if (aspect == null) {
            submitNodeCollector.submitCustomGeometry(poseStack,
                    AspectRendering.missingRenderType(AspectRendering.BlendMode.ALPHA),
                    (pose, buffer) -> AspectRendering.renderMissingQuad(pose, buffer, 1.0F, packedLight));
        } else {
            submitNodeCollector.submitCustomGeometry(poseStack,
                    AspectRendering.renderType(aspect, knowledge, AspectRendering.BlendMode.ALPHA),
                    (pose, buffer) -> AspectRendering.renderQuad(pose, buffer, aspect, knowledge, 1.0F, false, packedLight));
        }
        poseStack.popPose();
    }

    @Override
    public List<Component> getTooltip(AEssentiaKey key) {
        return List.of(key.getDisplayName(), Component.literal(Platform.formatModName(key.getModId())));
    }
}
