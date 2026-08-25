package net.blowbyblow.config;

import net.trarncore.config.ValidatedConfig;
import net.trarncore.hud.HudAnchor;
import net.trarncore.hud.HudPosition;

public class BlowByBlowConfig implements ValidatedConfig {

    /** Master on/off. Flipped by the hotkey and persisted. */
    public boolean enabled = true;

    // ── Where the lines go ───────────────────────────────────────────────────

    /** Draw the feed as its own HUD panel. */
    public boolean showPanel = true;

    /**
     * Also send every line to local chat.
     *
     * <p>Off by default: a busy fight produces a line per hit, and chat keeps history, so leaving
     * this on buries anything a server actually said to you. It is here because a scrollback you
     * can read after the fact is genuinely useful — just not as the default.
     */
    public boolean showInChat = false;

    /** Where the panel sits. Edited by dragging, not by typing coordinates. */
    public HudPosition panelPosition = new HudPosition(HudAnchor.MIDDLE_LEFT, 4, -40);

    // ── Panel appearance ─────────────────────────────────────────────────────

    /** Lines held in the panel at once. */
    public int maxLines = 8;
    /** How long a line stays fully visible, in seconds. */
    public int holdSeconds = 8;
    /** How long the fade takes, in seconds. */
    public int fadeSeconds = 1;
    /** Newest line at the bottom, chat-style. Off puts newest at the top. */
    public boolean newestAtBottom = true;
    /** Panel background as 0xAARRGGBB. Alpha 0 draws no panel at all. */
    public int panelBackground = 0x50101014;
    /** Pixels between the panel edge and its text. */
    public int padding = 4;
    /** Extra pixels between lines. */
    public int lineSpacing = 1;
    /** Draw text with a drop shadow. */
    public boolean textShadow = true;

    // ── What to log ──────────────────────────────────────────────────────────

    /** Log fights between other parties that happen near you. */
    public boolean showBystanders = false;
    /** Log your own healing. */
    public boolean showHealing = false;
    /** Name the weapon used. */
    public boolean showWeapons = true;
    /** Radius in blocks to watch for damage on other entities. */
    public int trackRadius = 32;

    // ── Numbers ──────────────────────────────────────────────────────────────

    /** Show hearts rather than the half-heart points the game counts in. */
    public boolean showInHearts = true;

    /**
     * Prefix inferred amounts with {@code ~}.
     *
     * <p>On by default, and worth leaving on. Outgoing damage is read from an entity's synced
     * health, so it cannot see overkill — a mob on 2 hearts hit for 10 reports 2. The tilde is the
     * difference between a number that is wrong and a number that is honest about being a floor.
     */
    public boolean markInferredAmounts = true;

    // ── Floating numbers ─────────────────────────────────────────────────────

    /** Pop the damage number off the thing you hit. */
    public boolean floatingNumbers = true;
    /** How long a floating number lives, in milliseconds. */
    public int floatingLifetimeMillis = 1200;
    /** How far it drifts upward over its life, in blocks. */
    public float floatingRise = 0.9f;
    /** Text scale for floating numbers. */
    public float floatingScale = 0.025f;
    /** Draw floating numbers through walls. */
    public boolean floatingSeeThrough = false;
    /** Cap on floating numbers alive at once. */
    public int maxFloatingNumbers = 40;

    // ── Colours ──────────────────────────────────────────────────────────────

    /** 0xRRGGBB for the name of whatever hit you. */
    public int attackerColor = 0xFF7043;
    /** 0xRRGGBB for the name of whatever you hit. */
    public int victimColor = 0xFFD54F;
    /** 0xRRGGBB for weapon names. */
    public int weaponColor = 0x90A4AE;
    /** 0xRRGGBB for outgoing floating numbers. */
    public int floatingOutgoingColor = 0xFFFFFF;
    /** 0xRRGGBB for incoming floating numbers. */
    public int floatingIncomingColor = 0xFF5252;

    @Override
    public void validate() {
        maxLines = Math.clamp(maxLines, 1, 50);
        holdSeconds = Math.clamp(holdSeconds, 1, 120);
        fadeSeconds = Math.clamp(fadeSeconds, 0, 30);
        padding = Math.clamp(padding, 0, 20);
        lineSpacing = Math.clamp(lineSpacing, 0, 10);
        trackRadius = Math.clamp(trackRadius, 4, 128);

        floatingLifetimeMillis = Math.clamp(floatingLifetimeMillis, 200, 10000);
        floatingRise = Math.clamp(floatingRise, 0f, 6f);
        floatingScale = Math.clamp(floatingScale, 0.005f, 0.2f);
        maxFloatingNumbers = Math.clamp(maxFloatingNumbers, 1, 200);

        if (panelPosition == null) panelPosition = new HudPosition(HudAnchor.MIDDLE_LEFT, 4, -40);

        // A panel dragged at one resolution and loaded at a smaller one would otherwise sit off
        // screen with no way to reach it. Clamping needs a screen size, so it happens in
        // FeedRenderer where one is available; here we only guard against a broken anchor name.
        panelPosition.anchorEnum();
    }
}
