package net.blowbyblow.render;

import net.blowbyblow.BlowByBlow;
import net.blowbyblow.config.BlowByBlowConfig;
import net.blowbyblow.config.ConfigManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.trarncore.hud.HudElement;
import net.trarncore.hud.TextFeed;

import java.util.List;

/** The scrolling panel. Line content comes from the mod; the scrolling and fading come from TrarnCore. */
public class FeedRenderer {

    private static final Identifier HUD_ELEMENT_ID =
        Identifier.fromNamespaceAndPath(BlowByBlow.MOD_ID, "feed");

    private static final TextFeed FEED = new TextFeed();

    /** Sample lines shown while placing the panel, so there is something to aim at. */
    private static final List<String> PLACEMENT_SAMPLE = List.of(
        "Zombie hit you for 1.5♥ with an Iron Sword",
        "You hit Zombie for ~3.5♥ with a Netherite Axe",
        "You took 1♥ from the fall");

    private static long lastError = 0;

    public static void register() {
        HudElementRegistry.addLast(HUD_ELEMENT_ID, (context, tickCounter) -> {
            try {
                renderHud(context);
            } catch (Exception e) {
                long now = System.currentTimeMillis();
                if (now - lastError > 5000) {
                    lastError = now;
                    BlowByBlow.LOGGER.error("[BlowByBlow] HUD render crashed (suppressing repeats for 5s)", e);
                }
            }
        });
    }

    public static void add(Component line) {
        FEED.add(line);
    }

    public static void clear() {
        FEED.clear();
    }

    private static void renderHud(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (client.screen != null) return;
        if (client.options.hideGui) return;

        BlowByBlowConfig config = ConfigManager.get();
        if (!BlowByBlow.enabled || !config.showPanel) return;

        applySettings(config);
        if (FEED.isEmpty()) return;

        Font font = client.font;
        int width = FEED.measureWidth(font, config.padding);
        int height = FEED.measureHeight(font, config.padding, config.lineSpacing);
        if (width == 0 || height == 0) return;

        int screenW = context.guiWidth();
        int screenH = context.guiHeight();

        // The panel grows and shrinks as lines come and go, so a placement made when it was tall
        // can put it off the bottom when it is taller still. Clamping every frame costs nothing
        // and means it can never end up somewhere unreachable.
        config.panelPosition.clampInto(screenW, screenH, width, height);

        FEED.render(context, font,
            config.panelPosition.resolveX(screenW, width),
            config.panelPosition.resolveY(screenH, height),
            config.padding, config.lineSpacing, config.panelBackground, config.textShadow);
    }

    private static void applySettings(BlowByBlowConfig config) {
        FEED.capacity(config.maxLines)
            .hold(config.holdSeconds * 1000L)
            .fade(config.fadeSeconds * 1000L)
            .newestAtBottom(config.newestAtBottom);
    }

    /**
     * A description of the panel for the drag-to-place screen.
     *
     * <p>Reports the size the panel would be at its configured line count rather than its current
     * one, so an empty feed still gives something to grab and the box does not change size under
     * the cursor as old lines expire mid-drag.
     */
    public static HudElement placementElement() {
        Minecraft client = Minecraft.getInstance();
        BlowByBlowConfig config = ConfigManager.get();
        Font font = client.font;

        int widest = 0;
        for (String sample : PLACEMENT_SAMPLE) widest = Math.max(widest, font.width(sample));

        int width = widest + config.padding * 2;
        int height = config.maxLines * (font.lineHeight + config.lineSpacing)
            - config.lineSpacing + config.padding * 2;

        return new HudElement(BlowByBlow.MOD_ID, "Combat feed", width, height, config.panelPosition);
    }
}
