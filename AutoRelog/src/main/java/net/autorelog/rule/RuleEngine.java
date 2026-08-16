package net.autorelog.rule;

import net.autorelog.AutoRelog;
import net.autorelog.config.AutoRelogConfig;

/**
 * Turns a disconnect into a decision.
 *
 * <p>Order is deliberate and the whole design rests on it: user rules are consulted before the
 * built-in classification, so a rule can always override what this mod thinks it knows. The
 * classification is a good default, not an authority — servers word their kicks however they like,
 * and no fixed list of keys can cover a plugin that says "Rebooting, back in 2 minutes".
 */
public final class RuleEngine {

    private RuleEngine() {
    }

    /**
     * @param reconnect     whether to attempt a reconnect at all
     * @param delaySeconds  base wait before the first attempt, before backoff and jitter
     * @param maxAttempts   attempt cap for this disconnect; 0 means unlimited
     * @param why           short human explanation, shown on screen and in chat
     */
    public record Decision(boolean reconnect, int delaySeconds, int maxAttempts, String why) {

        static Decision no(String why) {
            return new Decision(false, 0, 0, why);
        }
    }

    public static Decision decide(AutoRelogConfig config, DisconnectInfo info, boolean wasConnectAttempt) {
        if (!config.enabled) {
            return Decision.no("AutoRelog is disabled");
        }

        // ── User rules first, in order, first match wins ──────────────────────
        for (int i = 0; i < config.rules.size(); i++) {
            Rule rule = config.rules.get(i);
            if (!rule.matches(info)) continue;

            RuleAction action = rule.actionEnum();
            String label = "rule " + (i + 1) + " (" + rule.modeEnum().token() + " \"" + rule.pattern + "\")";

            if (action == RuleAction.IGNORE) {
                if (config.verboseLogging) {
                    AutoRelog.LOGGER.info("[AutoRelog] {} matched and yields to the defaults", label);
                }
                break;
            }
            if (action == RuleAction.NEVER) {
                return Decision.no("blocked by " + label);
            }
            return new Decision(
                true,
                rule.delaySeconds >= 0 ? rule.delaySeconds : defaultDelay(config, info),
                rule.maxAttempts  >= 0 ? rule.maxAttempts  : config.maxAttempts,
                "matched " + label);
        }

        // ── Built-in classification ───────────────────────────────────────────
        boolean allowed = switch (info.kind()) {
            case NETWORK        -> true;
            case SERVER_CLOSED  -> config.reconnectOnServerClosed;
            case SERVER_REFUSED -> config.reconnectWhenRefused;
            case CLIENT_PROBLEM -> config.reconnectOnClientProblem;
            case KICK_CUSTOM, UNKNOWN -> config.reconnectOnKick;
        };

        if (!allowed) {
            return Decision.no(switch (info.kind()) {
                case SERVER_REFUSED -> "the server refused the connection";
                case CLIENT_PROBLEM -> "reconnecting cannot fix this";
                case KICK_CUSTOM, UNKNOWN -> "you were kicked (enable \"Reconnect On Kick\" to change)";
                default -> info.kind().label() + " is disabled";
            });
        }

        if (wasConnectAttempt && !config.reconnectOnFailedConnect) {
            return Decision.no("the connection attempt failed and retrying those is disabled");
        }

        return new Decision(true, defaultDelay(config, info), config.maxAttempts, info.kind().label());
    }

    /** Server restarts get their own, longer wait; everything else uses the general delay. */
    private static int defaultDelay(AutoRelogConfig config, DisconnectInfo info) {
        return info.kind() == DisconnectKind.SERVER_CLOSED
            ? config.serverClosedDelaySeconds
            : config.delaySeconds;
    }
}
