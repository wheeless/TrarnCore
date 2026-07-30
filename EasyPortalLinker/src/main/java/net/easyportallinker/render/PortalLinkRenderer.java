package net.easyportallinker.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.easyportallinker.EasyPortalLinker;
import net.easyportallinker.config.ConfigManager;
import net.easyportallinker.config.EasyPortalLinkerConfig;
import net.easyportallinker.portal.LinkMath;
import net.easyportallinker.portal.PortalTarget;
import org.joml.Matrix4f;

/**
 * The in-world guide.
 *
 * <p>In the destination dimension it draws a full-height column at the target X/Z and an
 * axis-matched ghost outline of the exact portal, anchored to the player's current Y (so its base
 * sits at your feet and follows you up and down — the buried "recommended Y" is useless in the
 * dense Nether). In the source dimension it outlines the portal you selected.
 *
 * <p><b>Rendering note:</b> Minecraft's immediate {@code VertexConsumerProvider} backs both the
 * translucent-quad layer and the line layer with the same fallback buffer, so the two must never
 * be written interleaved. We render in two clean passes — every fill first, then every line.
 */
public class PortalLinkRenderer {

    private static long lastRenderError = 0;

    public static void register() {
        WorldRenderEvents.END_MAIN.register(PortalLinkRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        try {
            renderInternal(context);
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            if (now - lastRenderError > 5000) {
                lastRenderError = now;
                EasyPortalLinker.LOGGER.error("[EasyPortalLinker] Guide render crashed (suppressing repeats for 5s)", e);
            }
        }
    }

    private static void renderInternal(WorldRenderContext context) {
        if (!EasyPortalLinker.enabled) return;
        PortalTarget sel = EasyPortalLinker.selection;
        if (sel == null || sel.axis == null || sel.sourceDim == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        ClientWorld world = client.world;
        String dim = world.getRegistryKey().getValue().toString();
        EasyPortalLinkerConfig cfg = ConfigManager.get();

        boolean inDest = sel.isDestDim(dim);
        boolean inSource = sel.isSourceDim(dim) && cfg.showSourceHighlight;
        if (!inDest && !inSource) return;

        Camera camera = client.gameRenderer.getCamera();
        Vec3d cam = camera.getCameraPos();
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        double playerY = client.player.getY();

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        // ── Pass 1: all translucent fills ───────────────────────────────────────
        VertexConsumer q = consumers.getBuffer(RenderLayers.debugQuads());
        if (inDest) fillTarget(cfg, world, sel, playerY, q, mat);
        else fillSource(cfg, sel, q, mat);

        // ── Pass 2: all lines (fetch the line buffer only after every fill) ──────
        VertexConsumer l = consumers.getBuffer(RenderLayers.LINES);
        if (inDest) lineTarget(cfg, world, sel, playerY, l, mat);
        else lineSource(cfg, sel, l, mat);

        matrices.pop();

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(RenderLayers.debugQuads());
            imm.draw(RenderLayers.LINES);
        }

        // ── Floating coordinate labels (own matrix pushes, SEE_THROUGH layer) ───
        if (cfg.showFloatingCoords) {
            if (inDest) drawTargetLabel(cfg, client, matrices, consumers, camera, cam, world, sel, playerY);
            else drawSourceLabel(client, matrices, consumers, camera, cam, sel);
        }
    }

    // ── Destination fills / lines ──────────────────────────────────────────────

    private static void fillTarget(EasyPortalLinkerConfig cfg, ClientWorld world, PortalTarget sel,
                                   double playerY, VertexConsumer q, Matrix4f mat) {
        Direction.Axis axis = sel.axisEnum();
        int width = Math.max(2, sel.width);
        int height = Math.max(3, sel.height);
        int ax = sel.destX, az = sel.destZ;
        int ry = frameBaseY(cfg, world, playerY, height);

        float[] tc = rgb(cfg.targetColor);
        float ta = opacity(cfg.targetOpacity);
        float[] fc = { tc[0] * 0.5f, tc[1] * 0.5f, tc[2] * 0.5f };

        if (cfg.showColumn) {
            float[] fp = columnFootprint(axis, ax, az, width);
            addColumnWalls(q, mat, fp[0], fp[1], fp[2], fp[3],
                world.getBottomY(), world.getTopYInclusive() + 1, tc, ta);
        }
        if (cfg.showGhostFrame) {
            for (int i = 0; i < width; i++)
                for (int j = 0; j < height; j++)
                    addCubeFill(q, mat, cell(axis, ax, ry, az, i, j), tc, ta);
            for (int i = -1; i <= width; i++) {
                addCubeFill(q, mat, cell(axis, ax, ry, az, i, -1), fc, ta * 0.5f);
                addCubeFill(q, mat, cell(axis, ax, ry, az, i, height), fc, ta * 0.5f);
            }
            for (int j = 0; j < height; j++) {
                addCubeFill(q, mat, cell(axis, ax, ry, az, -1, j), fc, ta * 0.5f);
                addCubeFill(q, mat, cell(axis, ax, ry, az, width, j), fc, ta * 0.5f);
            }
        }
    }

    private static void lineTarget(EasyPortalLinkerConfig cfg, ClientWorld world, PortalTarget sel,
                                   double playerY, VertexConsumer l, Matrix4f mat) {
        Direction.Axis axis = sel.axisEnum();
        int width = Math.max(2, sel.width);
        int height = Math.max(3, sel.height);
        int ax = sel.destX, az = sel.destZ;
        int ry = frameBaseY(cfg, world, playerY, height);

        float[] tc = rgb(cfg.targetColor);
        float[] fc = { tc[0] * 0.6f, tc[1] * 0.6f, tc[2] * 0.6f };

        if (cfg.showColumn && cfg.drawEdgeLines) {
            float[] fp = columnFootprint(axis, ax, az, width);
            float x0 = fp[0], x1 = fp[1], z0 = fp[2], z1 = fp[3];
            float y0 = world.getBottomY(), y1 = world.getTopYInclusive() + 1;
            addLine(l, mat, x0, y0, z0, x0, y1, z0, tc);
            addLine(l, mat, x1, y0, z0, x1, y1, z0, tc);
            addLine(l, mat, x0, y0, z1, x0, y1, z1, tc);
            addLine(l, mat, x1, y0, z1, x1, y1, z1, tc);
            addRectXZ(l, mat, x0, x1, z0, z1, ry - 1, tc);
            addRectXZ(l, mat, x0, x1, z0, z1, ry + height + 1, tc);
        }
        if (cfg.showGhostFrame) {
            for (int i = 0; i < width; i++)
                for (int j = 0; j < height; j++)
                    addCubeOutline(l, mat, cell(axis, ax, ry, az, i, j), tc);
            for (int i = -1; i <= width; i++) {
                addCubeOutline(l, mat, cell(axis, ax, ry, az, i, -1), fc);
                addCubeOutline(l, mat, cell(axis, ax, ry, az, i, height), fc);
            }
            for (int j = 0; j < height; j++) {
                addCubeOutline(l, mat, cell(axis, ax, ry, az, -1, j), fc);
                addCubeOutline(l, mat, cell(axis, ax, ry, az, width, j), fc);
            }
        }
    }

    // ── Source fills / lines ────────────────────────────────────────────────────

    private static void fillSource(EasyPortalLinkerConfig cfg, PortalTarget sel,
                                   VertexConsumer q, Matrix4f mat) {
        Direction.Axis axis = sel.axisEnum();
        int width = Math.max(2, sel.width);
        int height = Math.max(3, sel.height);
        float[] sc = rgb(cfg.sourceColor);
        float sa = opacity(cfg.sourceOpacity);
        for (int i = 0; i < width; i++)
            for (int j = 0; j < height; j++)
                addCubeFill(q, mat, cell(axis, sel.sourceX, sel.sourceY, sel.sourceZ, i, j), sc, sa);
    }

    private static void lineSource(EasyPortalLinkerConfig cfg, PortalTarget sel,
                                   VertexConsumer l, Matrix4f mat) {
        Direction.Axis axis = sel.axisEnum();
        int width = Math.max(2, sel.width);
        int height = Math.max(3, sel.height);
        float[] sc = rgb(cfg.sourceColor);
        for (int i = 0; i < width; i++)
            for (int j = 0; j < height; j++)
                addCubeOutline(l, mat, cell(axis, sel.sourceX, sel.sourceY, sel.sourceZ, i, j), sc);
    }

    // ── Floating labels ────────────────────────────────────────────────────────

    private static void drawTargetLabel(EasyPortalLinkerConfig cfg, MinecraftClient client, MatrixStack matrices,
                                        VertexConsumerProvider consumers, Camera camera, Vec3d cam,
                                        ClientWorld world, PortalTarget sel, double playerY) {
        Direction.Axis axis = sel.axisEnum();
        int width = Math.max(2, sel.width);
        int height = Math.max(3, sel.height);
        int ry = frameBaseY(cfg, world, playerY, height);
        int idealY = LinkMath.recommendedY(sel.destDim, sel.sourceY, height);

        double cx, cz;
        if (axis == Direction.Axis.X) { cx = sel.destX + width / 2.0; cz = sel.destZ + 0.5; }
        else                          { cx = sel.destX + 0.5;          cz = sel.destZ + width / 2.0; }
        double ly = ry + height + 1.6;

        String third = cfg.lockTargetY
            ? "Axis " + sel.axis + "   -   Y " + ry + " (locked)"
            : "Axis " + sel.axis + "   -   any Y links (ideal " + idealY + ")";
        String[] lines = {
            "Build portal HERE",
            "X " + sel.destX + "     Z " + sel.destZ,
            third
        };
        drawBillboard(client, matrices, consumers, camera, cam, cx, ly, cz, lines, 0xFFFFFFFF);
    }

    private static void drawSourceLabel(MinecraftClient client, MatrixStack matrices,
                                        VertexConsumerProvider consumers, Camera camera, Vec3d cam,
                                        PortalTarget sel) {
        Direction.Axis axis = sel.axisEnum();
        int width = Math.max(2, sel.width);
        int height = Math.max(3, sel.height);

        double cx, cz;
        if (axis == Direction.Axis.X) { cx = sel.sourceX + width / 2.0; cz = sel.sourceZ + 0.5; }
        else                          { cx = sel.sourceX + 0.5;          cz = sel.sourceZ + width / 2.0; }
        double ly = sel.sourceY + height + 1.4;

        String second;
        if (sel.hasDestination()) {
            second = "Links to " + LinkMath.dimLabel(sel.destDim)
                + "   X " + sel.destX + "   Z " + sel.destZ;
        } else {
            second = "No counterpart (unsupported dimension)";
        }
        drawBillboard(client, matrices, consumers, camera, cam, cx, ly, cz,
            new String[]{ "Selected portal", second }, 0xFFFFFFFF);
    }

    private static void drawBillboard(MinecraftClient client, MatrixStack matrices,
                                      VertexConsumerProvider consumers, Camera camera, Vec3d cam,
                                      double wx, double wy, double wz, String[] lines, int color) {
        TextRenderer tr = client.textRenderer;
        matrices.push();
        matrices.translate(wx - cam.x, wy - cam.y, wz - cam.z);
        matrices.multiply(camera.getRotation());
        matrices.scale(0.025f, -0.025f, 0.025f);
        Matrix4f m = matrices.peek().getPositionMatrix();

        int lineH = 10;
        for (int k = 0; k < lines.length; k++) {
            String line = lines[k];
            int tw = tr.getWidth(line);
            tr.draw(line, -tw / 2f, k * lineH, color, false, m, consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, 0x90000000,
                LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }
        matrices.pop();
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    /**
     * Ghost-frame interior-bottom Y: the locked value when {@link EasyPortalLinkerConfig#lockTargetY}
     * is on, otherwise the player's feet. Always clamped to the destination world's build range.
     */
    private static int frameBaseY(EasyPortalLinkerConfig cfg, ClientWorld world, double playerY, int height) {
        int ry = cfg.lockTargetY ? cfg.lockedTargetY : MathHelper.floor(playerY);
        int lo = world.getBottomY() + 2;
        int hi = world.getTopYInclusive() - height;
        if (hi < lo) hi = lo;
        return Math.max(lo, Math.min(hi, ry));
    }

    /** {x0,x1,z0,z1} of the full structure footprint (interior widened by the frame). */
    private static float[] columnFootprint(Direction.Axis axis, int ax, int az, int width) {
        if (axis == Direction.Axis.X) return new float[]{ ax - 1, ax + width + 1, az, az + 1 };
        return new float[]{ ax, ax + 1, az - 1, az + width + 1 };
    }

    /** Interior/frame cell at grid offset (i along axis, j vertical) as {x,y,z} block coords. */
    private static int[] cell(Direction.Axis axis, int ax, int ay, int az, int i, int j) {
        if (axis == Direction.Axis.X) return new int[]{ ax + i, ay + j, az };
        return new int[]{ ax, ay + j, az + i };
    }

    private static void addCubeFill(VertexConsumer q, Matrix4f mat, int[] c, float[] col, float a) {
        float x0 = c[0], x1 = c[0] + 1, y0 = c[1], y1 = c[1] + 1, z0 = c[2], z1 = c[2] + 1;
        addWallAlongX(q, mat, x0, x1, z0, y0, y1, col, a);
        addWallAlongX(q, mat, x0, x1, z1, y0, y1, col, a);
        addWallAlongZ(q, mat, z0, z1, x0, y0, y1, col, a);
        addWallAlongZ(q, mat, z0, z1, x1, y0, y1, col, a);
        addQuadY(q, mat, x0, x1, z0, z1, y0, col, a);
        addQuadY(q, mat, x0, x1, z0, z1, y1, col, a);
    }

    private static void addCubeOutline(VertexConsumer l, Matrix4f mat, int[] c, float[] col) {
        addBoxEdges(l, mat, c[0], c[1], c[2], c[0] + 1, c[1] + 1, c[2] + 1, col);
    }

    private static void addColumnWalls(VertexConsumer q, Matrix4f mat,
                                       float x0, float x1, float z0, float z1, float y0, float y1,
                                       float[] c, float a) {
        addWallAlongX(q, mat, x0, x1, z0, y0, y1, c, a);
        addWallAlongX(q, mat, x0, x1, z1, y0, y1, c, a);
        addWallAlongZ(q, mat, z0, z1, x0, y0, y1, c, a);
        addWallAlongZ(q, mat, z0, z1, x1, y0, y1, c, a);
    }

    private static void addWallAlongX(VertexConsumer vc, Matrix4f mat,
                                      float x1, float x2, float z, float y0, float y1,
                                      float[] c, float a) {
        vc.vertex(mat, x1, y0, z).color(c[0], c[1], c[2], a);
        vc.vertex(mat, x2, y0, z).color(c[0], c[1], c[2], a);
        vc.vertex(mat, x2, y1, z).color(c[0], c[1], c[2], a);
        vc.vertex(mat, x1, y1, z).color(c[0], c[1], c[2], a);
    }

    private static void addWallAlongZ(VertexConsumer vc, Matrix4f mat,
                                      float z1, float z2, float x, float y0, float y1,
                                      float[] c, float a) {
        vc.vertex(mat, x, y0, z1).color(c[0], c[1], c[2], a);
        vc.vertex(mat, x, y0, z2).color(c[0], c[1], c[2], a);
        vc.vertex(mat, x, y1, z2).color(c[0], c[1], c[2], a);
        vc.vertex(mat, x, y1, z1).color(c[0], c[1], c[2], a);
    }

    private static void addQuadY(VertexConsumer vc, Matrix4f mat,
                                 float x0, float x1, float z0, float z1, float y,
                                 float[] c, float a) {
        vc.vertex(mat, x0, y, z0).color(c[0], c[1], c[2], a);
        vc.vertex(mat, x1, y, z0).color(c[0], c[1], c[2], a);
        vc.vertex(mat, x1, y, z1).color(c[0], c[1], c[2], a);
        vc.vertex(mat, x0, y, z1).color(c[0], c[1], c[2], a);
    }

    private static void addRectXZ(VertexConsumer l, Matrix4f mat,
                                  float x0, float x1, float z0, float z1, float y, float[] c) {
        addLine(l, mat, x0, y, z0, x1, y, z0, c);
        addLine(l, mat, x1, y, z0, x1, y, z1, c);
        addLine(l, mat, x1, y, z1, x0, y, z1, c);
        addLine(l, mat, x0, y, z1, x0, y, z0, c);
    }

    private static void addBoxEdges(VertexConsumer l, Matrix4f mat,
                                    float x0, float y0, float z0, float x1, float y1, float z1,
                                    float[] c) {
        addLine(l, mat, x0, y0, z0, x1, y0, z0, c);
        addLine(l, mat, x1, y0, z0, x1, y0, z1, c);
        addLine(l, mat, x1, y0, z1, x0, y0, z1, c);
        addLine(l, mat, x0, y0, z1, x0, y0, z0, c);
        addLine(l, mat, x0, y1, z0, x1, y1, z0, c);
        addLine(l, mat, x1, y1, z0, x1, y1, z1, c);
        addLine(l, mat, x1, y1, z1, x0, y1, z1, c);
        addLine(l, mat, x0, y1, z1, x0, y1, z0, c);
        addLine(l, mat, x0, y0, z0, x0, y1, z0, c);
        addLine(l, mat, x1, y0, z0, x1, y1, z0, c);
        addLine(l, mat, x0, y0, z1, x0, y1, z1, c);
        addLine(l, mat, x1, y0, z1, x1, y1, z1, c);
    }

    private static void addLine(VertexConsumer vc, Matrix4f mat,
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                float[] c) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0) { dx /= len; dy /= len; dz /= len; }
        vc.vertex(mat, x1, y1, z1).color(c[0], c[1], c[2], 1f).normal(dx, dy, dz).lineWidth(2.5f);
        vc.vertex(mat, x2, y2, z2).color(c[0], c[1], c[2], 1f).normal(dx, dy, dz).lineWidth(2.5f);
    }

    private static float[] rgb(int c) {
        return new float[]{ ((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f };
    }

    private static float opacity(int pct) {
        return Math.max(0, Math.min(100, pct)) / 100f;
    }
}
