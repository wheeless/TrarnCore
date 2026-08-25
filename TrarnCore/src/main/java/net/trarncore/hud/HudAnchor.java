package net.trarncore.hud;

/**
 * Which corner or edge of the screen a HUD element is measured from.
 *
 * <p>Storing an anchor plus a small offset, rather than raw screen coordinates, is what makes a
 * placement survive a resolution or GUI-scale change. A panel pinned to {@code BOTTOM_RIGHT} stays
 * in the bottom right when the window is resized; the same panel stored as {@code x=1850} ends up
 * off-screen the first time someone plays windowed.
 */
public enum HudAnchor {

    TOP_LEFT     (0.0f, 0.0f, "Top left"),
    TOP_CENTER   (0.5f, 0.0f, "Top centre"),
    TOP_RIGHT    (1.0f, 0.0f, "Top right"),
    MIDDLE_LEFT  (0.0f, 0.5f, "Middle left"),
    MIDDLE_CENTER(0.5f, 0.5f, "Centre"),
    MIDDLE_RIGHT (1.0f, 0.5f, "Middle right"),
    BOTTOM_LEFT  (0.0f, 1.0f, "Bottom left"),
    BOTTOM_CENTER(0.5f, 1.0f, "Bottom centre"),
    BOTTOM_RIGHT (1.0f, 1.0f, "Bottom right");

    private final float fx;
    private final float fy;
    private final String label;

    HudAnchor(float fx, float fy, String label) {
        this.fx = fx;
        this.fy = fy;
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Screen X of this anchor for an element of the given width. */
    public int originX(int screenWidth, int elementWidth) {
        return Math.round((screenWidth - elementWidth) * fx);
    }

    /** Screen Y of this anchor for an element of the given height. */
    public int originY(int screenHeight, int elementHeight) {
        return Math.round((screenHeight - elementHeight) * fy);
    }

    /**
     * The anchor whose origin is nearest a point, so a dragged element re-pins to whichever
     * corner it was dropped closest to and keeps a small offset from there.
     *
     * <p>Without this, dragging a panel to the bottom right would leave it anchored top-left with
     * a huge offset — and it would fly off-screen the moment the window changed size. Re-pinning
     * on drop is what makes the placement mean what it looks like.
     */
    public static HudAnchor nearest(int x, int y, int screenWidth, int screenHeight,
                                    int elementWidth, int elementHeight) {
        HudAnchor best = TOP_LEFT;
        long bestDistance = Long.MAX_VALUE;

        for (HudAnchor candidate : values()) {
            long dx = x - candidate.originX(screenWidth, elementWidth);
            long dy = y - candidate.originY(screenHeight, elementHeight);
            long distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }
}
