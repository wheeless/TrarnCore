package net.autorelog.screen;

import net.autorelog.AutoRelog;
import net.autorelog.reconnect.ReconnectManager;
import net.autorelog.reconnect.SessionTracker;
import net.autorelog.rule.DisconnectInfo;
import net.autorelog.rule.RuleEngine;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.trarncore.util.Guarded;

import java.lang.ref.WeakReference;

/**
 * Everything this mod does to the disconnect screen.
 *
 * <p>Driven by Fabric's screen events rather than a mixin, in keeping with the rest of the repo.
 * The reason itself is read from a field opened by {@code autorelog.accesswidener}; there is no
 * getter for it.
 */
public final class DisconnectScreenHook {

    private DisconnectScreenHook() {
    }

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;

    /**
     * The screen a decision has already been made for.
     *
     * <p>{@code AFTER_INIT} fires again on every window resize, and re-evaluating there would
     * restart the countdown — resize the window twice during a two-minute wait and it would never
     * fire. Weak so a closed screen is not held alive by this reference.
     */
    private static WeakReference<Screen> evaluatedFor = new WeakReference<>(null);

    private static Button reconnectButton;
    private static Button cancelButton;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof DisconnectedScreen disconnected)) return;
            Guarded.run(AutoRelog.LOGGER, "AutoRelog disconnect screen",
                () -> onDisconnectScreen(client, disconnected));
        });
    }

    private static void onDisconnectScreen(Minecraft client, DisconnectedScreen screen) {
        boolean firstInit = evaluatedFor.get() != screen;

        if (firstInit) {
            evaluatedFor = new WeakReference<>(screen);

            DisconnectInfo info = readReason(screen);
            RuleEngine.Decision decision = ReconnectManager.evaluate(info, wasConnectAttempt(screen));

            if (!decision.reconnect()) {
                if (AutoRelog.config().verboseLogging) {
                    AutoRelog.LOGGER.info("[AutoRelog] staying disconnected: {}", decision.why());
                }
                return;
            }
        } else if (!ReconnectManager.isArmed()) {
            // A resize after the countdown was cancelled or already fired. Nothing to re-add.
            return;
        }

        if (!AutoRelog.config().showCountdown) {
            // No UI wanted, but the countdown still has to be driven from somewhere.
            registerTick(screen);
            return;
        }

        addWidgets(client, screen);
        registerTick(screen);
        registerCancelOnKeyPress(screen);
    }

    private static void addWidgets(Minecraft client, DisconnectedScreen screen) {
        int x = screen.width / 2 - BUTTON_WIDTH / 2;
        // Below the vanilla button column. The disconnect screen lays its buttons out from the
        // middle downward, so sitting under them is the one spot that cannot collide.
        int y = Math.min(screen.height - 30, screen.height / 2 + 60);

        reconnectButton = Button.builder(countdownLabel(), b -> ReconnectManager.fire(client))
            .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build();

        cancelButton = Button.builder(
                Component.literal("Cancel reconnect").withStyle(ChatFormatting.GRAY),
                b -> {
                    ReconnectManager.cancel();
                    hideWidgets();
                })
            .bounds(x, y + BUTTON_HEIGHT + 4, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build();

        Screens.getWidgets(screen).add(reconnectButton);
        Screens.getWidgets(screen).add(cancelButton);
    }

    private static void registerTick(Screen screen) {
        ScreenEvents.afterTick(screen).register(s -> Guarded.run(AutoRelog.LOGGER, "AutoRelog tick", () -> {
            Minecraft client = Minecraft.getInstance();
            if (!ReconnectManager.isArmed()) {
                hideWidgets();
                return;
            }
            if (reconnectButton != null) reconnectButton.setMessage(countdownLabel());
            ReconnectManager.tick(client);
        }));
    }

    /**
     * Any key cancels, if configured. Deliberately not ESC-only: a player who came back to the
     * keyboard mid-countdown will hit something, and whatever they hit should stop the reconnect
     * rather than only the one key they had to guess.
     */
    private static void registerCancelOnKeyPress(Screen screen) {
        ScreenKeyboardEvents.afterKeyPress(screen).register((s, keyEvent) -> {
            if (!AutoRelog.config().cancelOnKeyPress) return;
            if (!ReconnectManager.isArmed()) return;
            ReconnectManager.cancel();
            hideWidgets();
        });
    }

    private static void hideWidgets() {
        if (reconnectButton != null) reconnectButton.visible = false;
        if (cancelButton != null) cancelButton.visible = false;
    }

    private static Component countdownLabel() {
        int seconds = ReconnectManager.secondsRemaining();
        String attempts = ReconnectManager.attemptCap() > 0
            ? " (attempt " + ReconnectManager.attemptNumber() + "/" + ReconnectManager.attemptCap() + ")"
            : " (attempt " + ReconnectManager.attemptNumber() + ")";
        return Component.literal("Reconnecting in " + seconds + "s" + attempts)
            .withStyle(ChatFormatting.GREEN);
    }

    /** Reads the reason out of the field opened by the access widener. */
    private static DisconnectInfo readReason(DisconnectedScreen screen) {
        try {
            return DisconnectInfo.of(screen.details.reason());
        } catch (Throwable t) {
            // Only reachable if the widener stopped applying — a Minecraft update renaming the
            // field. Better to fall back to "unknown", which the defaults refuse to retry, than
            // to take the client down on a screen that is already reporting a failure.
            AutoRelog.LOGGER.error("[AutoRelog] could not read the disconnect reason", t);
            return DisconnectInfo.of(null);
        }
    }

    /**
     * True when the connection never established, as opposed to a live session dropping.
     *
     * <p>Told apart by the screen's title: vanilla builds a failed connect with
     * {@code connect.failed} and a lost session with {@code disconnect.lost}. Falls back to
     * "session dropped" when the title is neither, since that is the commoner case.
     */
    private static boolean wasConnectAttempt(DisconnectedScreen screen) {
        return DisconnectInfo.of(screen.getTitle()).translationKeys().stream()
            .anyMatch(key -> key.startsWith("connect.failed"));
    }

    /** Clears the guard so the next disconnect is evaluated fresh. */
    public static void onJoined() {
        evaluatedFor = new WeakReference<>(null);
        reconnectButton = null;
        cancelButton = null;
        ReconnectManager.onJoined();
        if (AutoRelog.config().announceInChat && SessionTracker.canReconnect()) {
            AutoRelog.LOGGER.debug("[AutoRelog] session established, attempt counter reset");
        }
    }
}
