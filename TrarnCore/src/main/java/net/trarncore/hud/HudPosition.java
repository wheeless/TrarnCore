package net.trarncore.hud;

/**
 * Where a HUD element sits: an anchor, plus a pixel offset from it.
 *
 * <p>A plain mutable class rather than a record because it is serialized straight into a mod's
 * JSON config alongside its other settings, and it is edited in place by the placement screen.
 */
public class HudPosition {

    /** Stored as a name so a hand-edited config reads sensibly and survives enum reordering. */
    public String anchor = HudAnchor.TOP_LEFT.name();

    public int offsetX = 4;
    public int offsetY = 4;

    public HudPosition() {
    }

    public HudPosition(HudAnchor anchor, int offsetX, int offsetY) {
        this.anchor = anchor.name();
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public HudAnchor anchorEnum() {
        try {
            return HudAnchor.valueOf(anchor);
        } catch (IllegalArgumentException | NullPointerException e) {
            return HudAnchor.TOP_LEFT;
        }
    }

    public int resolveX(int screenWidth, int elementWidth) {
        return anchorEnum().originX(screenWidth, elementWidth) + offsetX;
    }

    public int resolveY(int screenHeight, int elementHeight) {
        return anchorEnum().originY(screenHeight, elementHeight) + offsetY;
    }

    /** Re-pins to the nearest anchor for a dropped position, keeping the remainder as the offset. */
    public void moveTo(int x, int y, int screenWidth, int screenHeight, int elementWidth, int elementHeight) {
        HudAnchor best = HudAnchor.nearest(x, y, screenWidth, screenHeight, elementWidth, elementHeight);
        this.anchor = best.name();
        this.offsetX = x - best.originX(screenWidth, elementWidth);
        this.offsetY = y - best.originY(screenHeight, elementHeight);
    }

    /**
     * Clamps the offset so the element cannot be stranded outside the window.
     *
     * <p>Call from a config's {@code validate()}. A hand-edited offset of 9999, or a placement
     * made at a much larger resolution, would otherwise leave a panel permanently invisible with
     * no way to find it again short of deleting the config.
     */
    public void clampInto(int screenWidth, int screenHeight, int elementWidth, int elementHeight) {
        int x = Math.clamp(resolveX(screenWidth, elementWidth), 0, Math.max(0, screenWidth - elementWidth));
        int y = Math.clamp(resolveY(screenHeight, elementHeight), 0, Math.max(0, screenHeight - elementHeight));
        moveTo(x, y, screenWidth, screenHeight, elementWidth, elementHeight);
    }

    public HudPosition copy() {
        return new HudPosition(anchorEnum(), offsetX, offsetY);
    }

    @Override
    public String toString() {
        return anchorEnum().label() + " " + (offsetX >= 0 ? "+" : "") + offsetX
            + "," + (offsetY >= 0 ? "+" : "") + offsetY;
    }
}
