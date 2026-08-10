package net.containerutil.render;

import net.containerutil.ContainerUtil;
import net.containerutil.config.ConfigManager;
import net.containerutil.config.ContainerUtilConfig;
import net.containerutil.container.ContainerKind;
import net.containerutil.data.ContainerRecord;
import net.containerutil.data.IndexManager;
import net.containerutil.data.WorldIdentity;
import net.containerutil.search.SearchHighlight;
import net.trarncore.render.Layers;
import net.trarncore.render.Shapes;
import net.trarncore.render.WorldText;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the coloured boxes over every known container in range.
 *
 * <p>Two things keep this cheap on a base with thousands of containers: a squared-distance
 * cull against the configured chunk radius, and a hard cap that keeps only the nearest
 * {@code maxRenderedContainers}. Sorting the survivors is O(n log n) on a list that has
 * already been filtered down, not on the whole index.
 */
public class ContainerEspRenderer {

    /** Draw distance for labels is short, so gather them during the box pass and emit them after. */
    private record PendingLabel(double x, double y, double z, Component text, int color) {
    }

    /** Resolved geometry and colour for one container, shared by the fill and outline passes. */
    private record DrawJob(AABB box, float r, float g, float b, float fillAlpha, float outlineAlpha) {
    }

    private static long lastRenderError = 0;

    public static void register() {
        LevelRenderEvents.END_MAIN.register(ContainerEspRenderer::render);
    }

    private static void render(LevelRenderContext context) {
        try {
            renderInternal(context);
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            if (now - lastRenderError > 5000) {
                lastRenderError = now;
                ContainerUtil.LOGGER.error("[ContainerUtil] Container render crashed (suppressing repeats for 5s)", e);
            }
        }
    }

