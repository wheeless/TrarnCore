package net.containerutil.search;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keys of the containers matching the most recent search, so the world renderer can pick them
 * out from the ordinary per-kind colours.
 *
 * <p>Held as a set of keys rather than records so it stays valid if the index prunes something
 * underneath us — a stale key simply stops matching instead of pinning a dead record in memory.
 */
public final class SearchHighlight {

    private static volatile Set<String> keys = Collections.emptySet();
    private static volatile String query = "";

    private SearchHighlight() {
    }

    public static void set(List<SearchResult> results, String queryText) {
        Set<String> next = new HashSet<>(Math.max(16, results.size() * 2));
        for (SearchResult result : results) {
            next.add(result.container().key());
        }
        keys = next;
        query = queryText == null ? "" : queryText;
    }

    public static boolean contains(String key) {
        Set<String> current = keys;
        return !current.isEmpty() && current.contains(key);
    }

    public static int size() {
        return keys.size();
    }

    public static String query() {
        return query;
    }

    public static boolean isActive() {
        return !keys.isEmpty();
    }

    public static void clear() {
        keys = Collections.emptySet();
        query = "";
    }
}
