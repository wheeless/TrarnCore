package net.easyportallinker.portal;

/**
 * The vanilla Nether-portal linking math, in one place.
 *
 * <p>When an entity travels between the Overworld and the Nether the game multiplies its
 * horizontal position by the ratio of the two dimensions' coordinate scales. The Overworld
 * has scale 1, the Nether has scale 8, so:
 *
 * <ul>
 *   <li>Overworld &rarr; Nether: {@code x, z} are divided by 8 (floored).</li>
 *   <li>Nether &rarr; Overworld: {@code x, z} are multiplied by 8.</li>
 * </ul>
 *
 * The game then searches within 128 blocks of that scaled point for an existing portal and
 * links to the closest one, creating a fresh portal only if none is found. Building your
 * counterpart portal exactly on the scaled point makes it the closest candidate, which is
 * what gives a clean, predictable link.
 */
public final class LinkMath {

    public static final String OVERWORLD = "minecraft:overworld";
    public static final String NETHER    = "minecraft:the_nether";

    /** Horizontal compression factor between the Overworld and the Nether. */
    public static final int NETHER_SCALE = 8;

    private LinkMath() {}

    /**
     * Fills in {@link PortalTarget#destDim}, {@link PortalTarget#destX} and
     * {@link PortalTarget#destZ} from the source portal. Leaves {@code destDim} null when the
     * source dimension has no supported counterpart (e.g. the End).
     *
     * @return true if a supported destination was computed
     */
    public static boolean computeDestination(PortalTarget t) {
        if (OVERWORLD.equals(t.sourceDim)) {
            t.destDim = NETHER;
            t.destX = Math.floorDiv(t.sourceX, NETHER_SCALE);
            t.destZ = Math.floorDiv(t.sourceZ, NETHER_SCALE);
            return true;
        }
        if (NETHER.equals(t.sourceDim)) {
            t.destDim = OVERWORLD;
            t.destX = t.sourceX * NETHER_SCALE;
            t.destZ = t.sourceZ * NETHER_SCALE;
            return true;
        }
        t.destDim = null;
        return false;
    }

    /**
     * A safe recommended interior-bottom Y for the counterpart portal: the source portal's Y,
     * clamped into a build-able band for the destination dimension (clear of the Nether's
     * bedrock floor and roof). This is an estimate used before the destination is loaded; the
     * renderer re-clamps it against the live world height once you are actually there.
     */
    public static int recommendedY(String destDim, int desiredY, int height) {
        int lo, hi;
        if (NETHER.equals(destDim)) {
            lo = 5;    // clear of the floor bedrock
            hi = 122;  // clear of the roof bedrock
        } else {
            lo = -63;  // just above the Overworld floor
            hi = 317;  // just below build height
        }
        int top = hi - Math.max(1, height);
        if (top < lo) top = lo;
        return Math.max(lo, Math.min(top, desiredY));
    }

    /** Short human label for a dimension id, e.g. "Nether". */
    public static String dimLabel(String dimId) {
        if (NETHER.equals(dimId)) return "Nether";
        if (OVERWORLD.equals(dimId)) return "Overworld";
        if (dimId == null) return "?";
        int c = dimId.indexOf(':');
        return c >= 0 ? dimId.substring(c + 1) : dimId;
    }
}
