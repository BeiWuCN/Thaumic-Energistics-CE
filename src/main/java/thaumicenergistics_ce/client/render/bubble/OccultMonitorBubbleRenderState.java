package thaumicenergistics_ce.client.render.bubble;

import java.util.List;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jspecify.annotations.Nullable;

public final class OccultMonitorBubbleRenderState extends BlockEntityRenderState {

    @Nullable
    List<List<BubbleCells.Cell>> rows;

    float panelWidth;
    float panelHeight;
}
