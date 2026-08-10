package net.easyportallinker.render;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
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
 * <p><b>Rendering note:</b> Minecraft's immediate {@code MultiBufferSource} backs both the
 * translucent-quad layer and the line layer with the same fallback buffer, so the two must never
 * be written interleaved. We render in two clean passes — every fill first, then every line.
 */
public class PortalLinkRenderer {

    private static long lastRenderError = 0;

    public static void register() {
        LevelRenderEvents.END_MAIN.register(PortalLinkRenderer::render);
    }

    private static void render(LevelRenderContext context) {
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

    private static void renderInternal(LevelRenderContext context) {
        if (!EasyPortalLinker.enabled) return;
        PortalTarget sel = EasyPortalLinker.selection;
        if (sel == null || sel.axis == null || sel.sourceDim == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        ClientLevel world = client.level;
        String dim = world.dimension().identifier().toString();
        EasyPortalLinkerConfig cfg = ConfigManager.get();

        boolean inDest = sel.isDestDim(dim);
        boolean inSource = sel.isSourceDim(dim) && cfg.showSourceHighlight;
        if (!inDest && !inSource) return;

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        PoseStack matrices = context.poseStack();
        MultiBufferSource consumers = context.bufferSource();
        if (consumers == null) return;

        double playerY = client.player.getY();

        matrices.pushPose();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = matrices.last().pose();

        // ── Pass 1: all translucent fills ───────────────────────────────────────
        VertexConsumer q = consumers.getBuffer(RenderTypes.debugQuads());
        if (inDest) fillTarget(cfg, world, sel, playerY, q, mat);
        else fillSource(cfg, sel, q, mat);

        // ── Pass 2: all lines (fetch the line buffer only after every fill) ──────
        VertexConsumer l = consumers.getBuffer(RenderTypes.LINES);
        if (inDest) lineTarget(cfg, world, sel, playerY, l, mat);
        else lineSource(cfg, sel, l, mat);

        matrices.popPose();

        if (consumers instanceof MultiBufferSource.BufferSource imm) {
            imm.endBatch(RenderTypes.debugQuads());
            imm.endBatch(RenderTypes.LINES);
        }

        // ── Floating coordinate labels (own matrix pushes, SEE_THROUGH layer) ───
        if (cfg.showFloatingCoords) {
            if (inDest) drawTargetLabel(cfg, client, matrices, consumers, camera, cam, world, sel, playerY);
            else drawSourceLabel(client, matrices, consumers, camera, cam, sel);

            // Flush while the world transform that billboarded the labels is still in effect.
            // Font picks its own layers internally so they cannot be ended by name — hence a
            // blanket flush. Leaving them for the level renderer to drain is what made the
            // labels swing around their anchor as the camera turned.
            if (consumers instanceof MultiBufferSource.BufferSource imm) {
                imm.endBatch();
            }
        }
    }

    // ── Destination fills / lines ──────────────────────────────────────────────

    private static void fillTarget(EasyPortalLinkerConfig cfg, ClientLevel world, PortalTarget sel,
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
                world.getMinY(), world.getMaxY() + 1, tc, ta);
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

    private static void lineTarget(EasyPortalLinkerConfig cfg, ClientLevel world, PortalTarget sel,
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
            float y0 = world.getMinY(), y1 = world.getMaxY() + 1;
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

    private static void drawTargetLabel(EasyPortalLinkerConfig cfg, Minecraft client, PoseStack matrices,
                                        MultiBufferSource consumers, Camera camera, Vec3 cam,
                                        ClientLevel world, PortalTarget sel, double playerY) {
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

    private static void drawSourceLabel(Minecraft client, PoseStack matrices,
                                        MultiBufferSource consumers, Camera camera, Vec3 cam,
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

    private static void drawBillboard(Minecraft client, PoseStack matrices,
                                      MultiBufferSource consumers, Camera camera, Vec3 cam,
                                      double wx, double wy, double wz, String[] lines, int color) {
        Font tr = client.font;
        matrices.pushPose();
        matrices.translate(wx - cam.x, wy - cam.y, wz - cam.z);
        matrices.mulPose(camera.rotation());
        matrices.scale(0.025f, -0.025f, 0.025f);
        Matrix4f m = matrices.last().pose();

        int lineH = 10;
        for (int k = 0; k < lines.length; k++) {
            String line = lines[k];
            int tw = tr.width(line);
            tr.drawInBatch(line, -tw / 2f, k * lineH, color, false, m, consumers,
                Font.DisplayMode.SEE_THROUGH, 0x90000000,
                0xF000F0);
        }
        matrices.popPose();
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    /**
     * Ghost-frame interior-bottom Y: the locked value when {@link EasyPortalLinkerConfig#lockTargetY}
     * is on, otherwise the player's feet. Always clamped to the destination world's build range.
     */
    private static int frameBaseY(EasyPortalLinkerConfig cfg, ClientLevel world, double playerY, int height) {
        int ry = cfg.lockTargetY ? cfg.lockedTargetY : Mth.floor(playerY);
        int lo = world.getMinY() + 2;
        int hi = world.getMaxY() - height;
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
        vc.addVertex(mat, x1, y0, z).setColor(c[0], c[1], c[2], a);
        vc.addVertex(mat, x2, y0, z).setColor(c[0], c[1], c[2], a);
        vc.addVertex(mat, x2, y1, z).setColor(c[0], c[1], c[2], a);
        vc.addVertex(mat, x1, y1, z).setColor(c[0], c[1], c[2], a);
    }

    private static void addWallAlongZ(VertexConsumer vc, Matrix4f mat,
                                      float z1, float z2, float x, float y0, float y1,
                                      float[] c, float a) {
        vc.addVertex(mat, x, y0, z1).setColor(c[0], c[1], c[2], a);
        vc.addVertex(mat, x, y0, z2).setColor(c[0], c[1], c[2], a);
        vc.addVertex(mat, x, y1, z2).setColor(c[0], c[1], c[2], a);
        vc.addVertex(mat, x, y1, z1).setColor(c[0], c[1], c[2], a);
    }

    private static void addQuadY(VertexConsumer vc, Matrix4f mat,
                                 float x0, float x1, float z0, float z1, float y,
                                 float[] c, float a) {
        vc.addVertex(mat, x0, y, z0).setColor(c[0], c[1], c[2], a);
        vc.addVertex(mat, x1, y, z0).setColor(c[0], c[1], c[2], a);
        vc.addVertex(mat, x1, y, z1).setColor(c[0], c[1], c[2], a);
        vc.addVertex(mat, x0, y, z1).setColor(c[0], c[1], c[2], a);
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
        vc.addVertex(mat, x1, y1, z1).setColor(c[0], c[1], c[2], 1f).setNormal(dx, dy, dz).setLineWidth(2.5f);
        vc.addVertex(mat, x2, y2, z2).setColor(c[0], c[1], c[2], 1f).setNormal(dx, dy, dz).setLineWidth(2.5f);
    }

    private static float[] rgb(int c) {
        return new float[]{ ((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f };
    }

    private static float opacity(int pct) {
        return Math.max(0, Math.min(100, pct)) / 100f;
    }
}
