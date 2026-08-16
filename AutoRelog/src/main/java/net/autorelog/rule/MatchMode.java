package net.autorelog.rule;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** How a rule's pattern is compared against the disconnect reason. */
public enum MatchMode {

    /** Reason contains the pattern anywhere. The default, and what almost every rule wants. */
    CONTAINS("contains"),

    /** Reason is exactly the pattern, ignoring surrounding whitespace. */
    EQUALS("equals"),

    /** Reason begins with the pattern. */
    STARTS("starts"),

    /** Reason ends with the pattern. */
    ENDS("ends"),

    /**
     * Java regular expression, matched anywhere in the reason.
     *
     * <p>A pattern that does not compile never matches, rather than throwing — a typo in one rule
     * must not take out the whole rule list at the moment a disconnect is being handled.
     */
    REGEX("regex"),

    /**
     * Pattern is a vanilla translation key, compared against the keys in the reason rather than
     * its text. Immune to language settings and to servers rewording their messages.
     */
    KEY("key");

    private final String token;

    MatchMode(String token) {
        this.token = token;
    }

    /** The word used in the rule syntax. */
    public String token() {
        return token;
    }

    public static MatchMode parse(String raw, MatchMode fallback) {
        if (raw == null) return fallback;
        String needle = raw.strip().toLowerCase(Locale.ROOT);
        for (MatchMode mode : values()) {
            if (mode.token.equals(needle) || mode.name().toLowerCase(Locale.ROOT).equals(needle)) {
                return mode;
            }
        }
        return fallback;
    }

    /**
     * @param text          the reason as displayed
     * @param keys          translation keys found in the reason, for {@link #KEY}
     * @param pattern       the rule's pattern
     * @param caseSensitive ignored by {@link #KEY}, which is always exact
     */
    public boolean matches(String text, Set<String> keys, String pattern, boolean caseSensitive) {
        if (pattern == null || pattern.isEmpty()) return false;

        if (this == KEY) {
            return keys.contains(pattern.strip());
        }
        if (this == REGEX) {
            try {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                return Pattern.compile(pattern, flags).matcher(text).find();
            } catch (PatternSyntaxException e) {
                return false;
            }
        }

        String haystack = caseSensitive ? text : text.toLowerCase(Locale.ROOT);
        String needle   = caseSensitive ? pattern : pattern.toLowerCase(Locale.ROOT);

        return switch (this) {
            case CONTAINS -> haystack.contains(needle);
            case EQUALS   -> haystack.strip().equals(needle.strip());
            case STARTS   -> haystack.strip().startsWith(needle);
            case ENDS     -> haystack.strip().endsWith(needle);
            default       -> false;
        };
    }
}
