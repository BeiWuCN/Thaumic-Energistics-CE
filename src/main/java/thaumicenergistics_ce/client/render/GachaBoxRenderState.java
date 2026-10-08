package thaumicenergistics_ce.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/**
 * 抽奖盒渲染器从 extract 阶段传给 submit 阶段的东西。
 * <p>
 * 两阶段拆分意味着再不能用局部变量：大脑在不在、转了多少度都在 {@code extractRenderState} 里定下，
 * 在 {@code submit} 里才画。
 */
public class GachaBoxRenderState extends BlockEntityRenderState {

    /** 箱里有没有大脑，也就是有没有东西可画。 */
    public boolean hasJar;

    /** 这一帧大脑转动的角度，单位是度。 */
    public float turn;
}
