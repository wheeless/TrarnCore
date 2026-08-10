package net.trarncore.render;

import net.minecraft.world.level.block.state.BlockState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import org.joml.Matrix4f;

/**
 * World-space geometry primitives.
 *
 * <p>Between them the five mods had written {@code addLine} four separate times (twice inside
 * ClaimViz alone), plus two copies each of the wall and rectangle emitters. This is that code,
 * written once.
 *
 * <p>All coordinates are world-space. Callers are expected to have already translated the matrix
 * stack by the negated camera position, which is what puts world coordinates in view space:
 * <pre>{@code
 * matrices.push();
 * matrices.translate(-cam.x, -cam.y, -cam.z);
 * Matrix4f mat = matrices.peek().getPositionMatrix();
 * ...
 * matrices.pop();
 * }</pre>
 */
public final class Shapes {

    /** Full block, inset slightly so a box on a solid block does not z-fight with its faces. */
    public static final double BLOCK_INSET = 0.002;

    private Shapes() {
    }

    // ── Boxes ────────────────────────────────────────────────────────────────

    /**
     * Six translucent faces. Both windings are emitted per face, so the box reads the same from
     * inside and out without needing a no-cull pipeline.
     */
    public static void fillBox(VertexConsumer vc, Matrix4f mat, AABB box,
                               float r, float g, float b, float a) {
        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

        // Down / up
        quad(vc, mat, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        quad(vc, mat, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, r, g, b, a);
        // North / south
        quad(vc, mat, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, a);
        quad(vc, mat, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2, r, g, b, a);
        // West / east
        quad(vc, mat, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, r, g, b, a);
        quad(vc, mat, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);
    }

    /** The twelve edges of a box. */
    public static void outlineBox(VertexConsumer vc, Matrix4f mat, AABB box,
                                  float r, float g, float b, float a, float width) {
        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

        // Bottom face
        line(vc, mat, x1, y1, z1, x2, y1, z1, r, g, b, a, width);
        line(vc, mat, x2, y1, z1, x2, y1, z2, r, g, b, a, width);
        line(vc, mat, x2, y1, z2, x1, y1, z2, r, g, b, a, width);
        line(vc, mat, x1, y1, z2, x1, y1, z1, r, g, b, a, width);
        // Top face
        line(vc, mat, x1, y2, z1, x2, y2, z1, r, g, b, a, width);
        line(vc, mat, x2, y2, z1, x2, y2, z2, r, g, b, a, width);
        line(vc, mat, x2, y2, z2, x1, y2, z2, r, g, b, a, width);
        line(vc, mat, x1, y2, z2, x1, y2, z1, r, g, b, a, width);
        // Verticals
        line(vc, mat, x1, y1, z1, x1, y2, z1, r, g, b, a, width);
        line(vc, mat, x2, y1, z1, x2, y2, z1, r, g, b, a, width);
        line(vc, mat, x2, y1, z2, x2, y2, z2, r, g, b, a, width);
        line(vc, mat, x1, y1, z2, x1, y2, z2, r, g, b, a, width);
    }

    /**
     * The box to draw for a block, in world space, using its real outline shape — so a hopper
     * looks like a hopper and a chest has no floating gap around it. Falls back to a full cube
     * for blocks with an empty outline.
     */
    public static AABB blockBox(BlockState state, BlockGetter view, BlockPos pos) {
        try {
            VoxelShape shape = state.getShape(view, pos);
            if (shape != null && !shape.isEmpty()) {
                return shape.bounds().move(pos.getX(), pos.getY(), pos.getZ()).inflate(BLOCK_INSET);
            }
        } catch (Exception e) {
            // A block whose shape depends on state we do not have; a full cube is a fine answer.
        }
        return new AABB(pos).inflate(BLOCK_INSET);
    }

    /** Union of two boxes — for drawing a double chest, or any paired block, as one box. */
    public static AABB union(AABB a, AABB b) {
        return new AABB(
            Math.min(a.minX, b.minX), Math.min(a.minY, b.minY), Math.min(a.minZ, b.minZ),
            Math.max(a.maxX, b.maxX), Math.max(a.maxY, b.maxY), Math.max(a.maxZ, b.maxZ)
        );
    }

    // ── Walls and rectangles ─────────────────────────────────────────────────

    /** Vertical quad in the plane {@code z = const}, spanning {@code x1..x2} and {@code y0..y1}. */
    public static void wallAlongX(VertexConsumer vc, Matrix4f mat,
                                  float x1, float x2, float z, float y0, float y1,
                                  float r, float g, float b, float a) {
        quad(vc, mat, x1, y0, z, x2, y0, z, x2, y1, z, x1, y1, z, r, g, b, a);
    }

    /** Vertical quad in the plane {@code x = const}, spanning {@code z1..z2} and {@code y0..y1}. */
    public static void wallAlongZ(VertexConsumer vc, Matrix4f mat,
                                  float z1, float z2, float x, float y0, float y1,
                                  float r, float g, float b, float a) {
        quad(vc, mat, x, y0, z1, x, y0, z2, x, y1, z2, x, y1, z1, r, g, b, a);
    }

    /** Axis-aligned rectangle outline at a fixed y. */
    public static void rectXZ(VertexConsumer vc, Matrix4f mat,
                              float x1, float x2, float z1, float z2, float y,
                              float r, float g, float b, float a, float width) {
        line(vc, mat, x1, y, z1, x2, y, z1, r, g, b, a, width);
        line(vc, mat, x2, y, z1, x2, y, z2, r, g, b, a, width);
        line(vc, mat, x2, y, z2, x1, y, z2, r, g, b, a, width);
        line(vc, mat, x1, y, z2, x1, y, z1, r, g, b, a, width);
    }

    /** A vertical translucent column, for marking a position from a distance. */
    public static void beam(VertexConsumer vc, Matrix4f mat,
                            double cx, double cz, float y0, float y1, float halfWidth,
                            float r, float g, float b, float a) {
        float x1 = (float) (cx - halfWidth), x2 = (float) (cx + halfWidth);
        float z1 = (float) (cz - halfWidth), z2 = (float) (cz + halfWidth);
        // Four sides, each emitted both ways so the beam is visible from any angle.
        quad(vc, mat, x1, y0, z1, x1, y1, z1, x2, y1, z1, x2, y0, z1, r, g, b, a);
        quad(vc, mat, x2, y0, z1, x2, y1, z1, x1, y1, z1, x1, y0, z1, r, g, b, a);
        quad(vc, mat, x2, y0, z2, x2, y1, z2, x1, y1, z2, x1, y0, z2, r, g, b, a);
        quad(vc, mat, x1, y0, z2, x1, y1, z2, x2, y1, z2, x2, y0, z2, r, g, b, a);
        quad(vc, mat, x1, y0, z2, x1, y1, z2, x1, y1, z1, x1, y0, z1, r, g, b, a);
        quad(vc, mat, x1, y0, z1, x1, y1, z1, x1, y1, z2, x1, y0, z2, r, g, b, a);
        quad(vc, mat, x2, y0, z1, x2, y1, z1, x2, y1, z2, x2, y0, z2, r, g, b, a);
        quad(vc, mat, x2, y0, z2, x2, y1, z2, x2, y1, z1, x2, y0, z1, r, g, b, a);
    }

    // ── Primitives ───────────────────────────────────────────────────────────

    /** One quad from four corners, wound in the order given. */
    public static void quad(VertexConsumer vc, Matrix4f mat,
                            float ax, float ay, float az, float bx, float by, float bz,
                            float cx, float cy, float cz, float dx, float dy, float dz,
                            float r, float g, float b, float a) {
        vc.addVertex(mat, ax, ay, az).setColor(r, g, b, a);
        vc.addVertex(mat, bx, by, bz).setColor(r, g, b, a);
        vc.addVertex(mat, cx, cy, cz).setColor(r, g, b, a);
        vc.addVertex(mat, dx, dy, dz).setColor(r, g, b, a);
    }

    /**
     * One line segment.
     *
     * <p>The normal is the normalised direction of the segment, which the line shader needs to
     * expand the segment into a screen-space quad — get it wrong and lines render as slivers or
     * vanish entirely.
     */
    public static void line(VertexConsumer vc, Matrix4f mat,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            float r, float g, float b, float a, float width) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0) { dx /= len; dy /= len; dz /= len; }
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a).setNormal(dx, dy, dz).setLineWidth(width);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a).setNormal(dx, dy, dz).setLineWidth(width);
    }

    // ── Colour helpers ───────────────────────────────────────────────────────

    /** Red channel of an {@code 0xRRGGBB} colour, as 0..1. */
    public static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255f;
    }

    /** Green channel of an {@code 0xRRGGBB} colour, as 0..1. */
    public static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255f;
    }

    /** Blue channel of an {@code 0xRRGGBB} colour, as 0..1. */
    public static float blue(int rgb) {
        return (rgb & 0xFF) / 255f;
    }
}
