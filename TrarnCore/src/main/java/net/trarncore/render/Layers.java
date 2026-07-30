package net.trarncore.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;
import net.trarncore.TrarnCore;

import java.util.Map;

/**
 * Render layers for world-space overlays, including ones that draw through terrain.
 *
 * <p>This is the single most version-fragile code in the family, which is exactly why it lives
 * here rather than being copied into each mod. When Minecraft reworks rendering, this file is
 * what breaks — and fixing it once fixes every mod.
 *
 * <p><b>On see-through rendering.</b> Depth testing is a {@link RenderPipeline} property in this
 * version, not mutable render state, and no vanilla pipeline pairs {@code NO_DEPTH_TEST} with a
 * lines or quads vertex format — the only no-depth pipelines vanilla ships are for weather and
 * see-through text. Rather than author pipelines from scratch, we clone the vanilla ones and flip
 * a single flag, which keeps Mojang's shaders, vertex formats and blending intact.
 *
 * <p>Custom layers are built lazily: the pipeline registry is not populated at mod-init time, and
 * the GPU compiles pipelines on first draw, so nothing needs registering up front.
 *
 * <p><b>One layer at a time.</b> {@link net.minecraft.client.render.VertexConsumerProvider.Immediate}
 * keeps only a single layer building at once — asking it for a second layer's buffer ends the
 * first. Callers must therefore finish and flush one layer before requesting the next; holding a
 * quad buffer and a line buffer simultaneously and writing to them interleaved throws
 * {@code IllegalStateException: Not building!} on the stale consumer.
 */
public final class Layers {

    private static RenderLayer noDepthLines;
    private static RenderLayer noDepthQuads;

    private Layers() {
    }

    /** Translucent, unlit quads. Depth-tested. */
    public static RenderLayer quads() {
        return RenderLayers.debugQuads();
    }

    /** Depth-tested lines. */
    public static RenderLayer lines() {
        return RenderLayers.LINES;
    }

    /** Translucent quads drawn through terrain. */
    public static RenderLayer seeThroughQuads() {
        if (noDepthQuads == null) {
            noDepthQuads = buildNoDepth("trarncore:quads_no_depth", RenderPipelines.DEBUG_QUADS);
        }
        return noDepthQuads;
    }

    /** Lines drawn through terrain. */
    public static RenderLayer seeThroughLines() {
        if (noDepthLines == null) {
            noDepthLines = buildNoDepth("trarncore:lines_no_depth", RenderPipelines.LINES);
        }
        return noDepthLines;
    }

    /** Quad layer, depth-tested or not. */
    public static RenderLayer quads(boolean seeThrough) {
        return seeThrough ? seeThroughQuads() : quads();
    }

    /** Line layer, depth-tested or not. */
    public static RenderLayer lines(boolean seeThrough) {
        return seeThrough ? seeThroughLines() : lines();
    }

    /**
     * Clones a vanilla pipeline with depth testing disabled and wraps it in a render layer.
     *
     * <p>Every property is copied across explicitly because {@link RenderPipeline} is immutable
     * and shared — mutating the vanilla instance would disable depth testing for everything else
     * that uses it.
     */
    private static RenderLayer buildNoDepth(String name, RenderPipeline source) {
        String path = name.substring(name.indexOf(':') + 1);

        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(Identifier.of(TrarnCore.MOD_ID, path))
            .withVertexShader(source.getVertexShader())
            .withFragmentShader(source.getFragmentShader())
            .withVertexFormat(source.getVertexFormat(), source.getVertexFormatMode())
            .withCull(source.isCull())
            .withPolygonMode(source.getPolygonMode())
            .withColorWrite(source.isWriteColor(), source.isWriteAlpha())
            // The one actual change. Everything above and below is a faithful copy.
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            // Not writing depth either, so an overlay never occludes the world behind it.
            .withDepthWrite(false);

        source.getBlendFunction().ifPresentOrElse(builder::withBlend, builder::withoutBlend);

        for (String sampler : source.getSamplers()) {
            builder.withSampler(sampler);
        }
        for (RenderPipeline.UniformDescription uniform : source.getUniforms()) {
            builder.withUniform(uniform.name(), uniform.type());
        }
        copyDefines(source.getShaderDefines(), builder);

        RenderSetup.Builder setup = RenderSetup.builder(builder.build());
        // Only quads get marked translucent. That flag makes the buffer depth-sort its
        // primitives, which is right for alpha-blended faces but is a quad-oriented operation —
        // asking for it on a LINES-mode buffer invites trouble for no benefit.
        if (source.getVertexFormatMode() == VertexFormat.DrawMode.QUADS) {
            setup.translucent();
        }
        return RenderLayer.of(name, setup.build());
    }

    /**
     * Shader defines are stored as strings but the builder only accepts int or float values, so
     * they have to be re-typed on the way back in. Both pipelines we clone have no valued defines
     * today; this exists so that stops being a silent assumption if that changes.
     */
    private static void copyDefines(Defines defines, RenderPipeline.Builder builder) {
        for (String flag : defines.flags()) {
            builder.withShaderDefine(flag);
        }
        for (Map.Entry<String, String> define : defines.values().entrySet()) {
            String value = define.getValue();
            try {
                builder.withShaderDefine(define.getKey(), Integer.parseInt(value));
                continue;
            } catch (NumberFormatException ignored) {
                // Not an int — try float below.
            }
            try {
                builder.withShaderDefine(define.getKey(), Float.parseFloat(value));
            } catch (NumberFormatException e) {
                TrarnCore.LOGGER.warn(
                    "Cannot copy shader define {}={} onto a see-through pipeline "
                        + "(neither int nor float); overlays may render incorrectly.",
                    define.getKey(), value);
            }
        }
    }
}
