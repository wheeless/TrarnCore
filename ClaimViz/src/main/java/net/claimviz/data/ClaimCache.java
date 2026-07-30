package net.claimviz.data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimCache {

    private static final Map<String, List<ClaimRect>> cache = new ConcurrentHashMap<>();

    public static void set(String dimension, List<ClaimRect> claims) {
        cache.put(dimension, claims);
    }

    public static List<ClaimRect> get(String dimension) {
        return cache.getOrDefault(dimension, List.of());
    }

    public static void clear() {
        cache.clear();
    }
}
