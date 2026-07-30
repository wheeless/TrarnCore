package net.simdistance.config;

/**
 * User settings for SimDistance. Persisted as JSON via {@link ConfigManager}
 * and edited in-game through the ModMenu / Cloth Config screen.
 */
public class SimDistanceConfig {

    /** Master on/off. Flipped by the hotkey and persisted so it survives restarts. */
    public boolean enabled = true;

    /**
     * When true, the border is drawn at the live simulation distance reported by the
     * server (works in singleplayer too). When false, {@link #manualChunkRadius} is used.
     */
    public boolean useServerSimulationDistance = true;

    /** Chunk radius used when {@link #useServerSimulationDistance} is false, or as a fallback. */
    public int manualChunkRadius = 8;

    /** Wall tint as 0xRRGGBB. Alpha is taken from {@link #wallOpacity}. */
    public int wallColor = 0xFF3030;

    /** Wall opacity as a percentage, 0–100. */
    public int wallOpacity = 22;

    /** Draw the wall across the full world height. When false, use {@link #verticalRadius}. */
    public boolean fullWorldHeight = true;

    /** Half-height (blocks) of the wall around the player's Y when not full world height. */
    public int verticalRadius = 48;

    /** Draw a crisp red outline along the top and bottom edges of the wall. */
    public boolean drawEdgeLines = true;

    /** Draw the internal per-chunk grid on the floor within the region (off by default). */
    public boolean showChunkGrid = false;
}
