package net.rswitch.config;

import net.trarncore.config.JsonConfig;

/**
 * Thin wrapper over TrarnCore's {@link JsonConfig} so call sites stay {@code ConfigManager.get()}.
 * Clamping happens via {@link RSwitchConfig}'s {@code ValidatedConfig} implementation.
 */
public class ConfigManager {

    private static final JsonConfig<RSwitchConfig> CONFIG =
        JsonConfig.of(RSwitchConfig.class, "rswitch");

    public static void load() {
        CONFIG.load();
    }

    public static void save() {
        CONFIG.save();
    }

    public static RSwitchConfig get() {
        return CONFIG.get();
    }
}
