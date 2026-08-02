package net.trarncore.update;

/**
 * A dotted numeric version, for deciding whether a release is newer than what is installed.
 *
 * <p>Deliberately narrow: it understands {@code 1.2.3} and tolerates a trailing suffix like
 * {@code 1.2.3-beta}, and refuses anything else. Refusing is the point — a version we cannot read
 * is one we must not guess about, because guessing wrong means either nagging about a downgrade or
 * silently hiding a real update.
 */
public final class ModVersion implements Comparable<ModVersion> {

    private final int[] parts;
    private final String raw;

    private ModVersion(int[] parts, String raw) {
        this.parts = parts;
        this.raw = raw;
    }

    /** Parses a version, or returns {@code null} if it is not a plain dotted-numeric one. */
    public static ModVersion parse(String text) {
        if (text == null || text.isBlank()) return null;

        String trimmed = text.trim();
        // Drop any pre-release/build suffix: 1.2.3-beta.1 and 1.2.3+build both compare as 1.2.3.
        int cut = trimmed.length();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c != '.' && (c < '0' || c > '9')) {
                cut = i;
                break;
            }
        }
        String numeric = trimmed.substring(0, cut);
        if (numeric.isEmpty()) return null;

        String[] pieces = numeric.split("\\.");
        int[] parts = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            if (pieces[i].isEmpty()) return null;
            try {
                parts[i] = Integer.parseInt(pieces[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return new ModVersion(parts, trimmed);
    }

    @Override
    public int compareTo(ModVersion other) {
        int length = Math.max(parts.length, other.parts.length);
        for (int i = 0; i < length; i++) {
            // Missing components count as zero, so 1.2 and 1.2.0 compare equal rather than
            // reporting a phantom update in either direction.
            int mine = i < parts.length ? parts[i] : 0;
            int theirs = i < other.parts.length ? other.parts[i] : 0;
            if (mine != theirs) return Integer.compare(mine, theirs);
        }
        return 0;
    }

    public boolean isNewerThan(ModVersion other) {
        return compareTo(other) > 0;
    }

    @Override
    public String toString() {
        return raw;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ModVersion other && compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (int part : parts) hash = hash * 31 + part;
        return hash;
    }
}
