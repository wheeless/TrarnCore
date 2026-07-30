package net.easyportallinker.portal;

import net.minecraft.util.math.Direction;

/**
 * A recorded portal selection and its computed counterpart in the other dimension.
 *
 * <p>Plain public fields so Gson can (de)serialize it straight into the config file —
 * this is what lets a selection survive a dimension change and a client restart.
 *
 * <p>All coordinates are integer block positions. The "anchor" is the lower / north-west
 * corner block of the portal's <em>interior</em> (the purple portal blocks), i.e. the
 * minimum X/Y/Z among them. {@link #width} is the interior span along {@link #axis},
 * {@link #height} the interior vertical span.
 */
public class PortalTarget {

    // ── Source portal (what the player selected) ─────────────────────────────
    /** Dimension id of the selected portal, e.g. {@code "minecraft:overworld"}. */
    public String sourceDim;
    public int sourceX;
    public int sourceY;
    public int sourceZ;
    /** Portal orientation: {@code "X"} or {@code "Z"} (the horizontal axis the portal spans). */
    public String axis;
    /** Interior width along {@link #axis}, in blocks (2 for a vanilla minimum portal). */
    public int width;
    /** Interior height, in blocks (3 for a vanilla minimum portal). */
    public int height;

    // ── Destination (computed counterpart) ───────────────────────────────────
    /** Dimension id where the counterpart should be built, or {@code null} if unsupported. */
    public String destDim;
    /** Recommended interior anchor X in the destination. */
    public int destX;
    /** Recommended interior anchor Z in the destination. */
    public int destZ;

    public PortalTarget() {
        // for Gson
    }

    public Direction.Axis axisEnum() {
        return "Z".equalsIgnoreCase(axis) ? Direction.Axis.Z : Direction.Axis.X;
    }

    /** True if this selection has a valid, supported destination. */
    public boolean hasDestination() {
        return destDim != null;
    }

    public boolean isSourceDim(String dimId) {
        return sourceDim != null && sourceDim.equals(dimId);
    }

    public boolean isDestDim(String dimId) {
        return destDim != null && destDim.equals(dimId);
    }
}