    private static void renderInternal(LevelRenderContext context) {
        if (!ContainerUtil.enabled) return;
        if (!IndexManager.isActive()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        MultiBufferSource consumers = context.bufferSource();
        if (consumers == null) return;

        ContainerUtilConfig config = ConfigManager.get();
        ClientLevel world = client.level;
        String dim = WorldIdentity.currentDimension();
        if (dim == null) return;

        // Measured from the player or the camera depending on the anchor setting — see ViewAnchor.
        Vec3 anchor = ViewAnchor.origin(client);
        double px = anchor.x;
        double py = anchor.y;
        double pz = anchor.z;

        // The chunk radius is horizontal, because that is what a chunk radius means: chunks load
        // as full columns, so a chest at bedrock two chunks away is exactly as loaded as one at
        // your feet. Vertical distance is only considered if the player explicitly asks for a
        // limit — by default a highlight is visible anywhere in the column.
        double range = config.renderChunkRadius * 16.0;
        double rangeSq = range * range;
        double verticalLimit = config.verticalRenderLimit;
        double verticalLimitSq = verticalLimit * verticalLimit;

        List<ContainerRecord> visible = new ArrayList<>();
        for (ContainerRecord record : IndexManager.index().all()) {
            if (!dim.equals(record.dim)) continue;
            ContainerKind kind = record.kindOrNull();
            if (kind == null || !config.isKindEnabled(kind)) continue;
            if (record.horizontalDistanceSqTo(px, pz) > rangeSq) continue;
            if (verticalLimit > 0) {
                double dy = record.centerY() - py;
                if (dy * dy > verticalLimitSq) continue;
            }
            visible.add(record);
        }

        if (visible.size() > config.maxRenderedContainers) {
            visible.sort((a, b) -> Double.compare(a.distanceSqTo(px, py, pz), b.distanceSqTo(px, py, pz)));
            visible = visible.subList(0, config.maxRenderedContainers);
        }
        if (visible.isEmpty() && !TrackedContainer.isTracking()) return;

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        PoseStack matrices = context.poseStack();

        // ── Resolve geometry once ───────────────────────────────────────────
        // Everything is computed up front rather than inside the emit loops, because the quad
        // and line passes have to be kept strictly separate (see below) and boxFor() is not
        // free — it reads a block state and resolves a VoxelShape per container.
        List<DrawJob> jobs = new ArrayList<>(visible.size());
        List<PendingLabel> labels = new ArrayList<>();
        double labelRangeSq = (double) config.labelMaxDistance * config.labelMaxDistance;

        for (ContainerRecord record : visible) {
            ContainerKind kind = record.kindOrNull();
            if (kind == null) continue;

            AABB box = boxFor(record, world);

            boolean highlighted = config.highlightSearchResults && SearchHighlight.contains(record.key());
            int rgb = highlighted ? config.searchHighlightColor : config.colorOf(kind);
            float r = ((rgb >> 16) & 0xFF) / 255f;
            float g = ((rgb >> 8) & 0xFF) / 255f;
            float b = (rgb & 0xFF) / 255f;

            // Outlines stay near-opaque even when the fill is faint — the edge is what makes a
            // container findable across a room; the fill only carries the fullness reading.
            float outlineAlpha = record.isUnopened() && config.dimUnopened ? 0.45f : 0.9f;
            jobs.add(new DrawJob(box, r, g, b, fillAlpha(record, config, highlighted), outlineAlpha));

            if (config.showLabels && record.distanceSqTo(px, py, pz) <= labelRangeSq) {
                labels.add(new PendingLabel(box.minX + (box.maxX - box.minX) / 2.0, box.maxY + 0.35,
                    box.minZ + (box.maxZ - box.minZ) / 2.0, labelFor(record, config), 0xFF000000 | rgb));
            }
        }

        ContainerRecord tracked = TrackedContainer.get();
        DrawJob trackJob = null;
        if (tracked != null && dim.equals(tracked.dim)) {
            int rgb = config.trackColor;
            trackJob = new DrawJob(boxFor(tracked, world).inflate(0.03),
                ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f,
                0.30f, 1f);
        }

        matrices.pushPose();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = matrices.last().pose();

        RenderType quadLayer = config.seeThrough
            ? Layers.seeThroughQuads()
            : Layers.quads();
        RenderType lineLayer = config.seeThrough
            ? Layers.seeThroughLines()
            : Layers.lines();

        // One layer at a time, start to finish.
        //
        // MultiBufferSource.BufferSource only keeps a single layer building at once: asking it
        // for a second layer's buffer ends the first one. Holding both and writing to them
        // interleaved therefore throws "Not building!" on the stale consumer, which is exactly
        // what an earlier version of this method did. Each pass must fully finish and flush
        // before the next one asks for its buffer.

        if (config.drawFilled || (trackJob != null && config.trackBeam)) {
            VertexConsumer quads = consumers.getBuffer(quadLayer);

            if (config.drawFilled) {
                for (DrawJob job : jobs) {
                    if (job.fillAlpha() > 0f) {
                        Shapes.fillBox(quads, mat, job.box(), job.r(), job.g(), job.b(), job.fillAlpha());
                    }
                }
            }
            if (trackJob != null && config.trackBeam) {
                Shapes.beam(quads, mat, tracked.centerX(), tracked.centerZ(),
                    world.getMinY(), world.getMaxY() + 1, 0.22f,
                    trackJob.r(), trackJob.g(), trackJob.b(), 0.30f);
            }
            if (consumers instanceof MultiBufferSource.BufferSource immediate) {
                immediate.endBatch(quadLayer);
            }
        }

        if (config.drawOutline || trackJob != null) {
            VertexConsumer lines = consumers.getBuffer(lineLayer);

            if (config.drawOutline) {
                for (DrawJob job : jobs) {
                    Shapes.outlineBox(lines, mat, job.box(), job.r(), job.g(), job.b(),
                        job.outlineAlpha(), config.outlineWidth);
                }
            }
            if (trackJob != null) {
                Shapes.outlineBox(lines, mat, trackJob.box(), trackJob.r(), trackJob.g(), trackJob.b(),
                    1f, config.outlineWidth + 1.5f);
            }
            if (consumers instanceof MultiBufferSource.BufferSource immediate) {
                immediate.endBatch(lineLayer);
            }
        }

        // Component goes last, after the geometry layers are flushed, for the same reason.
        for (PendingLabel label : labels) {
            WorldText.draw(matrices, consumers, client.font, camera, label.text(),
                label.x(), label.y(), label.z(), cam.x, cam.y, cam.z,
                label.color(), 0.02f, config.seeThrough);
        }

        matrices.popPose();

        // The quad and line layers were already flushed by name above. Only the text layers are
        // left buffered, and Font picks those internally so we cannot name them — hence
        // a blanket flush, but only when labels actually put something in them. Flushing
        // unconditionally would also drain layers the world renderer intends to draw itself.
        if (!labels.isEmpty() && consumers instanceof MultiBufferSource.BufferSource immediate) {
            immediate.endBatch();
        }
    }

    /** Level-space box for a record, spanning both halves of a double chest. */
    private static AABB boxFor(ContainerRecord record, ClientLevel world) {
        if (record.isEntityBacked()) {
            // Entities are recorded at their block position; a one-block box tracks them closely
            // enough at the tick rate we re-scan, without needing a live entity lookup here.
            return new AABB(new BlockPos(record.x, record.y, record.z)).inflate(0.02);
        }

        BlockPos pos = new BlockPos(record.x, record.y, record.z);
        AABB box = Shapes.blockBox(world.getBlockState(pos), world, pos);
        if (record.hasSecondary) {
            BlockPos other = new BlockPos(record.x2, record.y2, record.z2);
            box = Shapes.union(box, Shapes.blockBox(world.getBlockState(other), world, other));
        }
        return box;
    }

    /**
     * Fill opacity, optionally driven by how full the container is.
     *
     * <p>An unopened container always renders at the flat base opacity — we have no fill data
     * for it, and showing it as "empty" would be an outright lie about a chest we have simply
     * never looked in.
     */
    private static float fillAlpha(ContainerRecord record, ContainerUtilConfig config, boolean highlighted) {
        if (highlighted) return 0.55f;

        if (record.isUnopened()) {
            float base = config.baseFillOpacity / 100f;
            return config.dimUnopened ? base * 0.5f : base;
        }
        if (!config.fillScalesWithFullness) {
            return config.baseFillOpacity / 100f;
        }
        float min = config.minFillOpacity / 100f;
        float max = config.maxFillOpacity / 100f;
        return min + (max - min) * record.fullness();
    }

    private static Component labelFor(ContainerRecord record, ContainerUtilConfig config) {
        StringBuilder text = new StringBuilder(record.displayName());

        if (config.showFillCounts && !record.isUnopened() && record.slotCount > 0) {
            text.append("  ").append(record.usedSlots).append('/').append(record.slotCount);
        }
        if (record.isUnopened()) {
            text.append("  ?");
        } else if (config.showLastSeenAge || record.isStale(config.staleAfterDays)) {
            text.append("  ").append(formatAge(System.currentTimeMillis() - record.lastScanned));
        }
        return Component.literal(text.toString());
    }

    /** Compact relative age: 4m, 3h, 12d. */
    public static String formatAge(long millis) {
        long minutes = millis / 60_000L;
        if (minutes < 1) return "now";
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h";
        return (hours / 24) + "d";
    }
}
