package net.easyportallinker.render;

import net.easyportallinker.EasyPortalLinker;
import net.easyportallinker.config.ConfigManager;
import net.easyportallinker.config.EasyPortalLinkerConfig;
import net.easyportallinker.portal.PortalSighting;
import net.easyportallinker.portal.PortalTarget;
import net.easyportallinker.scan.PortalFinder;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.trarncore.render.Layers;
import net.trarncore.render.Shapes;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights every nether portal in range, the way ContainerUtil highlights containers.
 *
 * <p>Lit portals and complete-but-unlit frames get different colours, because the difference is
 * the whole point of showing them: one is a working link, the other is a frame somebody never
 * struck.
 *
 * <p><b>Rendering note:</b> the immediate {@code MultiBufferSource} backs the translucent-quad and
 * line layers with the same fallback buffer, so the two must never be interleaved. Geometry is
 * resolved up front and emitted in two clean passes — every fill, flush, then every line.
 */
public class PortalEspRenderer {

    /** Resolved geometry and colour for one portal, shared by the fill and outline passes. */
    private record DrawJob(AABB box, float r, float g, float b, float fillAlpha) {
    }

    private static long lastRenderError = 0;

    public static void register() {
        LevelRenderEvents.END_MAIN.register(PortalEspRenderer::render);
    }

    private static void render(LevelRenderContext context) {
        try {
            renderInternal(context);
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            if (now - lastRenderError > 5000) {
                lastRenderError = now;
                EasyPortalLinker.LOGGER.error("[EasyPortalLinker] Portal ESP render crashed (suppressing repeats for 5s)", e);
            }
        }
    }

    private static void renderInternal(LevelRenderContext context) {
        if (!EasyPortalLinker.portalEsp) return;

        List<PortalSighting> sightings = PortalFinder.sightings();
        if (sightings.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        MultiBufferSource consumers = context.bufferSource();
        if (consumers == null) return;

        EasyPortalLinkerConfig config = ConfigManager.get();

        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        // Horizontal, like ContainerUtil's: a chunk radius is a column, so a portal at bedrock two
        // chunks away is exactly as loaded as one at your feet. Height only matters if asked for.
        double range = config.portalEspChunkRadius * 16.0;
        double rangeSq = range * range;
        double verticalLimitSq = (double) config.portalEspVerticalLimit * config.portalEspVerticalLimit;

        AABB selected = selectedPortalBox();

        List<DrawJob> jobs = new ArrayList<>();
        for (PortalSighting sighting : sightings) {
            if (sighting.horizontalDistanceSqTo(px, pz) > rangeSq) continue;
            if (config.portalEspVerticalLimit > 0) {
                double dy = sighting.centerY() - py;
                if (dy * dy > verticalLimitSq) continue;
            }
            // The selected portal already has its own teal highlight; drawing both just z-fights.
            if (selected != null && selected.intersects(sighting.box())) continue;

            int rgb = sighting.lit() ? config.portalEspColor : config.unlitFrameColor;
            int opacity = sighting.lit() ? config.portalEspOpacity : config.unlitFrameOpacity;

            jobs.add(new DrawJob(
                sighting.box().inflate(0.01),  // lift off the block faces so the fill does not z-fight
                Shapes.red(rgb), Shapes.green(rgb), Shapes.blue(rgb),
                Math.clamp(opacity, 0, 100) / 100f));
        }
        if (jobs.isEmpty()) return;

        if (jobs.size() > config.maxRenderedPortals) {
            jobs.sort((a, b) -> Double.compare(
                boxDistanceSq(a.box(), px, py, pz), boxDistanceSq(b.box(), px, py, pz)));
            jobs = jobs.subList(0, config.maxRenderedPortals);
        }

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        PoseStack matrices = context.poseStack();

        RenderType quadLayer = Layers.quads(config.portalEspSeeThrough);
        RenderType lineLayer = Layers.lines(config.portalEspSeeThrough);

        matrices.pushPose();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = matrices.last().pose();

        // ── Pass 1: fills ────────────────────────────────────────────────────
        if (config.portalEspFill) {
            VertexConsumer quads = consumers.getBuffer(quadLayer);
            for (DrawJob job : jobs) {
                if (job.fillAlpha() > 0f) {
                    Shapes.fillBox(quads, mat, job.box(), job.r(), job.g(), job.b(), job.fillAlpha());
                }
            }
            if (consumers instanceof MultiBufferSource.BufferSource imm) {
                imm.endBatch(quadLayer);
            }
        }

        // ── Pass 2: outlines (line buffer fetched only after every fill) ─────
        if (config.portalEspOutline) {
            VertexConsumer lines = consumers.getBuffer(lineLayer);
            for (DrawJob job : jobs) {
                Shapes.outlineBox(lines, mat, job.box(), job.r(), job.g(), job.b(), 1f,
                    config.portalEspOutlineWidth);
            }
            if (consumers instanceof MultiBufferSource.BufferSource imm) {
                imm.endBatch(lineLayer);
            }
        }

        matrices.popPose();
    }

    /** Bounds of the currently selected portal in this dimension, or null. */
    private static AABB selectedPortalBox() {
        PortalTarget selection = EasyPortalLinker.selection;
        if (selection == null || !ConfigManager.get().showSourceHighlight) return null;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;
        if (!selection.isSourceDim(client.level.dimension().identifier().toString())) return null;

        int width = Math.max(2, selection.width);
        int height = Math.max(3, selection.height);
        boolean alongX = selection.axisEnum() == net.minecraft.core.Direction.Axis.X;

        return new AABB(
            selection.sourceX, selection.sourceY, selection.sourceZ,
            selection.sourceX + (alongX ? width : 1),
            selection.sourceY + height,
            selection.sourceZ + (alongX ? 1 : width));
    }

    private static double boxDistanceSq(AABB box, double x, double y, double z) {
        double dx = (box.minX + box.maxX) / 2.0 - x;
        double dy = (box.minY + box.maxY) / 2.0 - y;
        double dz = (box.minZ + box.maxZ) / 2.0 - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
