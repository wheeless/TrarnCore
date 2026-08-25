package net.trarncore.hud;

/**
 * A HUD panel that can be dragged into place by {@link HudPlacementScreen}.
 *
 * <p>Deliberately does not know how to draw itself. The placement screen only needs a name, a
 * size and somewhere to store the result, so an element can be a live panel, a preview box, or
 * something drawn by a completely different system.
 *
 * @param id       stable identifier, used only for logging
 * @param label    shown on the ghost box while placing
 * @param width    current width in scaled GUI pixels
 * @param height   current height in scaled GUI pixels
 * @param position the position to read and write; edited in place
 */
public record HudElement(String id, String label, int width, int height, HudPosition position) {
}
