package net.simdistance.config;

import net.trarncore.config.JsonConfig;

/**
 * Thin wrapper over TrarnCore's {@link JsonConfig} so call sites stay {@code ConfigManager.get()}.
 */
public class ConfigManager {

    private static final JsonConfig<SimDistanceConfig> CONFIG =
        JsonConfig.of(SimDistanceConfig.class, "simdistance");

    public static void load() {
        CONFIG.load();
    }

    public static void save() {
        CONFIG.save();
    }

    public static SimDistanceConfig get() {
        return CONFIG.get();
    }
}
