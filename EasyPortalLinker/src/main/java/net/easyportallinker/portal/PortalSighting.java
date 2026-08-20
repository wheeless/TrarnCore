package net.easyportallinker.portal;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * One portal found by the sweep.
 *
 * @param box   world-space bounds. For a lit portal this is the portal blocks themselves; for an
 *              unlit frame it is the empty interior, so both highlight the same part of the
 *              structure and a frame does not visually jump when you light it.
 * @param axis  the axis the portal's width runs along
 * @param lit   true when portal blocks are present, false for a complete but unlit frame
 */
public record PortalSighting(AABB box, Direction.Axis axis, boolean lit) {

    public double centerX() {
        return (box.minX + box.maxX) / 2.0;
    }

    public double centerY() {
        return (box.minY + box.maxY) / 2.0;
    }

    public double centerZ() {
        return (box.minZ + box.maxZ) / 2.0;
    }

    /** Horizontal distance only — chunks load as full columns, so height is not a range question. */
    public double horizontalDistanceSqTo(double x, double z) {
        double dx = centerX() - x;
        double dz = centerZ() - z;
        return dx * dx + dz * dz;
    }

    public double distanceSqTo(double x, double y, double z) {
        double dx = centerX() - x;
        double dy = centerY() - y;
        double dz = centerZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
