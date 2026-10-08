package thaumicenergistics_ce.client.render.bubble;

import java.util.List;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * 气泡渲染器从 extract 阶段传给 submit 阶段的东西。
 * <p>
 * 量面板要字体、还要看一眼机器，画它要相机；新的方块实体渲染器模型把两件事分在两个阶段做，
 * 所以行和它们加出来的方框存在这里。{@code rows} 为 null 表示这一帧没东西可画。
 */
public final class OccultMonitorBubbleRenderState extends BlockEntityRenderState {

    /** 排好版的行；机器没在报告、面板没建出来时为 null。 */
    @Nullable
    List<List<BubbleCells.Cell>> rows;

    /** 面板尺寸，单位是面板单位，含内边距；只在 extract 阶段量一次。 */
    float panelWidth;
    float panelHeight;
}
