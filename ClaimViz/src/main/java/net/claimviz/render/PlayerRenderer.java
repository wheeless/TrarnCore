package net.claimviz.render;

import net.claimviz.ClaimViz;
import net.claimviz.data.PlayerData;
import net.claimviz.data.PlayerFetcher;
import net.claimviz.event.ServerJoinHandler;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
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
    private static Vec3d storedCamPos;

    private static long lastWorldRenderError = 0;
    private static long lastHudRenderError = 0;
    private static long lastRenderStats = 0;

    public static void register() {
        WorldRenderEvents.END_MAIN.register(PlayerRenderer::renderWorld);
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> renderHud(drawContext));
    }

    // ── World-space: health cross + yaw tick + name tag ──────────────────────

    private static void renderWorld(WorldRenderContext context) {
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

    private static void renderWorldInternal(WorldRenderContext context) {
        if (!ClaimViz.showPlayers) return;
        var cfg = ServerJoinHandler.getActiveConfig();
        if (cfg == null || !cfg.showPlayers) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        String localDim = ServerJoinHandler.getLastDimension();
        if (localDim == null) return;

        Camera camera = client.gameRenderer.getCamera();
        Vec3d cam = camera.getCameraPos();

        // Build CPU-side MVP for HUD skin-icon projection this frame
        Quaternionf invRot = camera.getRotation().conjugate(new Quaternionf());
        Matrix4f view = new Matrix4f()
            .rotate(invRot)
            .translate(-(float) cam.x, -(float) cam.y, -(float) cam.z);
        int fovDeg = client.options.getFov().getValue();
        float fovRad = (float) Math.toRadians(fovDeg);
        float aspect = (float) client.getWindow().getScaledWidth()
                      / (float) client.getWindow().getScaledHeight();
        Matrix4f proj = new Matrix4f().perspective(fovRad, aspect, 0.05f, 768f);
        storedMVP = proj.mul(view, new Matrix4f());
        storedCamPos = cam;

        String selfUuid = client.player.getUuidAsString().replace("-", "");
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

        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();

        // ── Health cross markers ─────────────────────────────────────────────

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();
        VertexConsumer vc = consumers.getBuffer(RenderLayers.LINES);

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

        matrices.pop();

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(RenderLayers.LINES);
        }

        // ── Name tag billboards ──────────────────────────────────────────────

        for (PlayerData pd : players) {
            matrices.push();
            matrices.translate(
                pd.x() - cam.x,
                pd.y() + 2.3 - cam.y,
                pd.z() - cam.z
            );
            matrices.multiply(camera.getRotation());
            matrices.scale(0.025f, -0.025f, 0.025f);

            Matrix4f textMat = matrices.peek().getPositionMatrix();
            String name = pd.name();
            int tw = client.textRenderer.getWidth(name);

            client.textRenderer.draw(
                name, -tw / 2f, 0, 0xFFFFFFFF, false,
                textMat, consumers,
                TextRenderer.TextLayerType.SEE_THROUGH,
                0x44000000,
                LightmapTextureManager.MAX_LIGHT_COORDINATE
            );

            matrices.pop();
        }
    }

    // ── HUD overlay: skin face icon ──────────────────────────────────────────

    private static void renderHud(DrawContext drawContext) {
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

    private static void renderHudInternal(DrawContext drawContext) {
        if (!ClaimViz.showPlayers) return;
        var cfg = ServerJoinHandler.getActiveConfig();
        if (cfg == null || !cfg.showPlayers) return;
        if (storedMVP == null || storedCamPos == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        String localDim = ServerJoinHandler.getLastDimension();
        if (localDim == null) return;

        String selfUuid = client.player.getUuidAsString().replace("-", "");
        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();
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

            // Signature: drawTexturedQuad(id, x1, y1, x2, y2,  u1, u2,  v1, v2)
            // Face region: pixels (8,8)-(16,16) on a 64x64 skin
            drawContext.drawTexturedQuad(skin,
                sx - 8, sy - 24, sx + 8, sy - 8,
                8f / 64f, 16f / 64f,
                8f / 64f, 16f / 64f
            );
            // Hat overlay: pixels (40,8)-(48,16) on a 64x64 skin
            drawContext.drawTexturedQuad(skin,
                sx - 8, sy - 24, sx + 8, sy - 8,
                40f / 64f, 48f / 64f,
                8f / 64f, 16f / 64f
            );
        }
    }

    private static float[] project(double wx, double wy, double wz,
                                   Vec3d cam, Matrix4f mvp, int sw, int sh) {
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

    private static Identifier getSkin(PlayerData pd, MinecraftClient client) {
        if (client.getNetworkHandler() == null) return null;
        try {
            UUID uuid = UUID.fromString(
                pd.uuid().substring(0, 8) + "-" + pd.uuid().substring(8, 12) + "-" +
                pd.uuid().substring(12, 16) + "-" + pd.uuid().substring(16, 20) + "-" +
                pd.uuid().substring(20)
            );
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(uuid);
            if (entry == null) return null;
            SkinTextures textures = entry.getSkinTextures();
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
