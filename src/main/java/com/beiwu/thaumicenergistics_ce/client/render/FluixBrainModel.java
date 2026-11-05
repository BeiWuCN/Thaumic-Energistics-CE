package com.beiwu.thaumicenergistics_ce.client.render;

import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

/** Textured cuboids shared with the editable Blockbench model; coordinates are Y-up. */
final class FluixBrainModel {
    private record Vertex(Vector3f position, float u, float v) {}
    private record Quad(List<Vertex> vertices, Vector3f normal) {}
    private final List<Quad> quads;
    private final Identifier texture;
    private final Identifier emissive;
    private final float scale;

    FluixBrainModel() {
        try (var reader = Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(ThEIds.id("render_models/fluix_brain.json")).openAsReader()) {
            var data = JsonParser.parseReader(reader).getAsJsonObject();
            texture = Identifier.parse(data.getAsJsonObject("textures").get("cortex").getAsString());
            emissive = Identifier.parse(data.getAsJsonObject("textures").get("emissive").getAsString());
            scale = data.get("scale").getAsFloat();
            var size = data.getAsJsonArray("texture_size");
            float width = size.get(0).getAsFloat(), height = size.get(1).getAsFloat();
            var built = new ArrayList<Quad>();
            for (var value : data.getAsJsonArray("elements")) {
                var element = value.getAsJsonObject();
                var from = vector(element.getAsJsonArray("from"));
                var to = vector(element.getAsJsonArray("to"));
                var origin = element.has("origin") ? vector(element.getAsJsonArray("origin")) : new Vector3f();
                var rotation = element.has("rotation") ? vector(element.getAsJsonArray("rotation")) : new Vector3f();
                rotation.mul((float) (Math.PI / 180.0));
                for (var entry : element.getAsJsonObject("faces").entrySet()) {
                    var direction = Direction.byName(entry.getKey());
                    if (direction == null) throw new IllegalArgumentException("Unknown brain face: " + entry.getKey());
                    var uv = entry.getValue().getAsJsonObject().getAsJsonArray("uv");
                    float u0 = uv.get(0).getAsFloat()/width, v0 = uv.get(1).getAsFloat()/height;
                    float u1 = uv.get(2).getAsFloat()/width, v1 = uv.get(3).getAsFloat()/height;
                    float[][] corners = {{u0,v0},{u0,v1},{u1,v1},{u1,v0}};
                    var vertices = new ArrayList<Vertex>();
                    for (int i=0; i<4; i++) {
                        var position = FaceInfo.fromFacing(direction).getVertexInfo(i).select(from,to);
                        position.sub(origin);
                        rotate(position, rotation).add(origin).div(16);
                        vertices.add(new Vertex(position,corners[i][0],corners[i][1]));
                    }
                    var normal = new Vector3f(direction.getStepX(),direction.getStepY(),direction.getStepZ());
                    built.add(new Quad(List.copyOf(vertices), rotate(normal, rotation)));
                }
            }
            quads = List.copyOf(built);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load Fluix brain model",exception);
        }
    }

    void submit(PoseStack pose, SubmitNodeCollector collector, int light) {
        pose.pushPose();
        pose.scale(scale,scale,scale);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutoutCull(texture),
                (entry,buffer)->render(entry,buffer,light,false));
        collector.submitCustomGeometry(pose, RenderTypes.entityCutoutCull(emissive),
                (entry,buffer)->render(entry,buffer,0x00F000F0,true));
        pose.popPose();
    }

    private void render(PoseStack.Pose pose, VertexConsumer buffer, int light, boolean glow) {
        var normal = new Vector3f();
        for (var quad : quads) {
            pose.transformNormal(quad.normal(),normal);
            // Lift the sparse emissive mask slightly to avoid coplanar flickering.
            float offset = glow ? 0.00015F : 0;
            for (var vertex : quad.vertices()) {
                var p = vertex.position();
                buffer.addVertex(pose,p.x+quad.normal().x*offset,p.y+quad.normal().y*offset,p.z+quad.normal().z*offset)
                        .setColor(255,255,255,255).setUv(vertex.u(),vertex.v())
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(normal.x,normal.y,normal.z);
            }
        }
    }

    private static Vector3f vector(JsonArray array) {
        return new Vector3f(array.get(0).getAsFloat(),array.get(1).getAsFloat(),array.get(2).getAsFloat());
    }

    /** Match the Blockbench cube Euler order and the preview generator. */
    private static Vector3f rotate(Vector3f vector, Vector3f rotation) {
        return vector.rotateX(rotation.x).rotateY(rotation.y).rotateZ(rotation.z);
    }
}
