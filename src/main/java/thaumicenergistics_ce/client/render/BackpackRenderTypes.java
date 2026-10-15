package thaumicenergistics_ce.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Emissive faces keep back-face culling, including the single-sided antenna. */
final class BackpackRenderTypes extends RenderStateShard {
    private BackpackRenderTypes() {
        super("tece_backpack", () -> {}, () -> {});
    }

    static RenderType emissive(ResourceLocation texture) {
        return RenderType.create("tece_backpack_emissive", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 256, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new TextureStateShard(texture, false, false))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setCullState(CULL)
                        .setLightmapState(NO_LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(true));
    }
}
