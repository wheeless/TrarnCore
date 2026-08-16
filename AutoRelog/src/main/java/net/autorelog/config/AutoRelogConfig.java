package net.autorelog.config;

import net.autorelog.rule.MatchMode;
import net.autorelog.rule.Rule;
import net.autorelog.rule.RuleAction;
import net.trarncore.config.ValidatedConfig;

import java.util.ArrayList;
import java.util.List;

public class AutoRelogConfig implements ValidatedConfig {

    /** Master switch. Off means this mod does nothing at all. */
    public boolean enabled = true;

    // ── What counts as reconnectable ──────────────────────────────────────────

    /**
     * Reconnect after an operator or plugin kicked you.
     *
     * <p>Off by default, and that is the whole point of the mod's manners: a kick is a person or a
     * plugin deciding you should leave, and walking straight back in is the behaviour that gets
     * auto-reconnect mods banned outright on servers. Turn it on for a server whose "kick" is
     * really a restart notice, or better, write a rule matching that specific message.
     */
    public boolean reconnectOnKick = false;

    /**
     * Reconnect when the server refused you outright — banned, not whitelisted, name taken.
     *
     * <p>Off, and there is no good reason to change it. Retrying cannot succeed, and repeatedly
     * knocking on a server that banned you is how an account ban becomes an IP ban. Left
     * configurable only so it is your decision rather than mine.
     */
    public boolean reconnectWhenRefused = false;

    /**
     * Reconnect after the server closed — a restart or a full lobby.
     *
     * <p>On, with its own longer delay below, because a restart is precisely the case where
     * unattended reconnection earns its keep.
     */
    public boolean reconnectOnServerClosed = true;

    /**
     * Reconnect after a client-side or account problem: wrong version, expired session, auth down.
     *
     * <p>Off. Retrying cannot fix any of these, and the loop would run until it hit the attempt
     * cap while the real problem sat unread on screen.
     */
    public boolean reconnectOnClientProblem = false;

    /** Reconnect when a connection attempt failed, as opposed to an established session dropping. */
    public boolean reconnectOnFailedConnect = true;

    // ── Timing ────────────────────────────────────────────────────────────────

    /** Seconds before the first reconnect attempt. */
    public int delaySeconds = 5;

    /** Seconds before reconnecting after the server closed. Restarts take longer than a blip. */
    public int serverClosedDelaySeconds = 60;

    /** Multiply the delay by this after each failed attempt. 1.0 disables backoff. */
    public double backoffMultiplier = 1.8;

    /** Ceiling for the backed-off delay, so an overnight retry loop does not drift into hours. */
    public int maxDelaySeconds = 300;

    /**
     * Random extra delay, as a percentage of the computed wait.
     *
     * <p>Matters when a server restarts and every client running this mod counts down from the
     * same instant: without jitter they all reconnect on the same tick, which is a small
     * self-inflicted denial of service against a server that just booted.
     */
    public int jitterPercent = 20;

    /** Give up after this many consecutive attempts. 0 means never give up. */
    public int maxAttempts = 10;

    /**
     * A session lasting at least this many seconds resets the attempt counter.
     *
     * <p>Without it, ten brief drops over an evening exhaust the budget and the eleventh — hours
     * later, after a perfectly healthy session — is refused for no reason a player could guess.
     */
    public int sessionResetSeconds = 60;

    // ── Behaviour ─────────────────────────────────────────────────────────────

    /** Cancel a pending reconnect on any key press, not only the Cancel button. */
    public boolean cancelOnKeyPress = true;

    /** Show the countdown and controls on the disconnect screen. */
    public boolean showCountdown = true;

    /** Report what was decided, and why, in local chat after reconnecting. */
    public boolean announceInChat = true;

    /** Log every decision to the game log. Useful when writing rules; noisy otherwise. */
    public boolean verboseLogging = false;

    // ── Rules ─────────────────────────────────────────────────────────────────

    /**
     * Checked in order against every disconnect; the first match decides. Rules outrank all the
     * toggles above, which is what makes them useful — they are the escape hatch for a server
     * whose messages do not fit the general classification.
     */
    public List<Rule> rules = new ArrayList<>(defaultRules());

    /**
     * Ships as examples rather than as active policy: every one is disabled, so out of the box
     * behaviour is decided purely by the classification and the toggles. They exist to be read in
     * ModMenu, edited and switched on, because a blank rule list teaches nobody the syntax.
     */
    public static List<Rule> defaultRules() {
        List<Rule> defaults = new ArrayList<>();

        Rule restart = new Rule(RuleAction.RELOG, MatchMode.CONTAINS, "restarting", 120,
            "Example: a plugin's restart notice. Waits 2 minutes for the server to come back.");
        restart.enabled = false;
        defaults.add(restart);

        Rule maintenance = new Rule(RuleAction.NEVER, MatchMode.REGEX, "(?i)maintenance|whitelist", -1,
            "Example: do not retry while the server is closed for maintenance.");
        maintenance.enabled = false;
        defaults.add(maintenance);

        Rule shutdown = new Rule(RuleAction.RELOG, MatchMode.KEY,
            "multiplayer.disconnect.server_shutdown", 90,
            "Example: match the vanilla key instead of its text, so it survives translation.");
        shutdown.enabled = false;
        defaults.add(shutdown);

        Rule afk = new Rule(RuleAction.NEVER, MatchMode.CONTAINS, "idle", -1,
            "Example: an AFK kick means you walked away. Coming back for you defeats the point.");
        afk.enabled = false;
        defaults.add(afk);

        return defaults;
    }

    @Override
    public void validate() {
        if (delaySeconds < 0) delaySeconds = 0;
        if (serverClosedDelaySeconds < 0) serverClosedDelaySeconds = 0;
        if (maxDelaySeconds < 1) maxDelaySeconds = 1;
        if (backoffMultiplier < 1.0) backoffMultiplier = 1.0;
        if (backoffMultiplier > 10.0) backoffMultiplier = 10.0;
        if (jitterPercent < 0) jitterPercent = 0;
        if (jitterPercent > 100) jitterPercent = 100;
        if (maxAttempts < 0) maxAttempts = 0;
        if (sessionResetSeconds < 0) sessionResetSeconds = 0;

        // A delay above the ceiling would be silently clamped on first use, which reads as the
        // setting being ignored. Raise the ceiling to match instead.
        maxDelaySeconds = Math.max(maxDelaySeconds, Math.max(delaySeconds, serverClosedDelaySeconds));

        if (rules == null) rules = new ArrayList<>();
        rules.removeIf(java.util.Objects::isNull);
        for (Rule rule : rules) rule.validate();
    }
}
