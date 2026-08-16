package net.autorelog.rule;

import java.util.Locale;

/** What a matching rule decides to do. */
public enum RuleAction {

    /** Reconnect, overriding the built-in classification. */
    RELOG("relog"),

    /** Do not reconnect, overriding the built-in classification. */
    NEVER("never"),

    /**
     * Stop rule evaluation and fall through to the built-in classification.
     *
     * <p>Exists so a broad rule can be carved out without reordering the list: put
     * {@code ignore | contains | scheduled restart} above {@code never | contains | restart} and
     * the scheduled case is handled by the defaults rather than by the blanket rule below it.
     */
    IGNORE("ignore");

    private final String token;

    RuleAction(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    public static RuleAction parse(String raw, RuleAction fallback) {
        if (raw == null) return fallback;
        String needle = raw.strip().toLowerCase(Locale.ROOT);
        for (RuleAction action : values()) {
            if (action.token.equals(needle) || action.name().toLowerCase(Locale.ROOT).equals(needle)) {
                return action;
            }
        }
        // Common synonyms, because these are typed by hand in a text field.
        return switch (needle) {
            case "reconnect", "yes", "true", "rejoin", "retry" -> RELOG;
            case "no", "false", "stop", "block", "deny"        -> NEVER;
            case "skip", "default", "fallthrough"              -> IGNORE;
            default                                            -> fallback;
        };
    }
}
