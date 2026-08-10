package net.containerutil.render;

import net.containerutil.ContainerUtil;
import net.containerutil.config.ConfigManager;
import net.containerutil.config.ContainerUtilConfig;
import net.containerutil.data.ContainerRecord;
import net.containerutil.data.ItemEntry;
import net.containerutil.data.WorldIdentity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen-space overlays: the navigation readout for the container you are tracking, and the
 * peek panel showing a container's last-known contents when you look at it.
 */
public class HudRenderer {

    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_DIM = 0xFF9AA0A6;
    private static final int COLOR_STALE = 0xFFFFB74D;
    private static final int COLOR_TRACK = 0xFF00E676;

    private static final int PANEL_BG = 0xD2101014;
    private static final int PANEL_BORDER = 0xFF2A2A38;

    /** Eight-way arrows, indexed by relative bearing in 45° steps starting at "straight ahead". */
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static final Identifier HUD_ELEMENT_ID =
        Identifier.fromNamespaceAndPath(ContainerUtil.MOD_ID, "overlay");

    private static long lastError = 0;

    public static void register() {
        // HudElementRegistry rather than the older HudRenderCallback: that callback is
        // deprecated, and deprecated Fabric API is the first thing to disappear across a major
        // Minecraft bump.
        HudElementRegistry.addLast(HUD_ELEMENT_ID, (context, tickCounter) -> {
            try {
                renderHud(context);
            } catch (Exception e) {
                long now = System.currentTimeMillis();
                if (now - lastError > 5000) {
                    lastError = now;
                    ContainerUtil.LOGGER.error("[ContainerUtil] HUD render crashed (suppressing repeats for 5s)", e);
                }
            }
        });
    }

    private static void renderHud(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        // Nothing to say while a menu is up — the search screen draws its own chrome.
        if (client.screen != null) return;

        ContainerUtilConfig config = ConfigManager.get();

        if (config.trackHud) renderTracking(context, client, config);
        if (config.peekEnabled && ContainerUtil.enabled) renderPeek(context, client, config);
    }

    // ── Tracking readout ─────────────────────────────────────────────────────

    private static void renderTracking(GuiGraphicsExtractor context, Minecraft client, ContainerUtilConfig config) {
        ContainerRecord tracked = TrackedContainer.get();
        if (tracked == null) return;

        Font font = client.font;
        String dim = WorldIdentity.currentDimension();

        String line;
        if (dim != null && !dim.equals(tracked.dim)) {
            line = "▶ " + tracked.displayName() + " is in " + tracked.shortDim()
                + "  (" + tracked.coordsString() + ")";
        } else {
            net.minecraft.world.phys.Vec3 anchor = ViewAnchor.origin(client);
            double dx = tracked.centerX() - anchor.x;
            double dz = tracked.centerZ() - anchor.z;
            double distance = Math.sqrt(dx * dx + dz * dz);

            String item = TrackedContainer.itemLabel();
            String prefix = item != null && !item.isBlank() ? item + "  →  " : "";
            line = arrowFor(client, dx, dz) + "  " + prefix + tracked.displayName()
                + "  " + (int) Math.round(distance) + "m  (" + tracked.coordsString() + ")";
        }

        int width = font.width(line);
        int x = (client.getWindow().getGuiScaledWidth() - width) / 2;
        int y = 6;

        context.fill(x - 5, y - 4, x + width + 5, y + font.lineHeight + 3, PANEL_BG);
        context.fill(x - 5, y - 4, x + width + 5, y - 3, PANEL_BORDER);
        context.text(client.font, line, x, y, COLOR_TRACK, false);
    }

    /**
     * Picks an arrow glyph for the bearing to the target relative to where the player is facing.
     *
     * <p>Minecraft yaw is 0 at south and increases clockwise, so the bearing is computed as
     * {@code atan2(-dx, dz)} to line the arrow up with the player's own facing before the
     * difference is bucketed into eight directions.
     */
    private static String arrowFor(Minecraft client, double dx, double dz) {
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = targetYaw - ViewAnchor.yaw(client);
        relative = ((relative % 360) + 360) % 360;
        int index = (int) Math.round(relative / 45.0) % 8;
        return ARROWS[index];
    }

    // ── Peek panel ───────────────────────────────────────────────────────────

    private static void renderPeek(GuiGraphicsExtractor context, Minecraft client, ContainerUtilConfig config) {
        ContainerRecord record = ContainerPeek.lookedAt(client, config.peekDistance);
        if (record == null || record.isUnopened()) return;

        Font font = client.font;

        String title = record.displayName();
        String subtitle = record.usedSlots + "/" + record.slotCount + " slots"
            + "   ·   " + ContainerEspRenderer.formatAge(System.currentTimeMillis() - record.lastScanned) + " ago";
        boolean stale = record.isStale(config.staleAfterDays);

        List<String> lines = new ArrayList<>();
        List<ItemEntry> items = record.items != null ? record.items : List.of();

        // Biggest stacks first — that is what you want to know about a chest at a glance.
        List<ItemEntry> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> Integer.compare(b.count, a.count));

        int shown = Math.min(config.peekMaxLines, sorted.size());
        for (int i = 0; i < shown; i++) {
            ItemEntry entry = sorted.get(i);
            String line = entry.count + "× " + entry.name;
            if (entry.nestedIn != null) line += "  (in " + entry.nestedIn + ")";
            lines.add(line);
        }
        if (sorted.size() > shown) {
            lines.add("… and " + (sorted.size() - shown) + " more");
        }
        if (sorted.isEmpty()) {
            lines.add("empty");
        }

        int width = Math.max(font.width(title), font.width(subtitle));
        for (String line : lines) width = Math.max(width, font.width(line));

        int padding = 6;
        int lineHeight = font.lineHeight + 1;
        int boxWidth = width + padding * 2;
        int boxHeight = padding * 2 + lineHeight * (lines.size() + 2) + 2;

        int x = 8;
        int y = Math.max(8, (client.getWindow().getGuiScaledHeight() - boxHeight) / 2);

        context.fill(x, y, x + boxWidth, y + boxHeight, PANEL_BG);
        context.fill(x, y, x + boxWidth, y + 1, PANEL_BORDER);
        context.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, PANEL_BORDER);
        context.fill(x, y, x + 1, y + boxHeight, PANEL_BORDER);
        context.fill(x + boxWidth - 1, y, x + boxWidth, y + boxHeight, PANEL_BORDER);

        int textX = x + padding;
        int textY = y + padding;

        context.text(font, title, textX, textY, COLOR_TITLE, false);
        textY += lineHeight;
        context.text(font, subtitle, textX, textY, stale ? COLOR_STALE : COLOR_DIM, false);
        textY += lineHeight + 2;

        for (String line : lines) {
            context.text(font, line, textX, textY, COLOR_DIM, false);
            textY += lineHeight;
        }
    }
}
