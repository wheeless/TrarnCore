package net.trarncore.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.trarncore.TrarnCore;

import java.util.Map;

/**
 * Render layers for world-space overlays, including ones that draw through terrain.
 *
 * <p>This is the single most version-fragile code in the family, which is exactly why it lives
 * here rather than being copied into each mod. When Minecraft reworks rendering, this file is
 * what breaks — and fixing it once fixes every mod.
 *
 * <p><b>On see-through rendering.</b> Depth testing is a {@link RenderPipeline} property, not
 * mutable render state, and no vanilla pipeline pairs an always-passing depth test with a lines or
 * quads vertex format. Rather than author pipelines from scratch, we clone the vanilla ones and
 * change a single piece of state, which keeps Mojang's shaders, vertex formats and blending intact.
 *
 * <p>Custom layers are built lazily: the pipeline registry is not populated at mod-init time, and
 * the GPU compiles pipelines on first draw, so nothing needs registering up front.
 *
 * <p><b>One layer at a time.</b> {@link net.minecraft.client.renderer.MultiBufferSource.BufferSource}
 * keeps only a single layer building at once — asking it for a second layer's buffer ends the
 * first. Callers must therefore finish and flush one layer before requesting the next; holding a
 * quad buffer and a line buffer simultaneously and writing to them interleaved throws
 * {@code IllegalStateException: Not building!}.
 */
public final class Layers {

    private static RenderType noDepthLines;
    private static RenderType noDepthQuads;

    private Layers() {
    }

    /** Translucent, unlit quads. Depth-tested. */
    public static RenderType quads() {
        return RenderTypes.debugQuads();
    }

    /** Depth-tested lines. */
    public static RenderType lines() {
        return RenderTypes.LINES;
    }

    /** Translucent quads drawn through terrain. */
    public static RenderType seeThroughQuads() {
        if (noDepthQuads == null) {
            noDepthQuads = buildNoDepth("trarncore:quads_no_depth", RenderPipelines.DEBUG_QUADS);
        }
        return noDepthQuads;
    }

    /** Lines drawn through terrain. */
    public static RenderType seeThroughLines() {
        if (noDepthLines == null) {
            noDepthLines = buildNoDepth("trarncore:lines_no_depth", RenderPipelines.LINES);
        }
        return noDepthLines;
    }

    /** Quad layer, depth-tested or not. */
    public static RenderType quads(boolean seeThrough) {
        return seeThrough ? seeThroughQuads() : quads();
    }

    /** Line layer, depth-tested or not. */
    public static RenderType lines(boolean seeThrough) {
        return seeThrough ? seeThroughLines() : lines();
    }

    /**
     * Clones a vanilla pipeline with depth testing effectively disabled and wraps it in a render
     * type.
     *
     * <p>Every property is copied across explicitly because {@link RenderPipeline} is immutable and
     * shared — mutating the vanilla instance would disable depth testing for everything else that
     * uses it.
     */
    private static RenderType buildNoDepth(String name, RenderPipeline source) {
        String path = name.substring(name.indexOf(':') + 1);

        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(TrarnCore.MOD_ID, path))
            .withVertexShader(source.getVertexShader())
            .withFragmentShader(source.getFragmentShader())
            .withVertexFormat(source.getVertexFormat(), source.getVertexFormatMode())
            .withCull(source.isCull())
            .withPolygonMode(source.getPolygonMode())
            // Blending and the colour write mask travel together in this one object, so copying it
            // preserves the source pipeline's transparency behaviour exactly.
            .withColorTargetState(source.getColorTargetState())
            // The one actual change. ALWAYS_PASS means the depth test never rejects a fragment, and
            // not writing depth keeps the overlay from occluding the world drawn after it.
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false));

        for (String sampler : source.getSamplers()) {
            builder.withSampler(sampler);
        }
        for (RenderPipeline.UniformDescription uniform : source.getUniforms()) {
            builder.withUniform(uniform.name(), uniform.type());
        }
        copyDefines(source.getShaderDefines(), builder);

        RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(builder.build());
        // Only quads get sorted. That flag makes the buffer depth-sort its primitives, which is
        // right for alpha-blended faces but is a quad-oriented operation — asking for it on a
        // LINES-mode buffer invites trouble for no benefit.
        if (source.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
            setup.sortOnUpload();
        }
        return RenderType.create(name, setup.createRenderSetup());
    }

    /**
     * Shader defines are stored as strings but the builder only accepts int or float values, so
     * they have to be re-typed on the way back in. Both pipelines we clone have no valued defines
     * today; this exists so that stops being a silent assumption if that changes.
     */
    private static void copyDefines(ShaderDefines defines, RenderPipeline.Builder builder) {
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
