package thaumicenergistics_ce.client.render;

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
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.util.ThELog;

/**
 * AE2 怎么画源质 key。注册是硬要求：画没有处理器的 key 类型时 AE2 会抛。
 * 图标来自 Thaumaturge 的 {@link AspectRendering}，它知道要素纹理和玩家的发现状态；
 * 手写 blit 会丢掉遮罩。
 */
public class EssentiaKeyRenderHandler implements AEKeyRenderHandler<AEssentiaKey> {

    private static final Set<ResourceLocation> REPORTED = ConcurrentHashMap.newKeySet();

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics graphics, int x, int y, AEssentiaKey key) {
        Holder<IAspect> aspect = key.resolveAspect();
        if (aspect == null) {
            // AE2 吞掉渲染处理器抛的一切，未解析的 key 就此无声消失：这条警告是那段代码跑过的唯一证据。
            if (REPORTED.add(key.getId())) {
                ThELog.LOG.warn(
                        "[essentia-icon] {} has no aspect ({}); drawing the missing chip",
                        key.getId(),
                        AEssentiaKeyType.whyNoAspect(key.getId()));
            }
            AspectRendering.renderMissingGui(graphics, x, y);
            return;
        }
        // 数量写 0，因 AE2 紧接着在这之上自己画数量。再要芯片自己的标签会把数字印两遍。
        AspectRendering.renderGui(graphics, minecraft.font, x, y, aspect, 0.0F);
    }

    @Override
    public void drawOnBlockFace(
            PoseStack poseStack,
            MultiBufferSource buffers,
            AEssentiaKey key,
            float scale,
            int packedLight,
            Level level) {
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
