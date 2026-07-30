package net.containerutil.render;

import net.containerutil.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/**
 * Where ContainerUtil measures from.
 *
 * <p>Drawing is always camera-relative — geometry is translated by the camera position, so boxes
 * land at their true world coordinates no matter where you are viewing from. What this class
 * controls is everything that <em>measures</em>: the render-distance cull, label ranges, the peek
 * raycast and the HUD direction arrow. Those all ask "how far, and which way, from me?" and under
 * a freecam mod there are two defensible answers.
 *
 * <p>Anchored to the player (the default), flying a freecam across the base shows nothing new —
 * highlights stay clustered around your body. Anchored to the camera, the highlights follow where
 * you are actually looking from, which is the point of using a freecam to survey storage.
 *
 * <p>Freecam mods work by moving the game camera, so no integration with any specific mod is
 * needed: reading {@link Camera#getCameraPos()} picks up whatever moved it. It also means this
 * toggle does something mildly useful without a freecam at all — in third person it measures from
 * behind your shoulder rather than from your body.
 */
public final class ViewAnchor {

    private ViewAnchor() {
    }

    /** True when measurements should come from the camera rather than the player. */
    private static boolean useCamera(MinecraftClient client) {
        if (!ConfigManager.get().anchorToCamera) return false;
        Camera camera = client.gameRenderer.getCamera();
        // Before the first frame is set up the camera holds defaults, which would put the anchor
        // at the origin and cull everything. Fall back to the player until it is live.
        return camera != null && camera.isReady();
    }

    /** The point to measure distances from. */
    public static Vec3d origin(MinecraftClient client) {
        if (useCamera(client)) {
            return client.gameRenderer.getCamera().getCameraPos();
        }
        if (client.player == null) return Vec3d.ZERO;
        return new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
    }

    /** Yaw to orient the HUD direction arrow against, in degrees. */
    public static float yaw(MinecraftClient client) {
        if (useCamera(client)) {
            return client.gameRenderer.getCamera().getYaw();
        }
        return client.player != null ? client.player.getYaw() : 0f;
    }

    /** Eye position for the peek raycast. */
    public static Vec3d eyePos(MinecraftClient client) {
        if (useCamera(client)) {
            return client.gameRenderer.getCamera().getCameraPos();
        }
        return client.player != null ? client.player.getCameraPosVec(1f) : Vec3d.ZERO;
    }

    /** Unit look vector for the peek raycast. */
    public static Vec3d lookVec(MinecraftClient client) {
        if (useCamera(client)) {
            Camera camera = client.gameRenderer.getCamera();
            return Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        }
        return client.player != null ? client.player.getRotationVec(1f) : new Vec3d(0, 0, 1);
    }
}
