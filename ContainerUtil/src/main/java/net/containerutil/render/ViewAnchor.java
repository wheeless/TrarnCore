package net.containerutil.render;

import net.containerutil.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

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
    private static boolean useCamera(Minecraft client) {
        if (!ConfigManager.get().anchorToCamera) return false;
        Camera camera = client.gameRenderer.getMainCamera();
        // Before the first frame is set up the camera holds defaults, which would put the anchor
        // at the origin and cull everything. Fall back to the player until it is live.
        return camera != null && camera.isInitialized();
    }

    /** The point to measure distances from. */
    public static Vec3 origin(Minecraft client) {
        if (useCamera(client)) {
            return client.gameRenderer.getMainCamera().position();
        }
        if (client.player == null) return Vec3.ZERO;
        return new Vec3(client.player.getX(), client.player.getY(), client.player.getZ());
    }

    /** Yaw to orient the HUD direction arrow against, in degrees. */
    public static float yaw(Minecraft client) {
        if (useCamera(client)) {
            return yawFromCamera(client.gameRenderer.getMainCamera());
        }
        return client.player != null ? client.player.getYRot() : 0f;
    }

    /**
     * Yaw in Minecraft's convention, derived from the camera's forward vector.
     *
     * <p>{@code Camera} no longer exposes yaw directly in 26.x — rotation moved into the render
     * state extraction — so it is recovered from the forward direction. Minecraft yaw is 0 at
     * south and increases clockwise, hence {@code atan2(-x, z)}.
     */
    private static float yawFromCamera(Camera camera) {
        org.joml.Vector3fc forward = camera.forwardVector();
        return (float) Math.toDegrees(Math.atan2(-forward.x(), forward.z()));
    }

    /** Eye position for the peek raycast. */
    public static Vec3 eyePos(Minecraft client) {
        if (useCamera(client)) {
            return client.gameRenderer.getMainCamera().position();
        }
        return client.player != null ? client.player.getEyePosition(1f) : Vec3.ZERO;
    }

    /** Unit look vector for the peek raycast. */
    public static Vec3 lookVec(Minecraft client) {
        if (useCamera(client)) {
            Camera camera = client.gameRenderer.getMainCamera();
            return new Vec3(camera.forwardVector());
        }
        return client.player != null ? client.player.getViewVector(1f) : new Vec3(0, 0, 1);
    }
}
