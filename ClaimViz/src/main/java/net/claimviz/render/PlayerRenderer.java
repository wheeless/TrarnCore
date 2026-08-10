package net.claimviz.render;

import net.claimviz.ClaimViz;
import net.claimviz.data.PlayerData;
import net.claimviz.data.PlayerFetcher;
import net.claimviz.event.ServerJoinHandler;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.trarncore.render.Shapes;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.List;
import java.util.UUID;

public class PlayerRenderer {

    /** Matches what this renderer has always used. */
    private static final float LINE_WIDTH = 2f;

    // Captured during END_MAIN, consumed in HudRenderCallback — both on render thread.
    private static Matrix4f storedMVP;
    private static Vec3 storedCamPos;

    private static long lastWorldRenderError = 0;
    private static long lastHudRenderError = 0;
    private static long lastRenderStats = 0;

    public static void register() {
        LevelRenderEvents.END_MAIN.register(PlayerRenderer::renderWorld);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ClaimViz.MOD_ID, "players"),
            (drawContext, tickCounter) -> renderHud(drawContext));
    }

    // ── Level-space: health cross + yaw tick + name tag ──────────────────────

    private static void renderWorld(LevelRenderContext context) {
        long start = System.nanoTime();
        try {
            renderWorldInternal(context);
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            if (now - lastWorldRenderError > 5000) {
                lastWorldRenderError = now;
                ClaimViz.LOGGER.error("[ClaimViz] Player world render crashed (suppressing repeats for 5s)", e);
            }
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        if (ms > 50) {
            ClaimViz.LOGGER.warn("[ClaimViz] Player world render took {}ms — possible freeze source", ms);
        }
    }

    private static void renderWorldInternal(LevelRenderContext context) {
        if (!ClaimViz.showPlayers) return;
        var cfg = ServerJoinHandler.getActiveConfig();
        if (cfg == null || !cfg.showPlayers) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        String localDim = ServerJoinHandler.getLastDimension();
        if (localDim == null) return;

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();

        // Build CPU-side MVP for HUD skin-icon projection this frame
        Quaternionf invRot = camera.rotation().conjugate(new Quaternionf());
        Matrix4f view = new Matrix4f()
            .rotate(invRot)
            .translate(-(float) cam.x, -(float) cam.y, -(float) cam.z);
        int fovDeg = client.options.fov().get();
        float fovRad = (float) Math.toRadians(fovDeg);
        float aspect = (float) client.getWindow().getGuiScaledWidth()
                      / (float) client.getWindow().getGuiScaledHeight();
        Matrix4f proj = new Matrix4f().perspective(fovRad, aspect, 0.05f, 768f);
        storedMVP = proj.mul(view, new Matrix4f());
        storedCamPos = cam;

        String selfUuid = client.player.getStringUUID().replace("-", "");
        double renderDist = cfg.playerRenderDistance;
        double renderDistSq = renderDist * renderDist;
        double selfX = client.player.getX();
        double selfZ = client.player.getZ();
        List<PlayerData> players = PlayerFetcher.getCached().stream()
            .filter(p -> localDim.equals(p.world()))
            .filter(p -> !selfUuid.equals(p.uuid()))
            .filter(p -> distSq(p.x(), p.z(), selfX, selfZ) <= renderDistSq)
            .toList();

        long now = System.currentTimeMillis();
        if (now - lastRenderStats > 15000) {
            lastRenderStats = now;
            ClaimViz.LOGGER.info("[ClaimViz] Rendering {} players in {}, renderDist={}",
                players.size(), localDim, renderDist);
        }

        if (players.isEmpty()) return;

        PoseStack matrices = context.poseStack();
        MultiBufferSource consumers = context.bufferSource();

        // ── Health cross markers ─────────────────────────────────────────────

        matrices.pushPose();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = matrices.last().pose();
        VertexConsumer vc = consumers.getBuffer(RenderTypes.LINES);

        for (PlayerData pd : players) {
            float x = (float) pd.x();
            float y = (float) pd.y();
            float z = (float) pd.z();
            float r = healthRed(pd.health());
            float g = healthGreen(pd.health());

            addLine(vc, mat, x, y, z, x, y + 2f, z, r, g, 0f);
            addLine(vc, mat, x - 0.4f, y + 1.5f, z, x + 0.4f, y + 1.5f, z, r, g, 0f);
            addLine(vc, mat, x, y + 1.5f, z - 0.4f, x, y + 1.5f, z + 0.4f, r, g, 0f);
            float tx = (float)  Math.sin(Math.toRadians(pd.yaw())) * 0.6f;
            float tz = (float) -Math.cos(Math.toRadians(pd.yaw())) * 0.6f;
            addLine(vc, mat, x, y + 2f, z, x + tx, y + 2f, z + tz, r, g, 0f);
        }

        matrices.popPose();

        if (consumers instanceof MultiBufferSource.BufferSource imm) {
            imm.endBatch(RenderTypes.LINES);
        }

        // ── Name tag billboards ──────────────────────────────────────────────

        for (PlayerData pd : players) {
            matrices.pushPose();
            matrices.translate(
                pd.x() - cam.x,
                pd.y() + 2.3 - cam.y,
                pd.z() - cam.z
            );
            matrices.mulPose(camera.rotation());
            matrices.scale(0.025f, -0.025f, 0.025f);

            Matrix4f textMat = matrices.last().pose();
            String name = pd.name();
            int tw = client.font.width(name);

            client.font.drawInBatch(
                name, -tw / 2f, 0, 0xFFFFFFFF, false,
                textMat, consumers,
                Font.DisplayMode.SEE_THROUGH,
                0x44000000,
                0xF000F0
            );

            matrices.popPose();
        }

        // Flush while the world transform that billboarded the tags is still in effect. Font
        // picks its own layers internally so they cannot be ended by name — hence a blanket
        // flush. Leaving them for the level renderer to drain is what made the tags swing
        // around their anchor as the camera turned.
        if (!players.isEmpty() && consumers instanceof MultiBufferSource.BufferSource imm) {
            imm.endBatch();
        }
    }

    // ── HUD overlay: skin face icon ──────────────────────────────────────────

    private static void renderHud(GuiGraphicsExtractor drawContext) {
        long start = System.nanoTime();
        try {
            renderHudInternal(drawContext);
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            if (now - lastHudRenderError > 5000) {
                lastHudRenderError = now;
                ClaimViz.LOGGER.error("[ClaimViz] Player HUD render crashed (suppressing repeats for 5s)", e);
            }
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        if (ms > 50) {
            ClaimViz.LOGGER.warn("[ClaimViz] Player HUD render took {}ms — possible freeze source", ms);
        }
    }

    private static void renderHudInternal(GuiGraphicsExtractor drawContext) {
        if (!ClaimViz.showPlayers) return;
        var cfg = ServerJoinHandler.getActiveConfig();
        if (cfg == null || !cfg.showPlayers) return;
        if (storedMVP == null || storedCamPos == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        String localDim = ServerJoinHandler.getLastDimension();
        if (localDim == null) return;

        String selfUuid = client.player.getStringUUID().replace("-", "");
        int w = client.getWindow().getGuiScaledWidth();
        int h = client.getWindow().getGuiScaledHeight();
        double renderDistSq = (double) cfg.playerRenderDistance * cfg.playerRenderDistance;
        double selfX = client.player.getX();
        double selfZ = client.player.getZ();

        for (PlayerData pd : PlayerFetcher.getCached()) {
            if (!localDim.equals(pd.world())) continue;
            if (selfUuid.equals(pd.uuid())) continue;
            if (distSq(pd.x(), pd.z(), selfX, selfZ) > renderDistSq) continue;

            // Project ~2.3 blocks above feet
            float[] screen = project(pd.x(), pd.y() + 2.3, pd.z(), storedCamPos, storedMVP, w, h);
            if (screen == null) continue;

            int sx = (int) screen[0];
            int sy = (int) screen[1];

            Identifier skin = getSkin(pd, client);
            if (skin == null) continue;

            // Signature: blit(pipeline, id, x, y, u, v, destW, destH, srcW, srcH, texW, texH)
            // Face region: pixels (8,8)-(16,16) on a 64x64 skin
            drawContext.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skin,
                sx - 8, sy - 24, 8f, 8f, 16, 16, 8, 8, 64, 64);
            // Hat overlay: pixels (40,8)-(48,16) on a 64x64 skin
            drawContext.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skin,
                sx - 8, sy - 24, 40f, 8f, 16, 16, 8, 8, 64, 64);
        }
    }

    private static float[] project(double wx, double wy, double wz,
                                   Vec3 cam, Matrix4f mvp, int sw, int sh) {
        Vector4f v = new Vector4f(
            (float)(wx - cam.x),
            (float)(wy - cam.y),
            (float)(wz - cam.z),
            1f
        );
        mvp.transform(v);
        if (v.w <= 0f) return null;
        float ndcX = v.x / v.w;
        float ndcY = v.y / v.w;
        if (ndcX < -1f || ndcX > 1f || ndcY < -1f || ndcY > 1f) return null;
        return new float[]{
            (ndcX + 1f) * 0.5f * sw,
            (1f - ndcY) * 0.5f * sh
        };
    }

    private static Identifier getSkin(PlayerData pd, Minecraft client) {
        if (client.getConnection() == null) return null;
        try {
            UUID uuid = UUID.fromString(
                pd.uuid().substring(0, 8) + "-" + pd.uuid().substring(8, 12) + "-" +
                pd.uuid().substring(12, 16) + "-" + pd.uuid().substring(16, 20) + "-" +
                pd.uuid().substring(20)
            );
            PlayerInfo entry = client.getConnection().getPlayerInfo(uuid);
            if (entry == null) return null;
            PlayerSkin textures = entry.getSkin();
            return textures != null ? textures.body().texturePath() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void addLine(VertexConsumer vc, Matrix4f mat,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float r, float g, float b) {
        Shapes.line(vc, mat, x1, y1, z1, x2, y2, z2, r, g, b, 1f, LINE_WIDTH);
    }
    private static double distSq(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2, dz = z1 - z2;
        return dx * dx + dz * dz;
    }

    private static float healthRed(int health)   { return health <= 10 ? 1.0f : (float)(20 - health) / 10f; }
    private static float healthGreen(int health) { return health >= 10 ? 1.0f : health / 10f; }
}
