package net.trarncore.hud;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Drag HUD panels where you want them.
 *
 * <p>Exists because a pair of X/Y boxes in a config screen is a genuinely bad way to place
 * something you have to look at: you cannot see the result while you are typing, and the numbers
 * mean nothing until you close the screen and check.
 *
 * <p>Dropped panels re-pin to whichever anchor they landed nearest, so the placement holds when
 * the window is resized — see {@link HudAnchor#nearest}.
 */
public class HudPlacementScreen extends Screen {

    private static final int GRID = 0x18FFFFFF;
    private static final int GHOST_FILL = 0x66202030;
    private static final int GHOST_FILL_ACTIVE = 0x8840C060;
    private static final int GHOST_BORDER = 0xFF5A5A70;
    private static final int GHOST_BORDER_ACTIVE = 0xFF60E090;
    private static final int SNAP_GUIDE = 0x90FFD54F;

    /** Pixels from an anchor line within which a drag snaps to it. */
    private static final int SNAP_DISTANCE = 6;

    private final Screen parent;
    private final List<HudElement> elements;
    private final Runnable onSave;
    private final List<HudPosition> originals = new ArrayList<>();

    private HudElement dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean snapping = true;

    public HudPlacementScreen(Screen parent, List<HudElement> elements, Runnable onSave) {
        super(Component.literal("Move HUD"));
        this.parent = parent;
        this.elements = List.copyOf(elements);
        this.onSave = onSave;
        // Kept so Cancel can put everything back exactly as it was.
        for (HudElement element : this.elements) {
            originals.add(element.position().copy());
        }
    }

    @Override
    protected void init() {
        int y = height - 28;

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
            onSave.run();
            minecraft.setScreen(parent);
        }).bounds(width / 2 - 154, y, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            for (int i = 0; i < elements.size(); i++) restore(elements.get(i).position(), originals.get(i));
        }).bounds(width / 2 - 50, y, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            for (int i = 0; i < elements.size(); i++) restore(elements.get(i).position(), originals.get(i));
            minecraft.setScreen(parent);
        }).bounds(width / 2 + 54, y, 100, 20).build());
    }

    private static void restore(HudPosition target, HudPosition from) {
        target.anchor = from.anchor;
        target.offsetX = from.offsetX;
        target.offsetY = from.offsetY;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        Font font = this.font;
        drawAnchorGrid(context);

        for (HudElement element : elements) {
            boolean active = element == dragging;
            int x = element.position().resolveX(width, element.width());
            int y = element.position().resolveY(height, element.height());
            int w = Math.max(element.width(), font.width(element.label()) + 8);
            int h = Math.max(element.height(), font.lineHeight + 6);

            context.fill(x, y, x + w, y + h, active ? GHOST_FILL_ACTIVE : GHOST_FILL);
            outline(context, x, y, w, h, active ? GHOST_BORDER_ACTIVE : GHOST_BORDER);

            context.text(font, Component.literal(element.label()), x + 4, y + 3, 0xFFFFFFFF, true);

            if (active) drawSnapGuides(context, x, y, w, h);
        }

        String hint = dragging != null
            ? dragging.position().toString()
            : "Drag a panel to move it. Hold Alt to place freely without snapping.";
        context.text(font, Component.literal(hint).withStyle(ChatFormatting.GRAY),
            width / 2 - font.width(hint) / 2, height - 44, 0xFFFFFFFF, true);
    }

    /** Faint lines at each anchor's third of the screen, so the snap targets are visible. */
    private void drawAnchorGrid(GuiGraphicsExtractor context) {
        for (float f : new float[]{0f, 0.5f, 1f}) {
            int x = Math.clamp(Math.round(width * f), 0, width - 1);
            int y = Math.clamp(Math.round(height * f), 0, height - 1);
            context.fill(x, 0, x + 1, height, GRID);
            context.fill(0, y, width, y + 1, GRID);
        }
    }

    private void drawSnapGuides(GuiGraphicsExtractor context, int x, int y, int w, int h) {
        for (int edge : new int[]{x, x + w / 2, x + w}) {
            if (nearAnchorLine(edge, width)) context.fill(edge, 0, edge + 1, height, SNAP_GUIDE);
        }
        for (int edge : new int[]{y, y + h / 2, y + h}) {
            if (nearAnchorLine(edge, height)) context.fill(0, edge, width, edge + 1, SNAP_GUIDE);
        }
    }

    private boolean nearAnchorLine(int value, int extent) {
        return Math.abs(value) <= SNAP_DISTANCE
            || Math.abs(value - extent / 2) <= SNAP_DISTANCE
            || Math.abs(value - extent) <= SNAP_DISTANCE;
    }

    private static void outline(GuiGraphicsExtractor context, int x, int y, int w, int h, int argb) {
        context.fill(x, y, x + w, y + 1, argb);
        context.fill(x, y + h - 1, x + w, y + h, argb);
        context.fill(x, y, x + 1, y + h, argb);
        context.fill(x + w - 1, y, x + w, y + h, argb);
    }

    // ── Input ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // Topmost first, so overlapping panels pick the one drawn on top.
            for (int i = elements.size() - 1; i >= 0; i--) {
                HudElement element = elements.get(i);
                int x = element.position().resolveX(width, element.width());
                int y = element.position().resolveY(height, element.height());
                int w = Math.max(element.width(), font.width(element.label()) + 8);
                int h = Math.max(element.height(), font.lineHeight + 6);

                if (event.x() >= x && event.x() < x + w && event.y() >= y && event.y() < y + h) {
                    dragging = element;
                    dragOffsetX = (int) event.x() - x;
                    dragOffsetY = (int) event.y() - y;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging == null) return super.mouseDragged(event, dragX, dragY);

        int w = dragging.width();
        int h = dragging.height();
        int x = (int) event.x() - dragOffsetX;
        int y = (int) event.y() - dragOffsetY;

        if (snapping) {
            x = snap(x, w, width);
            y = snap(y, h, height);
        }

        x = Math.clamp(x, 0, Math.max(0, width - w));
        y = Math.clamp(y, 0, Math.max(0, height - h));
        dragging.position().moveTo(x, y, width, height, w, h);
        return true;
    }

    /** Pulls an edge or the centre onto the nearest anchor line when it is close enough. */
    private static int snap(int value, int size, int extent) {
        int[][] candidates = {
            {value, 0}, {value + size / 2, extent / 2}, {value + size, extent}
        };
        for (int[] pair : candidates) {
            if (Math.abs(pair[0] - pair[1]) <= SNAP_DISTANCE) {
                return value + (pair[1] - pair[0]);
            }
        }
        return value;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        snapping = (event.modifiers() & GLFW.GLFW_MOD_ALT) == 0;

        // Arrow keys nudge the selected panel a pixel at a time, for the last bit of precision
        // a mouse drag cannot give.
        HudElement target = dragging != null ? dragging : (elements.isEmpty() ? null : elements.get(0));
        if (target != null) {
            int dx = 0, dy = 0;
            switch (event.key()) {
                case GLFW.GLFW_KEY_LEFT  -> dx = -1;
                case GLFW.GLFW_KEY_RIGHT -> dx = 1;
                case GLFW.GLFW_KEY_UP    -> dy = -1;
                case GLFW.GLFW_KEY_DOWN  -> dy = 1;
                default -> { }
            }
            if (dx != 0 || dy != 0) {
                target.position().offsetX += dx;
                target.position().offsetY += dy;
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        onSave.run();
        minecraft.setScreen(parent);
    }

    /** Placement is done in-world, so pausing would hide the very thing being positioned. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
