package net.trarncore.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * Camera-facing text at a world position — the floating-label pattern.
 */
public final class WorldText {

    /** Roughly nameplate-sized. */
    public static final float DEFAULT_SCALE = 0.02f;

    private WorldText() {
    }

    /**
     * Draws a billboarded label.
     *
     * <p>Must be called <em>after</em> any geometry layers have been flushed: text pulls its own
     * buffers from the provider, and an {@code Immediate} provider only keeps one layer building
     * at a time. See {@link Layers} for the full explanation.
     *
     * @param camX camera position, subtracted to put the label in view space
     * @param argb label colour, alpha included
     */
    public static void draw(PoseStack matrices, MultiBufferSource consumers,
                            Font font, Camera camera, Component text,
                            double wx, double wy, double wz,
                            double camX, double camY, double camZ,
                            int argb, float scale, boolean seeThrough) {
        matrices.pushPose();
        matrices.translate(wx - camX, wy - camY, wz - camZ);
        matrices.mulPose(camera.rotation());
        // The negative scale is not a typo: Minecraft's text is authored y-down, so after the
        // billboard rotation both axes have to be flipped for the label to come out upright and
        // the right way round.
        matrices.scale(-scale, -scale, scale);

        Matrix4f mat = matrices.last().pose();
        float halfWidth = -font.width(text) / 2f;
        font.drawInBatch(text, halfWidth, 0f, argb, false, mat, consumers,
            seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
            0x50000000, 0xF000F0);

        matrices.popPose();
    }

    /** Convenience overload using {@link #DEFAULT_SCALE}. */
    public static void draw(PoseStack matrices, MultiBufferSource consumers,
                            Font font, Camera camera, Component text,
                            double wx, double wy, double wz,
                            double camX, double camY, double camZ,
                            int argb, boolean seeThrough) {
        draw(matrices, consumers, font, camera, text, wx, wy, wz,
            camX, camY, camZ, argb, DEFAULT_SCALE, seeThrough);
    }
}
