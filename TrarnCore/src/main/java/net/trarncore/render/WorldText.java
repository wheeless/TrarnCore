package net.trarncore.render;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
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
    public static void draw(MatrixStack matrices, VertexConsumerProvider consumers,
                            TextRenderer textRenderer, Camera camera, Text text,
                            double wx, double wy, double wz,
                            double camX, double camY, double camZ,
                            int argb, float scale, boolean seeThrough) {
        matrices.push();
        matrices.translate(wx - camX, wy - camY, wz - camZ);
        matrices.multiply(camera.getRotation());
        // The negative scale is not a typo: Minecraft's text is authored y-down, so after the
        // billboard rotation both axes have to be flipped for the label to come out upright and
        // the right way round.
        matrices.scale(-scale, -scale, scale);

        Matrix4f mat = matrices.peek().getPositionMatrix();
        float halfWidth = -textRenderer.getWidth(text) / 2f;
        textRenderer.draw(text, halfWidth, 0f, argb, false, mat, consumers,
            seeThrough ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL,
            0x50000000, 0xF000F0);

        matrices.pop();
    }

    /** Convenience overload using {@link #DEFAULT_SCALE}. */
    public static void draw(MatrixStack matrices, VertexConsumerProvider consumers,
                            TextRenderer textRenderer, Camera camera, Text text,
                            double wx, double wy, double wz,
                            double camX, double camY, double camZ,
                            int argb, boolean seeThrough) {
        draw(matrices, consumers, textRenderer, camera, text, wx, wy, wz,
            camX, camY, camZ, argb, DEFAULT_SCALE, seeThrough);
    }
}
