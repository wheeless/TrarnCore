package net.trustui.trust;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;
import net.trustui.TrustUI;
import net.trustui.config.ConfigManager;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Runs {@code /trustlist} and collects the plugin's reply.
 *
 * <p>The only way to read a server plugin's state from the client is to ask it and read the chat
 * it prints back, so this opens a short capture window, sends the command, and gathers whatever
 * arrives.
 *
 * <p><b>What gets hidden from chat.</b> Suppressing everything during the window would eat
 * unrelated messages — a death, someone talking — that happen to land in the same half-second.
 * So only lines that actually look like part of the listing are swallowed; anything else passes
 * through untouched. The player gets a clean chat without the mod deciding it owns their whole
 * message feed for the duration.
 */
public final class TrustListReader {

    /** The result of one lookup. */
    public record Result(boolean inClaim, Map<String, EnumSet<TrustLevel>> trusted) {
    }

    private static volatile boolean capturing = false;
    private static final List<String> captured = new ArrayList<>();
    private static Consumer<Result> pending;
    private static int ticksRemaining = 0;

    private TrustListReader() {
    }

    /** Registered once at client init. */
    public static void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register(TrustListReader::onGameMessage);
    }

    private static boolean onGameMessage(Text message, boolean overlay) {
        // Overlay messages are the action bar, never chat output from a command.
        if (!capturing || overlay) return true;

        String plain = message.getString();
        synchronized (captured) {
            captured.add(plain);
        }
        // Swallow only what belongs to the listing; let everything else reach the player.
        return !isTrustListLine(plain);
    }

    private static boolean isTrustListLine(String plain) {
        if (!ConfigManager.get().hideTrustListOutput) return false;
        String trimmed = plain.trim();
        if (trimmed.startsWith(">")) return true;
        String header = ConfigManager.get().trustListHeader;
        return !header.isBlank() && trimmed.startsWith(header);
    }

    /**
     * Sends {@code /trustlist} and calls back once the window closes.
     *
     * <p>Always calls back exactly once, including when nothing arrives — a caller waiting on a
     * response that never comes is worse than one told the claim could not be read.
     */
    public static void request(Consumer<Result> onComplete) {
        if (capturing) {
            TrustUI.LOGGER.debug("[TrustUI] trustlist already in flight; ignoring request");
            return;
        }
        synchronized (captured) {
            captured.clear();
        }
        pending = onComplete;
        ticksRemaining = ConfigManager.get().trustListTimeoutTicks;
        capturing = true;
        TrustCommands.requestTrustList();
    }

    /** Driven from the client tick; closes the window when it expires. */
    public static void tick() {
        if (!capturing) return;
        if (--ticksRemaining > 0) return;
        finish();
    }

    private static void finish() {
        capturing = false;

        List<String> lines;
        synchronized (captured) {
            lines = new ArrayList<>(captured);
            captured.clear();
        }

        Consumer<Result> callback = pending;
        pending = null;
        if (callback == null) return;

        boolean inClaim = TrustListParser.looksLikeTrustList(lines);
        Map<String, EnumSet<TrustLevel>> trusted = TrustListParser.parse(lines);
        TrustUI.LOGGER.debug("[TrustUI] captured {} line(s), inClaim={}, {} trusted",
            lines.size(), inClaim, trusted.size());

        try {
            callback.accept(new Result(inClaim, trusted));
        } catch (Exception e) {
            TrustUI.LOGGER.error("[TrustUI] trustlist callback failed", e);
        }
    }

    /** Drops any in-flight capture — called on disconnect so state never leaks across servers. */
    public static void clear() {
        capturing = false;
        pending = null;
        synchronized (captured) {
            captured.clear();
        }
    }
}
