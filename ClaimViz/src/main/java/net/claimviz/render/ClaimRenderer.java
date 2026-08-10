package net.claimviz.render;

import net.claimviz.ClaimViz;
import net.claimviz.config.ConfigManager;
import net.claimviz.data.ClaimCache;
import net.claimviz.data.ClaimRect;
import net.claimviz.event.ServerJoinHandler;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import net.trarncore.render.Shapes;
import org.joml.Matrix4f;

import java.util.List;

public class ClaimRenderer {

    /** Matches what this renderer has always used. */
    private static final float LINE_WIDTH = 2.5f;

    private static final int COLOR_OWN    = 0xBB44FF;
    private static final int COLOR_ADMIN  = 0x00CCBB;
    private static final int COLOR_INSIDE = 0xFFFF00;

    private static long lastRenderError = 0;
    private static long lastRenderStats = 0;

    private static final int LABEL_COLOR_OWN   = 0xFFDD99FF; // lighter purple, full alpha
    private static final int LABEL_COLOR_ADMIN  = 0xFF44FFEE; // lighter teal, full alpha
    private static final int LABEL_COLOR_OTHER  = 0xFFFFFFFF; // white, full alpha

    // How far above the claim line the text hovers (blocks)
    private static final float LABEL_HEIGHT = 2.8f;

    public static void register() {
        LevelRenderEvents.END_MAIN.register(ClaimRenderer::render);
    }

