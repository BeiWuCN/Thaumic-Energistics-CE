package thaumicenergistics_ce.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.jspecify.annotations.Nullable;

/**
 * 奥术组装机渲染器从 extract 阶段带到 submit 阶段的东西。
 * <p>
 * 新的方块实体渲染模型把一帧拆成两半：{@code extractRenderState} 决定有什么，
 * {@code submit} 只负责把它画出来。旋转和上下浮动要的一切因此都成了这里的字段，
 * 两个阶段再也共享不了局部变量。
 */
public class ArcaneAssemblerRenderState extends BlockEntityRenderState {

    /** 还在展示的产物；机器闲置超过停留窗口后为 null。 */
    public @Nullable ItemStackRenderState item;

    /** 这一帧的年龄，按 tick 计，驱动旋转和上下浮动。 */
    public float age;

    /** 模型低于方块中点的距离；物品模型和方块模型落下的量不同。 */
    public float drop;
}
