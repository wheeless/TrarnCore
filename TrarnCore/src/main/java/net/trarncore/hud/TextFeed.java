package net.trarncore.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A scrolling feed of short lines drawn on the HUD.
 *
 * <p>The generic half of a combat log, a pickup log, an event ticker — a bounded ring of
 * timestamped lines that fade out on their own. What each line <em>says</em> is the owning mod's
 * business; this only holds and draws them.
 *
 * <p>Wall-clock timing rather than tick counting, so a line's dwell time is the same whether the
 * game is running at 20 TPS or stuttering, and so the fade keeps moving on a paused single-player
 * world instead of freezing mid-way.
 */
public class TextFeed {

    /** One line, with the moment it was added. */
    private record Entry(Component text, long addedAtMillis) {
    }

    private final Deque<Entry> entries = new ArrayDeque<>();

    private int capacity = 100;
    private long holdMillis = 8000;
    private long fadeMillis = 1000;
    private boolean newestAtBottom = true;

    // ── Configuration ────────────────────────────────────────────────────────

    /** Maximum lines retained. Oldest are dropped first. */
    public TextFeed capacity(int capacity) {
        this.capacity = Math.max(1, capacity);
        trim();
        return this;
    }

    /** How long a line stays fully opaque before it begins to fade. */
    public TextFeed hold(long millis) {
        this.holdMillis = Math.max(0, millis);
        return this;
    }

    /** How long the fade itself takes. Zero makes lines vanish outright. */
    public TextFeed fade(long millis) {
        this.fadeMillis = Math.max(0, millis);
        return this;
    }

    /**
     * Whether new lines appear at the bottom (chat-like) or the top.
     *
     * <p>Worth exposing rather than picking one: a feed anchored to the bottom of the screen wants
     * to grow upward from its anchor, and one anchored to the top wants the opposite, or the panel
     * appears to crawl across the screen as it fills.
     */
    public TextFeed newestAtBottom(boolean newestAtBottom) {
        this.newestAtBottom = newestAtBottom;
        return this;
    }

    // ── Content ──────────────────────────────────────────────────────────────

    public void add(Component line) {
        entries.addLast(new Entry(line, System.currentTimeMillis()));
        trim();
    }

    public void clear() {
        entries.clear();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private void trim() {
        while (entries.size() > capacity) {
            entries.removeFirst();
        }
    }

    /** Lines still within their hold-plus-fade window, oldest first. */
    private List<Entry> live(long now) {
        List<Entry> visible = new ArrayList<>();
        long lifetime = holdMillis + fadeMillis;
        for (Entry entry : entries) {
            if (now - entry.addedAtMillis() < lifetime) visible.add(entry);
        }
        return visible;
    }

    /** 0-255 alpha for a line of the given age. */
    private int alphaFor(long ageMillis) {
        if (ageMillis <= holdMillis) return 255;
        if (fadeMillis <= 0) return 0;
        float remaining = 1f - (ageMillis - holdMillis) / (float) fadeMillis;
        return Math.clamp(Math.round(remaining * 255f), 0, 255);
    }

    // ── Measuring and drawing ────────────────────────────────────────────────

    /** Width of the widest live line, plus padding. Zero when nothing is showing. */
    public int measureWidth(Font font, int padding) {
        long now = System.currentTimeMillis();
        int widest = 0;
        for (Entry entry : live(now)) {
            widest = Math.max(widest, font.width(entry.text()));
        }
        return widest == 0 ? 0 : widest + padding * 2;
    }

    /** Height for the number of live lines, plus padding. Zero when nothing is showing. */
    public int measureHeight(Font font, int padding, int lineSpacing) {
        int count = live(System.currentTimeMillis()).size();
        return count == 0 ? 0 : count * (font.lineHeight + lineSpacing) - lineSpacing + padding * 2;
    }

    /**
     * Draws the feed with its top-left corner at {@code x, y}.
     *
     * @param backgroundArgb panel colour; alpha 0 draws no panel. Its alpha is scaled by the
     *                       most-opaque live line so the panel fades out with its content rather
     *                       than lingering as an empty box.
     */
    public void render(GuiGraphicsExtractor context, Font font, int x, int y,
                       int padding, int lineSpacing, int backgroundArgb, boolean shadow) {
        long now = System.currentTimeMillis();
        List<Entry> visible = live(now);
        if (visible.isEmpty()) return;

        if (!newestAtBottom) {
            List<Entry> reversed = new ArrayList<>(visible);
            java.util.Collections.reverse(reversed);
            visible = reversed;
        }

        int width = measureWidth(font, padding);
        int height = measureHeight(font, padding, lineSpacing);

        if (((backgroundArgb >>> 24) & 0xFF) != 0) {
            int strongest = 0;
            for (Entry entry : visible) {
                strongest = Math.max(strongest, alphaFor(now - entry.addedAtMillis()));
            }
            int panelAlpha = ((backgroundArgb >>> 24) & 0xFF) * strongest / 255;
            context.fill(x, y, x + width, y + height,
                (panelAlpha << 24) | (backgroundArgb & 0x00FFFFFF));
        }

        int lineY = y + padding;
        for (Entry entry : visible) {
            int alpha = alphaFor(now - entry.addedAtMillis());
            if (alpha > 0) {
                // Text colour comes from the Component's own style; only alpha is applied here,
                // so a mod can colour its lines however it likes and still get the fade.
                context.text(font, entry.text(), x + padding, lineY, (alpha << 24) | 0x00FFFFFF, shadow);
            }
            lineY += font.lineHeight + lineSpacing;
        }
    }
}
