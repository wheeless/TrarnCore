package net.trustui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.trustui.trust.TrustLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A row in the trust list.
 *
 * <p>Two shapes, because {@link ContainerObjectSelectionList} rows are a fixed height and cannot grow when
 * clicked: a {@link Player} row, and an {@link Actions} row that gets inserted directly beneath it
 * while that player is expanded. To the list they are just two consecutive entries; to the player
 * it reads as one row opening up.
 */
public abstract class TrustListEntry extends ContainerObjectSelectionList.Entry<TrustListEntry> {

    protected static final int HEAD_SIZE = 24;
    protected static final int PADDING = 4;

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of();
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
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
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getContentX();
            int y = getContentY();
            Minecraft client = Minecraft.getInstance();

            PlayerFaceExtractor.extractRenderState(context, player.skin(), x + PADDING, y + PADDING, HEAD_SIZE);

            int textX = x + PADDING + HEAD_SIZE + 8;
            int textY = y + (getContentHeight() - client.font.lineHeight) / 2;

            // Trusted players are drawn bright; everyone else is dimmed, so the people who already
            // have access stand out from the rest of the online list.
            int nameColor = player.hasAnyTrust() ? 0xFFFFFFFF : 0xFF9A9A9A;
            context.text(client.font, player.name(), textX, textY, nameColor, false);

            int rightEdge = (getContentX() + getContentWidth()) - PADDING;

            String chevron = expanded ? "▾" : "▸";
            int chevronWidth = client.font.width(chevron);
            context.text(client.font, chevron, rightEdge - chevronWidth, textY, 0xFF9A9A9A, false);
            rightEdge -= chevronWidth + 8;

            if (!player.isOnline()) {
                String offline = "offline";
                int w = client.font.width(offline);
                context.text(client.font, offline, rightEdge - w, textY, 0xFF6A6A6A, false);
                rightEdge -= w + 8;
            }

            TrustLevel highest = player.highestTrust();
            if (highest != null) {
                String badge = player.trust().size() > 1
                    ? highest.displayName() + " +" + (player.trust().size() - 1)
                    : highest.displayName();
                int w = client.font.width(badge);
                context.text(client.font, badge, rightEdge - w, textY,
                    0xFF000000 | highest.color(), false);
            }
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
            onClick.run();
            return true;
        }
    }

    // ── Actions row ──────────────────────────────────────────────────────────

    /**
     * The buttons shown while a player is expanded.
     *
     * <p>Real {@link Button}s rather than drawn rectangles, so they get vanilla's hover and
     * press states, focus outline and keyboard navigation for free.
     */
    public static class Actions extends TrustListEntry {

        private final List<AbstractWidget> buttons = new ArrayList<>();
        private final PlayerEntry player;

        public Actions(PlayerEntry player, Consumer<TrustLevel> onGrant, Runnable onRevoke) {
            this.player = player;

            for (TrustLevel level : TrustLevel.values()) {
                // A tier already held is labelled in that tier's colour, so the row shows current
                // state as well as offering the action.
                Component label = player.trust().contains(level)
                    ? Component.literal(level.displayName()).withStyle(s -> s.withColor(level.color()))
                    : Component.literal(level.displayName());
                buttons.add(Button.builder(label, b -> onGrant.accept(level))
                    .size(58, 16)
                    .build());
            }
            if (player.hasAnyTrust()) {
                buttons.add(Button.builder(
                        Component.literal("Remove").withStyle(ChatFormatting.RED), b -> onRevoke.run())
                    .size(58, 16)
                    .build());
            }
        }

        public PlayerEntry player() {
            return player;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getContentX() + PADDING + HEAD_SIZE + 8;
            int y = getContentY() + (getContentHeight() - 16) / 2;

            for (AbstractWidget button : buttons) {
                button.setX(x);
                button.setY(y);
                button.extractRenderState(context, mouseX, mouseY, delta);
                x += button.getWidth() + 2;
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return buttons;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return buttons;
        }
    }
}
