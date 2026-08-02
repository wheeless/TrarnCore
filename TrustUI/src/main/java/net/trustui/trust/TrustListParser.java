package net.trustui.trust;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads GriefPrevention's {@code /trustlist} output.
 *
 * <p>The output looks like this — the important thing is what it <em>doesn't</em> contain:
 *
 * <pre>
 *   Explicit permissions here:
 *   &gt;NotReallyTrarn          &lt;- Manage      (gold)
 *   &gt;NotReallyTrarn          &lt;- Build       (yellow)
 *   &gt;                        &lt;- Containers  (green)
 *   &gt;                        &lt;- Access      (blue)
 *   Manage Build Containers Access
 * </pre>
 *
 * <p><b>The tier lines carry no labels.</b> GriefPrevention distinguishes them purely by colour,
 * documented by that legend on the last line — which is useless to parse against, since it is the
 * same four words regardless of who holds what. What is reliable is the <em>order</em>: always
 * exactly four {@code >} lines, always Manage, Build, Containers, Access. So the tier comes from
 * position, per {@link TrustLevel#TRUSTLIST_ORDER}.
 *
 * <p>Consequences worth knowing:
 * <ul>
 *   <li>Empty tiers still print a bare {@code >}, so the four lines are always present and
 *       positions never shift.</li>
 *   <li>The legend line has no {@code >} prefix, so filtering on that prefix drops it for free.</li>
 *   <li>A player can hold several tiers at once and appears on several lines — hence
 *       {@link EnumSet} per player rather than a single level.</li>
 *   <li>Standing outside a claim produces no {@code >} lines at all, which reads as "no claim
 *       here" rather than "a claim with nobody trusted".</li>
 * </ul>
 */
public final class TrustListParser {

    private TrustListParser() {
    }

    /** Marks a tier line. Everything else in the captured window is ignored. */
    private static final String TIER_PREFIX = ">";

    /**
     * @param lines every message captured while {@code /trustlist} was running, in arrival order
     * @return player name → the tiers they hold, or an empty map if no tier lines were seen
     */
    public static Map<String, EnumSet<TrustLevel>> parse(List<String> lines) {
        List<String> tierLines = new ArrayList<>(4);
        for (String line : lines) {
            String trimmed = line == null ? "" : stripFormatting(line).trim();
            if (trimmed.startsWith(TIER_PREFIX)) {
                tierLines.add(trimmed.substring(TIER_PREFIX.length()).trim());
            }
        }

        Map<String, EnumSet<TrustLevel>> result = new LinkedHashMap<>();
        // Guard on both ends: fewer than four lines means we did not get a full listing (not in a
        // claim, or the message format changed), and more means something else was captured in the
        // window. Only read as many as line up with the known order.
        int count = Math.min(tierLines.size(), TrustLevel.TRUSTLIST_ORDER.length);
        for (int i = 0; i < count; i++) {
            TrustLevel level = TrustLevel.TRUSTLIST_ORDER[i];
            for (String name : tierLines.get(i).split("\\s+")) {
                String cleaned = name.trim();
                if (cleaned.isEmpty()) continue;
                result.computeIfAbsent(cleaned, key -> EnumSet.noneOf(TrustLevel.class)).add(level);
            }
        }
        return result;
    }

    /** True if the captured lines actually contained a permission listing. */
    public static boolean looksLikeTrustList(List<String> lines) {
        for (String line : lines) {
            String trimmed = line == null ? "" : stripFormatting(line).trim();
            if (trimmed.startsWith(TIER_PREFIX)) return true;
        }
        return false;
    }

    /**
     * Strips legacy {@code §} colour codes. The captured text usually arrives already decoded, but
     * servers that build these messages as raw strings can leave the codes in, and a stray
     * {@code §a} on the front of a name would otherwise become part of it.
     */
    private static String stripFormatting(String text) {
        if (text.indexOf('§') < 0) return text;
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++; // skip the code character too
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