    private static void render(LevelRenderContext context) {
        long start = System.nanoTime();
        try {
            renderInternal(context);
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            if (now - lastRenderError > 5000) {
                lastRenderError = now;
                ClaimViz.LOGGER.error("[ClaimViz] Claim render crashed (suppressing repeats for 5s)", e);
            }
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        if (ms > 50) {
            ClaimViz.LOGGER.warn("[ClaimViz] Claim render took {}ms — possible freeze source", ms);
        }
    }

    private static void renderInternal(LevelRenderContext context) {
        if (!ClaimViz.showClaims) return;
        var cfg = ServerJoinHandler.getActiveConfig();
        if (cfg == null || !cfg.showClaims) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        String dim = ServerJoinHandler.getLastDimension();
        if (dim == null) return;

        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        double renderDist = ConfigManager.get().claimRenderDistance;
        List<ClaimRect> nearby = ClaimCache.get(dim).stream()
            .filter(c -> c.isNear(px, pz, renderDist))
            .toList();
        if (nearby.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - lastRenderStats > 15000) {
            lastRenderStats = now;
            ClaimViz.LOGGER.info("[ClaimViz] Rendering {} claims in {}", nearby.size(), dim);
        }

        String playerName = client.player.getGameProfile().name();
        Vec3 cam = client.gameRenderer.getMainCamera().position();
        PoseStack matrices = context.poseStack();
        MultiBufferSource consumers = context.bufferSource();

        matrices.pushPose();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = matrices.last().pose();

        VertexConsumer vc = consumers.getBuffer(RenderTypes.LINES);
        float y = (float) py;

        for (ClaimRect claim : nearby) {
            int packed = claimColor(claim, px, pz, playerName);
            float r = ((packed >> 16) & 0xFF) / 255f;
            float g = ((packed >>  8) & 0xFF) / 255f;
            float b = ( packed        & 0xFF) / 255f;

            float x1 = claim.minX();
            float x2 = claim.maxX();
            float z1 = claim.minZ();
            float z2 = claim.maxZ();

            addLine(vc, mat, x1, y, z1, x2, y, z1, r, g, b);
            addLine(vc, mat, x2, y, z1, x2, y, z2, r, g, b);
            addLine(vc, mat, x2, y, z2, x1, y, z2, r, g, b);
            addLine(vc, mat, x1, y, z2, x1, y, z1, r, g, b);
        }

        matrices.popPose();

        if (consumers instanceof MultiBufferSource.BufferSource imm) {
            imm.endBatch(RenderTypes.LINES);
        }

        // ── Owner labels ─────────────────────────────────────────────────────
        if (cfg.showClaimOwnerLabels) {
            Camera camera = client.gameRenderer.getMainCamera();
            float spacing = Math.max(1, cfg.claimLabelSpacing);
            for (ClaimRect claim : nearby) {
                String text = labelText(claim, playerName);
                int color   = labelColor(claim, playerName);
                int tw = client.font.width(text);
                float labelY = y + LABEL_HEIGHT;

                placeLabelsOnEdge(text, color, tw, claim.minX(), claim.maxX(), claim.minZ(), true,
                    labelY, spacing, cam, matrices, consumers, client, camera);
                placeLabelsOnEdge(text, color, tw, claim.minX(), claim.maxX(), claim.maxZ(), true,
                    labelY, spacing, cam, matrices, consumers, client, camera);
                placeLabelsOnEdge(text, color, tw, claim.minZ(), claim.maxZ(), claim.minX(), false,
                    labelY, spacing, cam, matrices, consumers, client, camera);
                placeLabelsOnEdge(text, color, tw, claim.minZ(), claim.maxZ(), claim.maxX(), false,
                    labelY, spacing, cam, matrices, consumers, client, camera);
            }

            // Text must be flushed here, while the world transform that billboarded it is still
            // the one in effect. Font picks its own layers internally so they cannot be ended by
            // name — hence a blanket flush. Leaving them buffered for the level renderer to drain
            // is what made the labels swing around their anchor as the camera turned.
            if (!nearby.isEmpty() && consumers instanceof MultiBufferSource.BufferSource imm) {
                imm.endBatch();
            }
        }
    }

    /**
     * Places labels at the midpoint of an edge and at LABEL_SPACING intervals outward from it.
     * fixedIsX=true  → fixed coordinate is X, varying is Z (horizontal edge)
     * fixedIsX=false → fixed coordinate is Z, varying is X (vertical edge)
     */
    private static void placeLabelsOnEdge(String text, int color, int tw,
                                           float varyFrom, float varyTo, float fixed, boolean fixedIsX,
                                           float labelY, float spacing, Vec3 cam, PoseStack matrices,
                                           MultiBufferSource consumers, Minecraft client,
                                           Camera camera) {
        float mid = (varyFrom + varyTo) / 2f;
        placeLabel(text, color, tw, varyToWorld(mid, fixed, fixedIsX), labelY,
            varyToWorldZ(mid, fixed, fixedIsX), cam, matrices, consumers, client, camera);

        for (float offset = spacing; ; offset += spacing) {
            boolean placedAny = false;
            if (mid - offset >= varyFrom) {
                float v = mid - offset;
                placeLabel(text, color, tw, varyToWorld(v, fixed, fixedIsX), labelY,
                    varyToWorldZ(v, fixed, fixedIsX), cam, matrices, consumers, client, camera);
                placedAny = true;
            }
            if (mid + offset <= varyTo) {
                float v = mid + offset;
                placeLabel(text, color, tw, varyToWorld(v, fixed, fixedIsX), labelY,
                    varyToWorldZ(v, fixed, fixedIsX), cam, matrices, consumers, client, camera);
                placedAny = true;
            }
            if (!placedAny) break;
        }
    }

    private static float varyToWorld(float vary, float fixed, boolean fixedIsX) {
        return fixedIsX ? vary : fixed;
    }

    private static float varyToWorldZ(float vary, float fixed, boolean fixedIsX) {
        return fixedIsX ? fixed : vary;
    }

    private static void placeLabel(String text, int color, int tw,
                                    float wx, float wy, float wz, Vec3 cam,
                                    PoseStack matrices, MultiBufferSource consumers,
                                    Minecraft client, Camera camera) {
        matrices.pushPose();
        matrices.translate(wx - cam.x, wy - cam.y, wz - cam.z);
        matrices.mulPose(camera.rotation());
        matrices.scale(0.025f, -0.025f, 0.025f);
        Matrix4f textMat = matrices.last().pose();
        client.font.drawInBatch(
            text, -tw / 2f, 0, color, false,
            textMat, consumers,
            Font.DisplayMode.NORMAL,
            0x55000000,
            0xF000F0
        );
        matrices.popPose();
    }

    private static String labelText(ClaimRect claim, String playerName) {
        if (playerName.equalsIgnoreCase(claim.owner())) return "Your claim";
        if ("Administrator".equals(claim.owner()))      return "Admin";
        return claim.owner();
    }

    private static int labelColor(ClaimRect claim, String playerName) {
        if (playerName.equalsIgnoreCase(claim.owner())) return LABEL_COLOR_OWN;
        if ("Administrator".equals(claim.owner()))      return LABEL_COLOR_ADMIN;
        return LABEL_COLOR_OTHER;
    }

    private static int claimColor(ClaimRect claim, double px, double pz, String playerName) {
        if (playerName.equalsIgnoreCase(claim.owner())) return COLOR_OWN;
        if ("Administrator".equals(claim.owner()))      return COLOR_ADMIN;
        return claim.contains(px, pz) ? COLOR_INSIDE : (claim.color() & 0xFFFFFF);
    }

    private static void addLine(VertexConsumer vc, Matrix4f mat,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float r, float g, float b) {
        Shapes.line(vc, mat, x1, y1, z1, x2, y2, z2, r, g, b, 1f, LINE_WIDTH);
    }}
