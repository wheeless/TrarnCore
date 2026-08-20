package net.easyportallinker.config;

import net.easyportallinker.portal.PortalTarget;

/**
 * User settings for EasyPortalLinker, plus the currently remembered portal selection.
 * Persisted as JSON via {@link ConfigManager} and edited in-game through ModMenu / Cloth Config.
 *
 * <p>The selection lives here (rather than only in memory) so it survives the trip between
 * dimensions and a client restart — you can select in the Overworld, quit, come back, and the
 * Nether guide is still waiting for you.
 */
public class EasyPortalLinkerConfig {

    /** Master on/off. Flipped by the toggle hotkey and persisted so it survives restarts. */
    public boolean enabled = true;

    /** Item that selects a portal on right-click. Item id, e.g. {@code "minecraft:wooden_shovel"}. */
    public String selectionItem = "minecraft:wooden_shovel";

    /** Sneak + right-click with the selection item clears the current selection. */
    public boolean requireSneakToClear = true;

    /** How far (blocks) the selection ray reaches when looking for a portal. */
    public int selectReach = 6;

    // ── Target Y ─────────────────────────────────────────────────────────────
    /**
     * Pin the ghost frame's base to {@link #lockedTargetY} instead of tracking the player's feet.
     * Handy for a Nether hub built at one consistent level.
     */
    public boolean lockTargetY = false;
    /** Fixed base Y for the ghost frame when {@link #lockTargetY} is on. Clamped to the world. */
    public int lockedTargetY = 120;

    // ── Target guide (the counterpart you need to build) ─────────────────────
    /** Guide colour as 0xRRGGBB (portal purple by default). Alpha comes from opacity. */
    public int targetColor = 0xA24BF0;
    /** Translucent fill opacity for the target guide, 0–100. */
    public int targetOpacity = 28;

    // ── Source highlight (the portal you selected) ───────────────────────────
    /** Highlight colour for the selected source portal, 0xRRGGBB (teal by default). */
    public int sourceColor = 0x2BE0C0;
    /** Translucent fill opacity for the source highlight, 0–100. */
    public int sourceOpacity = 22;

    // ── What to draw ─────────────────────────────────────────────────────────
    /** Draw the full-height column from bedrock to build height at the target X/Z. */
    public boolean showColumn = true;
    /** Draw the axis-matched ghost outline of the obsidian frame at the recommended Y. */
    public boolean showGhostFrame = true;
    /** Draw the coordinates floating in the world at the target. */
    public boolean showFloatingCoords = true;
    /** Draw a compact coordinate readout on the HUD. */
    public boolean showHudCoords = true;
    /** Highlight the selected portal itself while you are in its dimension (confirmation). */
    public boolean showSourceHighlight = true;
    /** Draw crisp outline edges on the column and boxes. */
    public boolean drawEdgeLines = true;

    // ── Portal highlighting (the ESP) ────────────────────────────────────────
    /** Master on/off for the portal highlights. Flipped by the hotkey and persisted. */
    public boolean portalEsp = false;

    /** Highlight colour for lit portals, 0xRRGGBB. Portal purple. */
    public int portalEspColor = 0xA24BF0;
    /** Translucent fill opacity for lit portals, 0-100. */
    public int portalEspOpacity = 30;

    /**
     * Highlight colour for complete but unlit frames, 0xRRGGBB.
     *
     * <p>Deliberately a duller purple than {@link #portalEspColor}: the two want to read as the
     * same family at a glance while still being told apart, since the whole reason to show unlit
     * frames is that they are the ones needing attention.
     */
    public int unlitFrameColor = 0x6A3FA0;
    /** Translucent fill opacity for unlit frames, 0-100. */
    public int unlitFrameOpacity = 18;

    /** Draw the translucent fill. */
    public boolean portalEspFill = true;
    /** Draw the outline edges. */
    public boolean portalEspOutline = true;
    /** Outline thickness in pixels. */
    public float portalEspOutlineWidth = 2.0f;
    /** Draw highlights through walls. */
    public boolean portalEspSeeThrough = true;

    /** Horizontal radius, in chunks, to scan and draw within. */
    public int portalEspChunkRadius = 8;
    /** Vertical cutoff in blocks. 0 means the whole column, which is what a chunk radius implies. */
    public int portalEspVerticalLimit = 0;
    /** Hard cap on highlights drawn at once; the nearest survive. */
    public int maxRenderedPortals = 64;

    /**
     * Also highlight complete frames that have not been lit.
     *
     * <p>Off by default because it costs more than finding lit portals does: portal blocks are
     * rare enough that a palette check skips almost every chunk section, whereas obsidian is not,
     * and each candidate has to be validated against the real frame rules. Detection itself is
     * exact — it asks Minecraft the same question flint and steel does — but the sweep works
     * harder for it.
     */
    public boolean detectUnlitFrames = false;
    /** Smaller radius for unlit frames, since they are the expensive half of the sweep. */
    public int unlitFrameChunkRadius = 4;

    /**
     * The remembered portal selection, or {@code null} if nothing is selected. Serialized inline
     * with the rest of the config.
     */
    public PortalTarget selection = null;
}
