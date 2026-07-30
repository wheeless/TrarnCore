package net.containerutil.config;

import net.containerutil.container.ContainerKind;
import net.trarncore.config.ValidatedConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User settings for ContainerUtil. Persisted as JSON via {@link ConfigManager} and edited
 * in-game through the ModMenu / Cloth Config screen.
 *
 * <p>Per-kind settings live in maps keyed by {@link ContainerKind#id()} rather than as one
 * field per kind, so adding a container type in a future Minecraft version does not
 * invalidate anyone's existing config file.
 */
public class ContainerUtilConfig implements ValidatedConfig {

    // ── Master ───────────────────────────────────────────────────────────────

    /** Master on/off for the highlights. Flipped by the hotkey and persisted. */
    public boolean enabled = true;

    /** Record container contents when you open them. Turning this off leaves the existing index intact. */
    public boolean indexingEnabled = true;

    // ── Render distance & budget ─────────────────────────────────────────────

    /**
     * Highlights are drawn for containers within this many chunks of the player, measured
     * horizontally. Chunks load as full columns, so this deliberately does not constrain depth —
     * see {@link #verticalRenderLimit} if you want it to.
     */
    public int renderChunkRadius = 8;

    /**
     * Optional cap on how far above or below you a highlight can be, in blocks. {@code 0} means
     * no limit, matching the full height of a loaded chunk column — the default, because a chest
     * disappearing purely for being deep is surprising rather than useful.
     */
    public int verticalRenderLimit = 0;

    /**
     * Measure distances from the camera instead of the player's body. Off by default; turn it on
     * when using a freecam so highlights follow where you are viewing from rather than staying
     * clustered around your body. See {@link net.containerutil.render.ViewAnchor}.
     */
    public boolean anchorToCamera = false;

    /**
     * Hard cap on highlights per frame, nearest first. Prevents a mega-base sorting hall
     * from tanking the frame rate; raise it if you have the headroom.
     */
    public int maxRenderedContainers = 512;

    // ── Appearance ───────────────────────────────────────────────────────────

    /** Draw the translucent filled box. */
    public boolean drawFilled = true;

    /** Draw the crisp box outline. */
    public boolean drawOutline = true;

    /** Draw highlights through terrain. Off = they are hidden behind blocks like normal geometry. */
    public boolean seeThrough = true;

    /** Fill opacity (%) for a container we have never opened, or when fullness scaling is off. */
    public int baseFillOpacity = 18;

    /**
     * When true, the box fill gets more opaque the fuller the container is, ramping from
     * {@link #minFillOpacity} (empty) to {@link #maxFillOpacity} (full). Makes "which chest
     * still has room" readable at a glance.
     */
    public boolean fillScalesWithFullness = true;

    public int minFillOpacity = 6;
    public int maxFillOpacity = 45;

    /** Line width for box outlines. */
    public float outlineWidth = 2.0f;

    // ── Labels ───────────────────────────────────────────────────────────────

    /** Draw a floating label above each highlighted container. */
    public boolean showLabels = true;

    /** Labels are only drawn within this many blocks (they get illegible and expensive past that). */
    public int labelMaxDistance = 48;

    /** Include the "12/27" slot usage on the label. */
    public boolean showFillCounts = true;

    /** Include how long ago the contents were recorded on the label. */
    public boolean showLastSeenAge = false;

    // ── Staleness ────────────────────────────────────────────────────────────

    /** Contents older than this are flagged as stale in search results and on labels. 0 disables. */
    public int staleAfterDays = 14;

    /** Draw containers we have never opened dimmer than indexed ones. */
    public boolean dimUnopened = true;

    /**
     * Drop an indexed record when we are standing near its position and the block is
     * demonstrably gone. Without this the index slowly fills with lies.
     */
    public boolean autoPrune = true;

    /** Only prune within this many blocks — far enough to be useful, close enough that the chunk is certainly loaded. */
    public int pruneRadius = 24;

    // ── Peek ─────────────────────────────────────────────────────────────────

    /** Show a container's last-known contents when you look at it, without opening it. */
    public boolean peekEnabled = true;

    /** How far the peek raycast reaches, in blocks. */
    public int peekDistance = 12;

    /** Cap on how many item lines the peek panel shows before summarising the rest. */
    public int peekMaxLines = 10;

    // ── Tracking ─────────────────────────────────────────────────────────────

    /** Draw a vertical beam on the container you are currently tracking from a search result. */
    public boolean trackBeam = true;

    /** Draw the distance/direction readout at the top of the screen while tracking. */
    public boolean trackHud = true;

    /** Colour of the tracked-container beam and outline, as 0xRRGGBB. */
    public int trackColor = 0x00E676;

    /** Stop tracking automatically once you get this close, in blocks. 0 = never auto-clear. */
    public int trackClearDistance = 3;

    // ── Search ───────────────────────────────────────────────────────────────

    /** Highlight every container matching the last search, in {@link #searchHighlightColor}. */
    public boolean highlightSearchResults = true;

    /** Colour used for containers matching the active search, as 0xRRGGBB. */
    public int searchHighlightColor = 0xFFFFFF;

    /** Maximum rows the search screen will render. */
    public int searchResultLimit = 200;

    // ── Per-kind maps ────────────────────────────────────────────────────────

    /** Kind id → 0xRRGGBB. Missing entries fall back to {@link ContainerKind#defaultColor()}. */
    public Map<String, Integer> kindColors = new LinkedHashMap<>();

    /** Kind id → whether it is highlighted at all. Missing entries default to true. */
    public Map<String, Boolean> kindEnabled = new LinkedHashMap<>();

    // ── Accessors ────────────────────────────────────────────────────────────

    public int colorOf(ContainerKind kind) {
        Integer c = kindColors.get(kind.id());
        return c != null ? (c & 0xFFFFFF) : kind.defaultColor();
    }

    public void setColorOf(ContainerKind kind, int rgb) {
        kindColors.put(kind.id(), rgb & 0xFFFFFF);
    }

    public boolean isKindEnabled(ContainerKind kind) {
        Boolean b = kindEnabled.get(kind.id());
        return b == null || b;
    }

    public void setKindEnabled(ContainerKind kind, boolean value) {
        kindEnabled.put(kind.id(), value);
    }

    /** Fills in any kind absent from the maps, so a config written by an older version stays complete. */
    public void fillDefaults() {
        if (kindColors == null) kindColors = new LinkedHashMap<>();
        if (kindEnabled == null) kindEnabled = new LinkedHashMap<>();
        for (ContainerKind kind : ContainerKind.values()) {
            kindColors.putIfAbsent(kind.id(), kind.defaultColor());
            kindEnabled.putIfAbsent(kind.id(), true);
        }
    }

    /**
     * Runs after every load and before every save via TrarnCore's config layer: fills in kinds
     * added since the file was written, then clamps every numeric field into a sane range — a hand-edited config should not be able to wedge the renderer. */
    @Override
    public void validate() {
        fillDefaults();
        renderChunkRadius = clampInt(renderChunkRadius, 1, 32);
        // 0 stays 0 — it is the "no limit" sentinel, not a small limit.
        verticalRenderLimit = clampInt(verticalRenderLimit, 0, 1024);
        maxRenderedContainers = clampInt(maxRenderedContainers, 16, 8192);
        baseFillOpacity = clampInt(baseFillOpacity, 0, 100);
        minFillOpacity = clampInt(minFillOpacity, 0, 100);
        maxFillOpacity = clampInt(maxFillOpacity, 0, 100);
        if (minFillOpacity > maxFillOpacity) {
            int swap = minFillOpacity;
            minFillOpacity = maxFillOpacity;
            maxFillOpacity = swap;
        }
        outlineWidth = Math.max(0.5f, Math.min(8f, outlineWidth));
        labelMaxDistance = clampInt(labelMaxDistance, 4, 256);
        staleAfterDays = clampInt(staleAfterDays, 0, 3650);
        pruneRadius = clampInt(pruneRadius, 4, 128);
        peekDistance = clampInt(peekDistance, 2, 64);
        peekMaxLines = clampInt(peekMaxLines, 1, 54);
        trackClearDistance = clampInt(trackClearDistance, 0, 64);
        searchResultLimit = clampInt(searchResultLimit, 10, 2000);
        trackColor &= 0xFFFFFF;
        searchHighlightColor &= 0xFFFFFF;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
