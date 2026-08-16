package net.autorelog.reconnect;

import net.autorelog.AutoRelog;
import net.autorelog.config.AutoRelogConfig;
import net.autorelog.rule.DisconnectInfo;
import net.autorelog.rule.RuleEngine;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The countdown and the attempt budget.
 *
 * <p>One armed reconnect at a time. Everything is wall-clock rather than tick-counted, because a
 * two-minute wait spent watching a disconnect screen is exactly when the client is free to run at
 * whatever frame rate it likes, and a tick-counted countdown drifts visibly.
 */
public final class ReconnectManager {

    private ReconnectManager() {
    }

    private static boolean armed;
    private static long fireAtMillis;
    private static int attempt;
    private static int attemptCap;
    private static String reason = "";
    private static String lastDecision = "";

    /**
     * Evaluates a disconnect and arms a reconnect if the rules and settings allow one.
     *
     * @param wasConnectAttempt true when the connection never established, as opposed to an
     *                          existing session dropping — the two are worth distinguishing
     *                          because retrying a failed connect is a different judgement call
     * @return the decision, so the screen can explain itself even when the answer is no
     */
    public static RuleEngine.Decision evaluate(DisconnectInfo info, boolean wasConnectAttempt) {
        AutoRelogConfig config = AutoRelog.config();
        disarm();

        RuleEngine.Decision decision = RuleEngine.decide(config, info, wasConnectAttempt);
        lastDecision = decision.why();

        if (config.verboseLogging) {
            AutoRelog.LOGGER.info("[AutoRelog] disconnect kind={} keys={} reason=\"{}\" -> {} ({})",
                info.kind(), info.translationKeys(), info.describe(),
                decision.reconnect() ? "reconnect" : "stay", decision.why());
        }

        if (!decision.reconnect()) return decision;

        if (!SessionTracker.canReconnect()) {
            lastDecision = "no multiplayer server to return to";
            return new RuleEngine.Decision(false, 0, 0, lastDecision);
        }

        // A session that lasted properly means the previous failures are not part of this story.
        if (config.sessionResetSeconds > 0
            && SessionTracker.lastSessionSeconds() >= config.sessionResetSeconds) {
            attempt = 0;
        }

        attemptCap = decision.maxAttempts();
        if (attemptCap > 0 && attempt >= attemptCap) {
            lastDecision = "gave up after " + attempt + " attempts";
            attempt = 0;
            return new RuleEngine.Decision(false, 0, 0, lastDecision);
        }

        reason = decision.why();
        armed = true;
        fireAtMillis = System.currentTimeMillis() + waitMillis(config, decision.delaySeconds());
        return decision;
    }

    /**
     * Applies backoff and jitter to the base delay.
     *
     * <p>Backoff is raised to the power of attempts already made, so the first wait is exactly
     * what was configured and only a repeatedly failing server sees the delay grow.
     */
    private static long waitMillis(AutoRelogConfig config, int baseSeconds) {
        double seconds = baseSeconds * Math.pow(config.backoffMultiplier, attempt);
        seconds = Math.min(seconds, config.maxDelaySeconds);

        if (config.jitterPercent > 0 && seconds > 0) {
            double spread = seconds * (config.jitterPercent / 100.0);
            seconds += ThreadLocalRandom.current().nextDouble(0, spread);
        }
        return Math.max(0L, (long) (seconds * 1000.0));
    }

    /** Drives the countdown. Safe to call every frame or every tick. */
    public static void tick(Minecraft client) {
        if (!armed) return;
        if (System.currentTimeMillis() < fireAtMillis) return;
        fire(client);
    }

    /** Reconnects now, cancelling any pending countdown. */
    public static void fire(Minecraft client) {
        if (!SessionTracker.canReconnect()) {
            disarm();
            return;
        }
        ServerData server = SessionTracker.lastServer();
        disarm();
        attempt++;

        AutoRelog.LOGGER.info("[AutoRelog] reconnecting to {} ({}), attempt {}{}",
            server.name, server.ip, attempt, attemptCap > 0 ? "/" + attemptCap : "");

        if (AutoRelog.config().announceInChat) {
            AutoRelog.CHAT.send("Reconnecting to " + server.name + " — " + reason, ChatFormatting.GRAY);
        }

        // Vanilla's own join path. The parent screen is what Cancel and a failed connect fall
        // back to, so it must be a screen that makes sense to land on: the server list.
        ServerAddress address = ServerAddress.parseString(server.ip);
        ConnectScreen.startConnecting(
            new JoinMultiplayerScreen(new TitleScreen()), client, address, server, false, null);
    }

    /** Cancels a pending reconnect. Also resets the attempt budget: cancelling is a decision. */
    public static void cancel() {
        if (armed && AutoRelog.config().announceInChat) {
            AutoRelog.CHAT.send("Reconnect cancelled", ChatFormatting.GRAY);
        }
        disarm();
        attempt = 0;
        lastDecision = "cancelled";
    }

    private static void disarm() {
        armed = false;
        fireAtMillis = 0;
    }

    public static boolean isArmed() {
        return armed;
    }

    /** Whole seconds left, rounded up so a countdown never sits on "0" before firing. */
    public static int secondsRemaining() {
        if (!armed) return 0;
        long remaining = fireAtMillis - System.currentTimeMillis();
        return remaining <= 0 ? 0 : (int) ((remaining + 999) / 1000);
    }

    public static int attemptNumber() {
        return attempt + 1;
    }

    public static int attemptCap() {
        return attemptCap;
    }

    /** Why the last disconnect was or was not going to be retried. */
    public static String lastDecision() {
        return lastDecision;
    }

    /** Clears state on a successful join, so a good session starts from a clean budget. */
    public static void onJoined() {
        disarm();
        attempt = 0;
    }
}
