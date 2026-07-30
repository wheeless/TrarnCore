package net.claimviz.config;

import net.trarncore.config.JsonConfig;

import java.util.Optional;

/**
 * Thin wrapper over TrarnCore's {@link JsonConfig}, keeping the domain lookup below.
 *
 * <p>Note the file name: ClaimViz stores {@code servers.json}, not {@code config.json}. Renaming
 * it would silently orphan whatever server entries are already configured.
 */
public class ConfigManager {

    private static final JsonConfig<ClaimVizConfig> CONFIG =
        JsonConfig.of(ClaimVizConfig.class, "claimviz", "servers.json");

    public static void load() {
        CONFIG.load();
    }

    public static void save() {
        CONFIG.save();
    }

    public static ClaimVizConfig get() {
        return CONFIG.get();
    }

    /**
     * Returns the first enabled server entry whose serverAddress is a substring
     * of the given address (case-insensitive).
     */
    public static Optional<ClaimVizConfig.ServerConfig> getForServer(String address) {
        ClaimVizConfig config = CONFIG.get();
        if (config.servers == null || address == null) return Optional.empty();
        String lower = address.toLowerCase();
        return config.servers.stream()
            .filter(s -> s.enabled && lower.contains(s.serverAddress.toLowerCase()))
            .findFirst();
    }
}
