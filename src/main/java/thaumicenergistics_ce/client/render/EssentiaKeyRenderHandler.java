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

/**
 * AE2 怎么画源质 key。注册是硬要求：画没有处理器的 key 类型时 AE2 会抛。
 * 图标来自 Thaumaturge 的 {@link AspectRendering}，它知道要素纹理和玩家的发现状态；
 * 手写 blit 会丢掉遮罩。
 */
public class EssentiaKeyRenderHandler implements AEKeyRenderer<AEssentiaKey, AEKeyRenderState> {

    private static final Set<Identifier> REPORTED = ConcurrentHashMap.newKeySet();

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphicsExtractor graphics, int x, int y, AEssentiaKey key) {
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
    public Class<AEKeyRenderState> stateClass() {
        return AEKeyRenderState.class;
    }

    @Override
    public AEKeyRenderState createState() {
        return new AEKeyRenderState();
    }

    @Override
    public void extract(AEKeyRenderState state, AEssentiaKey key, Level level, int packedLight) {
        // 没有方块表面形态：见类注释。
    }

    @Override
    public void submit(
            PoseStack poseStack, AEKeyRenderState state, SubmitNodeCollector submitNodeCollector, int packedLight) {
        // 没有方块表面形态：见类注释。
    }

    @Override
    public List<Component> getTooltip(AEssentiaKey key) {
        return List.of(key.getDisplayName(), Component.literal(Platform.formatModName(key.getModId())));
    }
}
