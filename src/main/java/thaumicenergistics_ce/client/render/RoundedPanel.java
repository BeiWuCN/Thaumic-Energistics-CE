package thaumicenergistics_ce.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Every colour the bubble draws in, and the box that uses two of them: a filled rounded rectangle with a
 * one-pixel gradient border, the way Jade draws tooltips. Border and fill sit at different depths, because
 * coplanar quads fight for the same one and flicker.
 */
final class RoundedPanel {

    private static final int PANEL_FILL = 0xF0100010;
    private static final int BORDER_TOP = 0x505000FF;
    private static final int BORDER_BOTTOM = 0x5028007F;
    /** Corner radius, in panel units: two, matching Jade's box; ten read as far too round. */
    private static final float CORNER_RADIUS = 2.0F;

    /** Border and fill sit at different depths: coplanar quads fight for the same depth and flicker. */
    private static final float BORDER_Z = -0.08F;
    private static final float FILL_Z = -0.06F;

    private RoundedPanel() {}

    /**
     * Draws the box. {@code debugQuads} takes position and colour only, which is all the panel has to give.
     */
    static void draw(
            MultiBufferSource buffers, Matrix4f matrix, float left, float top, float right, float bottom) {
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        // The border is the whole shape in the gradient, the fill the same shape one pixel in, so the radius
        // is one less. Separate depths because sprites in one plane fight for it and flicker.
        roundedFill(quads, matrix, left, top, right, bottom, CORNER_RADIUS, BORDER_TOP, BORDER_BOTTOM, BORDER_Z);
        roundedFill(
                quads,
                matrix,
                left + 1,
                top + 1,
                right - 1,
                bottom - 1,
                CORNER_RADIUS - 1,
                PANEL_FILL,
                PANEL_FILL,
                FILL_Z);
    }

    private static void roundedFill(
            VertexConsumer quads,
            Matrix4f matrix,
            float left,
            float top,
            float right,
            float bottom,
            float radius,
            int topColour,
            int bottomColour,
            float z) {
        float r = Math.max(0.0F, Math.min(radius, Math.min((right - left) / 2.0F, (bottom - top) / 2.0F)));
        if (r < 1.0F) {
            fill(quads, matrix, left, top, right, bottom, topColour, bottomColour, z);
            return;
        }
        strip(quads, matrix, left + r, top, right - r, top + r, top, bottom, topColour, bottomColour, z);
        strip(quads, matrix, left, top + r, right, bottom - r, top, bottom, topColour, bottomColour, z);
        strip(quads, matrix, left + r, bottom - r, right - r, bottom, top, bottom, topColour, bottomColour, z);

        int steps = (int) Math.ceil(r);
        for (int i = 0; i < steps; i++) {
            float dy = r - i - 0.5F;
            float dx = r - (float) Math.sqrt(Math.max(0.0F, r * r - dy * dy));
            float yTop = top + i;
            float yBottom = bottom - i - 1;
            strip(quads, matrix, left + dx, yTop, left + r, yTop + 1, top, bottom, topColour, bottomColour, z);
            strip(quads, matrix, right - r, yTop, right - dx, yTop + 1, top, bottom, topColour, bottomColour, z);
            strip(quads, matrix, left + dx, yBottom, left + r, yBottom + 1, top, bottom, topColour, bottomColour, z);
            strip(
                    quads,
                    matrix,
                    right - r,
                    yBottom,
                    right - dx,
                    yBottom + 1,
                    top,
                    bottom,
                    topColour,
                    bottomColour,
                    z);
        }
    }

    private static void strip(
            VertexConsumer quads,
            Matrix4f matrix,
            float x0,
            float y0,
            float x1,
            float y1,
            float panelTop,
            float panelBottom,
            int topColour,
            int bottomColour,
            float z) {
        float span = Math.max(1.0F, panelBottom - panelTop);
        fill(
                quads,
                matrix,
                x0,
                y0,
                x1,
                y1,
                mix(topColour, bottomColour, (y0 - panelTop) / span),
                mix(topColour, bottomColour, (y1 - panelTop) / span),
                z);
    }

    private static void fill(
            VertexConsumer quads,
            Matrix4f matrix,
            float x0,
            float y0,
            float x1,
            float y1,
            int topColour,
            int bottomColour,
            float z) {
        quads.addVertex(matrix, x0, y0, z).setColor(topColour);
        quads.addVertex(matrix, x0, y1, z).setColor(bottomColour);
        quads.addVertex(matrix, x1, y1, z).setColor(bottomColour);
        quads.addVertex(matrix, x1, y0, z).setColor(topColour);
    }

    private static int mix(int from, int to, float t) {
        float f = Math.max(0.0F, Math.min(1.0F, t));
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * f);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * f);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * f);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** The risk tier's colour, from green for a quiet altar to red for one that will not survive it. */
    static int colourOf(int tier) {
        return switch (tier) {
            case 1 -> 0xFF55FF55;
            case 2 -> 0xFFAAFF55;
            case 3 -> 0xFFFFFF55;
            case 4 -> 0xFFFFAA55;
            default -> 0xFFFF5555;
        };
    }
}
