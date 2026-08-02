package net.trustui.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.trustui.trust.TrustLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A row in the trust list.
 *
 * <p>Two shapes, because {@link ElementListWidget} rows are a fixed height and cannot grow when
 * clicked: a {@link Player} row, and an {@link Actions} row that gets inserted directly beneath it
 * while that player is expanded. To the list they are just two consecutive entries; to the player
 * it reads as one row opening up.
 */
public abstract class TrustListEntry extends ElementListWidget.Entry<TrustListEntry> {

    protected static final int HEAD_SIZE = 24;
    protected static final int PADDING = 4;

    @Override
    public List<? extends Element> children() {
        return List.of();
    }

    @Override
    public List<? extends Selectable> selectableChildren() {
        return List.of();
    }

    // ── Player row ───────────────────────────────────────────────────────────

    /** Head, name, and a badge showing the highest tier they hold. */
    public static class Player extends TrustListEntry {

        private final PlayerEntry player;
        private final Runnable onClick;
        private final boolean expanded;

        public Player(PlayerEntry player, boolean expanded, Runnable onClick) {
            this.player = player;
            this.expanded = expanded;
            this.onClick = onClick;
        }

        public PlayerEntry player() {
            return player;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getContentX();
            int y = getContentY();
            MinecraftClient client = MinecraftClient.getInstance();

            PlayerSkinDrawer.draw(context, player.skin(), x + PADDING, y + PADDING, HEAD_SIZE);

            int textX = x + PADDING + HEAD_SIZE + 8;
            int textY = y + (getContentHeight() - client.textRenderer.fontHeight) / 2;

            // Trusted players are drawn bright; everyone else is dimmed, so the people who already
            // have access stand out from the rest of the online list.
            int nameColor = player.hasAnyTrust() ? 0xFFFFFFFF : 0xFF9A9A9A;
            context.drawText(client.textRenderer, player.name(), textX, textY, nameColor, false);

            int rightEdge = getContentRightEnd() - PADDING;

            String chevron = expanded ? "▾" : "▸";
            int chevronWidth = client.textRenderer.getWidth(chevron);
            context.drawText(client.textRenderer, chevron, rightEdge - chevronWidth, textY, 0xFF9A9A9A, false);
            rightEdge -= chevronWidth + 8;

            if (!player.isOnline()) {
                String offline = "offline";
                int w = client.textRenderer.getWidth(offline);
                context.drawText(client.textRenderer, offline, rightEdge - w, textY, 0xFF6A6A6A, false);
                rightEdge -= w + 8;
            }

            TrustLevel highest = player.highestTrust();
            if (highest != null) {
                String badge = player.trust().size() > 1
                    ? highest.displayName() + " +" + (player.trust().size() - 1)
                    : highest.displayName();
                int w = client.textRenderer.getWidth(badge);
                context.drawText(client.textRenderer, badge, rightEdge - w, textY,
                    0xFF000000 | highest.color(), false);
            }
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
            onClick.run();
            return true;
        }
    }

    // ── Actions row ──────────────────────────────────────────────────────────

    /**
     * The buttons shown while a player is expanded.
     *
     * <p>Real {@link ButtonWidget}s rather than drawn rectangles, so they get vanilla's hover and
     * press states, focus outline and keyboard navigation for free.
     */
    public static class Actions extends TrustListEntry {

        private final List<ClickableWidget> buttons = new ArrayList<>();
        private final PlayerEntry player;

        public Actions(PlayerEntry player, Consumer<TrustLevel> onGrant, Runnable onRevoke) {
            this.player = player;

            for (TrustLevel level : TrustLevel.values()) {
                // A tier already held is labelled in that tier's colour, so the row shows current
                // state as well as offering the action.
                Text label = player.trust().contains(level)
                    ? Text.literal(level.displayName()).styled(s -> s.withColor(level.color()))
                    : Text.literal(level.displayName());
                buttons.add(ButtonWidget.builder(label, b -> onGrant.accept(level))
                    .size(58, 16)
                    .build());
            }
            if (player.hasAnyTrust()) {
                buttons.add(ButtonWidget.builder(
                        Text.literal("Remove").formatted(Formatting.RED), b -> onRevoke.run())
                    .size(58, 16)
                    .build());
            }
        }

        public PlayerEntry player() {
            return player;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getContentX() + PADDING + HEAD_SIZE + 8;
            int y = getContentY() + (getContentHeight() - 16) / 2;

            for (ClickableWidget button : buttons) {
                button.setX(x);
                button.setY(y);
                button.render(context, mouseX, mouseY, delta);
                x += button.getWidth() + 2;
            }
        }

        @Override
        public List<? extends Element> children() {
            return buttons;
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return buttons;
        }
    }
}
