package net.autorelog.rule;

import java.util.Set;

/**
 * One user-defined decision about a disconnect reason.
 *
 * <p>Rules are stored as objects in {@code config/autorelog/config.json}, where they are readable
 * and commentable, but they are edited in ModMenu as one line of text each — Cloth Config has no
 * good editor for a list of structured objects, and a compact syntax beats six collapsed
 * sub-widgets per rule. {@link #serialize()} and {@link #parse(String)} are the two halves of that
 * round trip, and the JSON stays the source of truth.
 *
 * <pre>
 * action | mode | pattern | delay | attempts
 * </pre>
 *
 * Everything after {@code pattern} is optional. {@code delay} and {@code attempts} accept
 * {@code -} to mean "inherit the global setting".
 */
public class Rule {

    /** Set false to keep a rule in the file without it taking effect. */
    public boolean enabled = true;

    /** Free-text note. Never matched against; purely so a rule list stays readable. */
    public String comment = "";

    public String action = RuleAction.NEVER.token();
    public String mode = MatchMode.CONTAINS.token();
    public String pattern = "";

    /** Match {@link #pattern} case-sensitively. Off by default; kick text is inconsistently cased. */
    public boolean caseSensitive = false;

    /** Seconds to wait before reconnecting. Negative inherits the global delay. */
    public int delaySeconds = -1;

    /** Attempt cap for this reason. Negative inherits the global cap. */
    public int maxAttempts = -1;

    public Rule() {
    }

    public Rule(RuleAction action, MatchMode mode, String pattern, int delaySeconds, String comment) {
        this.action = action.token();
        this.mode = mode.token();
        this.pattern = pattern;
        this.delaySeconds = delaySeconds;
        this.comment = comment;
    }

    public RuleAction actionEnum() {
        return RuleAction.parse(action, RuleAction.NEVER);
    }

    public MatchMode modeEnum() {
        return MatchMode.parse(mode, MatchMode.CONTAINS);
    }

    public boolean matches(DisconnectInfo info) {
        if (!enabled || pattern == null || pattern.isBlank()) return false;
        return modeEnum().matches(info.text(), info.translationKeys(), pattern, caseSensitive);
    }

    /** Renders this rule as one editable line. Trailing "inherit" fields are omitted. */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        if (!enabled) sb.append("# ");
        sb.append(actionEnum().token()).append(" | ")
          .append(modeEnum().token()).append(caseSensitive ? "!" : "").append(" | ")
          .append(pattern);
        if (delaySeconds >= 0 || maxAttempts >= 0) {
            sb.append(" | ").append(delaySeconds >= 0 ? String.valueOf(delaySeconds) : "-");
        }
        if (maxAttempts >= 0) {
            sb.append(" | ").append(maxAttempts);
        }
        return sb.toString();
    }

    /**
     * Parses one line back into a rule. Never throws and never returns null for non-blank input:
     * a malformed line becomes a disabled rule carrying the original text in {@link #comment}, so
     * a typo is visible and recoverable rather than silently dropped on the next save.
     */
    public static Rule parse(String line) {
        if (line == null || line.isBlank()) return null;

        Rule rule = new Rule();
        String working = line.strip();

        // A leading # disables without deleting, matching how the serialized form marks it.
        if (working.startsWith("#")) {
            rule.enabled = false;
            working = working.substring(1).strip();
            if (working.isEmpty()) return null;
        }

        String[] parts = working.split("\\|", -1);
        if (parts.length < 3) {
            rule.enabled = false;
            rule.comment = "unparseable: " + working;
            return rule;
        }

        rule.action = RuleAction.parse(parts[0], RuleAction.NEVER).token();

        String modeToken = parts[1].strip();
        if (modeToken.endsWith("!")) {
            rule.caseSensitive = true;
            modeToken = modeToken.substring(0, modeToken.length() - 1);
        }
        rule.mode = MatchMode.parse(modeToken, MatchMode.CONTAINS).token();

        rule.pattern = parts[2].strip();
        rule.delaySeconds = parts.length > 3 ? parseOptionalInt(parts[3]) : -1;
        rule.maxAttempts  = parts.length > 4 ? parseOptionalInt(parts[4]) : -1;

        if (rule.pattern.isEmpty()) {
            rule.enabled = false;
            rule.comment = "empty pattern: " + working;
        }
        return rule;
    }

    /** Blank or {@code -} means "inherit". Anything unparseable means the same, rather than 0. */
    private static int parseOptionalInt(String raw) {
        String value = raw.strip();
        if (value.isEmpty() || value.equals("-")) return -1;
        try {
            return Math.max(-1, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Clamps hand-edited JSON into range. Called from the config's {@code validate()}. */
    public void validate() {
        if (action == null || RuleAction.parse(action, null) == null) action = RuleAction.NEVER.token();
        if (mode == null || MatchMode.parse(mode, null) == null) mode = MatchMode.CONTAINS.token();
        if (pattern == null) pattern = "";
        if (comment == null) comment = "";
        if (delaySeconds < -1) delaySeconds = -1;
        if (maxAttempts < -1) maxAttempts = -1;
        // A regex that does not compile is worth surfacing at load rather than at disconnect.
        if (modeEnum() == MatchMode.REGEX && !pattern.isBlank()
            && !MatchMode.REGEX.matches("", Set.of(), pattern, caseSensitive)
            && !isCompilable(pattern)) {
            enabled = false;
            if (!comment.startsWith("invalid regex")) {
                comment = "invalid regex: " + comment;
            }
        }
    }

    private static boolean isCompilable(String pattern) {
        try {
            java.util.regex.Pattern.compile(pattern);
            return true;
        } catch (java.util.regex.PatternSyntaxException e) {
            return false;
        }
    }
}
