package net.simdistance.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.simdistance.SimDistance;
import net.simdistance.config.ConfigManager;
import net.simdistance.config.SimDistanceConfig;
import net.trarncore.render.Shapes;
import org.joml.Matrix4f;

/**
 * Draws a tall, translucent red wall on the chunk borders at the edge of the
 * simulation-distance region around the player. Anchored to the player's current
 * chunk, so it snaps by 16 blocks as you cross chunk boundaries.
 */
public class SimDistanceRenderer {

    /** Matches what this renderer has always used. */
    private static final float LINE_WIDTH = 2.5f;

    private static long lastRenderError = 0;

    public static void register() {
        WorldRenderEvents.END_MAIN.register(SimDistanceRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        try {
            renderInternal(context);
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            if (now - lastRenderError > 5000) {
                lastRenderError = now;
                SimDistance.LOGGER.error("[SimDistance] Border render crashed (suppressing repeats for 5s)", e);
            }
        }
    }

    private static void renderInternal(WorldRenderContext context) {
        if (!SimDistance.enabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        SimDistanceConfig cfg = ConfigManager.get();
        ClientWorld world = client.world;

        // ── Chunk radius: live simulation distance (falls back to the manual value) ──
        int radius;
        if (cfg.useServerSimulationDistance) {
            int sd = world.getSimulationDistance();
            radius = sd > 0 ? sd : cfg.manualChunkRadius;
        } else {
            radius = cfg.manualChunkRadius;
        }
        radius = Math.max(1, radius);

        // ── Region bounds, snapped to chunk borders around the player's chunk ──
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();
        int pcx = (int) Math.floor(px) >> 4;
        int pcz = (int) Math.floor(pz) >> 4;

        float minX = (pcx - radius) << 4;
        float maxX = (pcx + radius + 1) << 4;
        float minZ = (pcz - radius) << 4;
        float maxZ = (pcz + radius + 1) << 4;

        float y0, y1;
        if (cfg.fullWorldHeight) {
            y0 = world.getBottomY();
            y1 = world.getTopYInclusive() + 1;
        } else {
            int vr = Math.max(1, cfg.verticalRadius);
            y0 = (float) (py - vr);
            y1 = (float) (py + vr);
        }

        int c = cfg.wallColor;
        float r = ((c >> 16) & 0xFF) / 255f;
        float g = ((c >> 8) & 0xFF) / 255f;
        float b = (c & 0xFF) / 255f;
        float a = Math.max(0, Math.min(100, cfg.wallOpacity)) / 100f;

        Vec3d cam = client.gameRenderer.getCamera().getCameraPos();
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        // ── Translucent red walls ───────────────────────────────────────────────
        RenderLayer quadLayer = RenderLayers.debugQuads();
        VertexConsumer q = consumers.getBuffer(quadLayer);
        addWallAlongX(q, mat, minX, maxX, minZ, y0, y1, r, g, b, a); // north
        addWallAlongX(q, mat, minX, maxX, maxZ, y0, y1, r, g, b, a); // south
        addWallAlongZ(q, mat, minZ, maxZ, minX, y0, y1, r, g, b, a); // west
        addWallAlongZ(q, mat, minZ, maxZ, maxX, y0, y1, r, g, b, a); // east

        // ── Crisp outline along the top/bottom edges and vertical corners ─────────
        boolean lines = cfg.drawEdgeLines || cfg.showChunkGrid;
        if (lines) {
            VertexConsumer l = consumers.getBuffer(RenderLayers.LINES);
            if (cfg.drawEdgeLines) {
                addRect(l, mat, minX, maxX, minZ, maxZ, y0, r, g, b); // bottom
                addRect(l, mat, minX, maxX, minZ, maxZ, y1, r, g, b); // top
                addLine(l, mat, minX, y0, minZ, minX, y1, minZ, r, g, b);
                addLine(l, mat, maxX, y0, minZ, maxX, y1, minZ, r, g, b);
                addLine(l, mat, minX, y0, maxZ, minX, y1, maxZ, r, g, b);
                addLine(l, mat, maxX, y0, maxZ, maxX, y1, maxZ, r, g, b);
            }
            if (cfg.showChunkGrid) {
                float gy = (float) py;
                for (float x = minX; x <= maxX; x += 16f) {
                    addLine(l, mat, x, gy, minZ, x, gy, maxZ, r, g, b);
                }
                for (float z = minZ; z <= maxZ; z += 16f) {
                    addLine(l, mat, minX, gy, z, maxX, gy, z, r, g, b);
                }
            }
        }

        matrices.pop();

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(quadLayer);
            if (lines) imm.draw(RenderLayers.LINES);
        }
    }

    /** Quad in the plane z = const, spanning x1..x2 vertically from y0 to y1. */
    private static void addWallAlongX(VertexConsumer vc, Matrix4f mat,
                                      float x1, float x2, float z, float y0, float y1,
                                      float r, float g, float b, float a) {
        Shapes.wallAlongX(vc, mat, x1, x2, z, y0, y1, r, g, b, a);
    }
    /** Quad in the plane x = const, spanning z1..z2 vertically from y0 to y1. */
    private static void addWallAlongZ(VertexConsumer vc, Matrix4f mat,
                                      float z1, float z2, float x, float y0, float y1,
                                      float r, float g, float b, float a) {
        Shapes.wallAlongZ(vc, mat, z1, z2, x, y0, y1, r, g, b, a);
    }
    /** Axis-aligned rectangle outline at a fixed y. */
    private static void addRect(VertexConsumer vc, Matrix4f mat,
                                float x1, float x2, float z1, float z2, float y,
                                float r, float g, float b) {
        Shapes.rectXZ(vc, mat, x1, x2, z1, z2, y, r, g, b, 1f, LINE_WIDTH);
    }
    private static void addLine(VertexConsumer vc, Matrix4f mat,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float r, float g, float b) {
        Shapes.line(vc, mat, x1, y1, z1, x2, y2, z2, r, g, b, 1f, LINE_WIDTH);
    }}
