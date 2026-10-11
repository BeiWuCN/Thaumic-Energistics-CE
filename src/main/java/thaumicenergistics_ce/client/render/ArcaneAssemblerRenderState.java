package thaumicenergistics_ce.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.jspecify.annotations.Nullable;

public class ArcaneAssemblerRenderState extends BlockEntityRenderState {

    public @Nullable ItemStackRenderState item;

    public float age;

    public float drop;
}
